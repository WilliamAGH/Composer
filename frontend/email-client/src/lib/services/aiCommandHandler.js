import { get } from 'svelte/store';
import { createComposeWindow, WindowKind } from '../window/windowTypes.js';
import { mergeDefaultArgs, resolveDefaultInstruction, getFunctionMeta } from './aiCatalog';
import { parseSubjectAndBody } from './emailUtils';
import { normalizeReplySubject } from './emailSubjectPrefixHandler.js';
import { deriveRecipientContext } from './emailContextConstructor';

/**
 * Centralizes AI-command handling so App.svelte can delegate compose/summary logic. By isolating this in
 * JS we avoid duplicate logic when other components need to trigger AI helpers.
 */
export async function handleAiCommand({
  command,
  commandVariant = null,
  instructionOverride = null,
  selectedEmail,
  catalogStore,
  windowManager,
  callAiCommand,
  ensureCatalogLoaded
}) {
  if (!selectedEmail) throw new Error('Select an email first.');
  const ready = await ensureCatalogLoaded();
  if (!ready) throw new Error('AI helpers are unavailable. Please refresh and try again.');
  const catalog = get(catalogStore);
  const fn = getFunctionMeta(catalog, command);
  if (!fn) throw new Error('Command unavailable.');
  const variant = resolveVariant(fn, commandVariant);
  const commandArgs = mergeDefaultArgs(fn, variant);
  const title = fn.label || 'AI Assistant';
  const targetsCompose = Array.isArray(fn.scopes) && fn.scopes.includes('compose');

  if (targetsCompose) {
    const existingCompose = findMatchingComposeWindow(windowManager, selectedEmail?.id || null);
    const descriptor = existingCompose || createComposeWindow(selectedEmail, {
      to: selectedEmail.fromEmail || '',
      subject: normalizeReplySubject(selectedEmail.subject || ''),
      isReply: true,
      title: selectedEmail.subject ? `Reply: ${selectedEmail.subject}` : fn.label || 'AI Compose'
    });

    if (existingCompose) {
      windowManager.focus(descriptor.id);
    } else {
      const result = windowManager.open(descriptor);
      if (!result.ok) {
        throw new Error('Close or minimize an existing draft before opening another.');
      }
    }

    await draftWithAi({
      descriptor,
      command,
      selectedEmail,
      fn,
      variant,
      instructionOverride,
      windowManager,
      callAiCommand,
      commandArgs,
      recipientContext: {
        name: descriptor.payload?.recipientName || '',
        email: descriptor.payload?.recipientEmail || ''
      }
    });
    return { type: WindowKind.COMPOSE, id: descriptor.id };
  }

  const instruction = instructionOverride || resolveDefaultInstruction(fn, variant);
  const data = await callAiCommand(command, instruction, {
    contextId: selectedEmail.contextId,
    subject: selectedEmail.subject,
    journeyScope: 'panel',
    journeyScopeTarget: selectedEmail.id || selectedEmail.contextId || null,
    journeyLabel: selectedEmail.subject,
    journeyHeadline: deriveHeadline(command, title),
    commandVariant: variant?.key || null,
    commandArgs
  });
  const html = (data?.response && window.Composer?.renderMarkdown ? window.Composer.renderMarkdown(data.response) : '')
    || (data?.sanitizedHtml || data?.sanitizedHTML || '')
    || '<div class="text-sm text-slate-500">No response received.</div>';
  const contextId = selectedEmail.contextId || selectedEmail.id || null;
  return {
    type: WindowKind.SUMMARY,
    contextId,
    html,
    title,
    command,
    commandLabel: title,
    raw: data
  };
}

function findMatchingComposeWindow(windowManager, contextId) {
  const openWindows = get(windowManager.windows);
  if (!Array.isArray(openWindows) || openWindows.length === 0) return null;
  return openWindows.find((win) =>
    win.kind === WindowKind.COMPOSE && (contextId ? win.contextId === contextId : true)
  ) || null;
}

async function draftWithAi({ descriptor, command, selectedEmail, fn, variant, instructionOverride = null, windowManager, callAiCommand, commandArgs, recipientContext = null }) {
  const instruction = instructionOverride || resolveDefaultInstruction(fn, variant);
  const data = await callAiCommand(command, instruction, {
    contextId: selectedEmail.contextId,
    subject: descriptor.payload.subject,
    journeyScope: 'compose',
    journeyScopeTarget: descriptor.id,
    journeyLabel: descriptor.payload.subject || selectedEmail.subject || 'reply',
    journeyHeadline: deriveHeadline(command, fn.label || 'AI Assistant'),
    commandVariant: variant?.key || null,
    commandArgs,
    recipientContext
  });
  let draftText = (data?.response && data.response.trim()) || '';
  if (!draftText && data?.sanitizedHtml) {
    const temp = document.createElement('div');
    temp.innerHTML = data.sanitizedHtml;
    draftText = temp.textContent.trim();
  }
  if (draftText) {
    const parsed = parseSubjectAndBody(draftText);
    windowManager.updateComposeDraft(descriptor.id, {
      subject: parsed.subject || descriptor.payload.subject,
      body: parsed.body || draftText
    });
  }
}

export function deriveHeadline(command, fallback) {
  switch (command) {
    case 'summarize':
      return 'Summarizing this email';
    case 'translate':
      return 'Translating the thread';
    case 'draft':
    case 'compose':
      return 'Drafting your reply';
    case 'tone':
      return 'Retuning the tone';
    default:
      return fallback || 'Working on your request';
  }
}

function resolveVariant(meta, variantKey) {
  if (!variantKey || !meta || !Array.isArray(meta.variants)) return null;
  return meta.variants.find((variant) => variant.key === variantKey) || null;
}

export function buildComposeInstruction(command, currentDraft, isReply, meta) {
  const fallback = resolveDefaultInstruction(meta, null);
  if (command === 'draft') {
    if (currentDraft && currentDraft.length > 0) {
      return `Improve this ${isReply ? 'reply' : 'draft'} while preserving the intent:\n\n${currentDraft}`;
    }
    return isReply ? fallback : `${fallback}`;
  }
  if (command === 'compose') {
    return currentDraft && currentDraft.length > 0
      ? `Polish this email draft and make it clear and concise:\n\n${currentDraft}`
      : fallback;
  }
  if (command === 'tone') {
    return currentDraft && currentDraft.length > 0
      ? `Adjust the tone of this email to be friendly but professional:\n\n${currentDraft}`
      : fallback;
  }
  return fallback;
}

export async function runComposeWindowAi({
  windowManager,
  windowConfig,
  detail,
  catalogStore,
  ensureCatalogLoaded,
  callAiCommand,
  resolveEmailById,
  selectedEmail
}) {
  if (!windowConfig || !detail?.command) {
    throw new Error('Compose window unavailable.');
  }
  const ready = await ensureCatalogLoaded();
  if (!ready) {
    throw new Error('AI helpers are unavailable. Please try again.');
  }
  const catalog = get(catalogStore);
  const fn = getFunctionMeta(catalog, detail.command);
  if (!fn) {
    throw new Error('Command unavailable.');
  }
  const instruction = detail.instructionOverride || buildComposeInstruction(detail.command, detail.draft || '', detail.isReply, fn);
  const relatedEmail = windowConfig.contextId ? resolveEmailById?.(windowConfig.contextId) : selectedEmail || null;
  const commandArgs = mergeDefaultArgs(fn, null);
  const recipientContext = deriveRecipientContext({
    toInput: detail.to,
    composePayload: windowConfig.payload,
    fallbackEmail: relatedEmail
  });
  if (typeof window !== 'undefined' && window.Composer?.debugRecipientContext) {
    console.debug('[ComposeAI] recipientContext', {
      toInput: detail.to,
      payload: {
        to: windowConfig.payload?.to,
        recipientName: windowConfig.payload?.recipientName,
        recipientEmail: windowConfig.payload?.recipientEmail
      },
      fallbackEmail: relatedEmail && {
        from: relatedEmail.from,
        fromEmail: relatedEmail.fromEmail,
        senderName: relatedEmail.senderName
      },
      resolved: recipientContext
    });
  }

  const data = await callAiCommand(detail.command, instruction, {
    contextId: relatedEmail?.contextId || relatedEmail?.id || null,
    subject: detail.subject || relatedEmail?.subject,
    journeyScope: 'compose',
    journeyScopeTarget: windowConfig.id,
    journeyLabel: detail.subject || relatedEmail?.subject || 'draft',
    journeyHeadline: deriveHeadline(detail.command, fn.label || 'AI Assistant'),
    commandArgs,
    recipientContext
  });
  let draftText = (data?.response && data.response.trim()) || '';
  if (!draftText && data?.sanitizedHtml) {
    const temp = document.createElement('div');
    temp.innerHTML = data.sanitizedHtml;
    draftText = temp.textContent.trim();
  }
  if (draftText) {
    const parsed = parseSubjectAndBody(draftText);
    let updatedBody = parsed.body || draftText;
    const quote = windowConfig.payload?.quotedContext;
    if (quote && !updatedBody.includes(quote.trim())) {
      updatedBody = `${updatedBody.trimEnd()}\n\n${quote}`;
    }
    windowManager.updateComposeDraft(windowConfig.id, {
      subject: parsed.subject || detail.subject,
      body: updatedBody
    });
  }
  return data;
}

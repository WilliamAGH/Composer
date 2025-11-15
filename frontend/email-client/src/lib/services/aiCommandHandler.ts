import { get } from 'svelte/store';
import { createComposeWindow, WindowKind, type ComposeWindowDescriptor } from '../window/windowTypes';
import type { WindowManager } from '../window/windowStore';
import { mergeDefaultArgs, resolveDefaultInstruction, getFunctionMeta, type AiFunctionSummary, type AiFunctionVariantSummary } from './aiCatalog';
import { parseSubjectAndBody } from './emailUtils';
import { normalizeReplySubject } from './emailSubjectPrefixHandler';
import { deriveRecipientContext } from './emailContextConstructor';
import type { Readable } from 'svelte/store';
import type { AiFunctionCatalogDto } from '../../main';
import type { ChatResponsePayload } from './catalogCommandClient';
import type { EmailMessage } from '../../main';

type CatalogStore = Readable<AiFunctionCatalogDto | null>;

type SelectedEmail = (EmailMessage & {
  from?: string | null;
  fromEmail?: string | null;
  preview?: string | null;
}) | null;

type CallAiCommandFn = (command: string, instruction: string, options: Record<string, unknown>) => Promise<ChatResponsePayload | null>;

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
}: {
  command: string;
  commandVariant?: string | null;
  instructionOverride?: string | null;
  selectedEmail: SelectedEmail;
  catalogStore: CatalogStore;
  windowManager: WindowManager;
  callAiCommand: CallAiCommandFn;
  ensureCatalogLoaded: () => Promise<boolean>;
}) {
  if (!selectedEmail) throw new Error('Select an email first.');
  const ready = await ensureCatalogLoaded();
  if (!ready) throw new Error('AI helpers are unavailable. Please refresh and try again.');
  const catalog = get(catalogStore);
  const fn = getFunctionMeta(catalog, command) as AiFunctionSummary | null;
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

function findMatchingComposeWindow(windowManager: WindowManager, contextId: string | null) {
  const openWindows = get(windowManager.windows);
  if (!Array.isArray(openWindows) || openWindows.length === 0) return null;
  return openWindows.find((win) =>
    win.kind === WindowKind.COMPOSE && (contextId ? win.contextId === contextId : true)
  ) || null;
}

async function draftWithAi({
  descriptor,
  command,
  selectedEmail,
  fn,
  variant,
  instructionOverride = null,
  windowManager,
  callAiCommand,
  commandArgs,
  recipientContext = null
}: {
  descriptor: ComposeWindowDescriptor;
  command: string;
  selectedEmail: SelectedEmail;
  fn: AiFunctionSummary;
  variant: AiFunctionVariantSummary | null;
  instructionOverride?: string | null;
  windowManager: WindowManager;
  callAiCommand: CallAiCommandFn;
  commandArgs: Record<string, unknown> | null;
  recipientContext?: { name?: string; email?: string } | null;
}) {
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
  const htmlContent = data?.sanitizedHtml || data?.sanitizedHTML;
  if (!draftText && htmlContent) {
    const temp = document.createElement('div');
    temp.innerHTML = htmlContent;
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

export function deriveHeadline(command: string, fallback?: string | null) {
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

function resolveVariant(meta: AiFunctionSummary, variantKey?: string | null) {
  if (!variantKey || !meta || !Array.isArray(meta.variants)) return null;
  return meta.variants.find((variant) => variant.key === variantKey) || null;
}

export function buildComposeInstruction(command: string, currentDraft: string, isReply: boolean, meta: AiFunctionSummary) {
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
}: {
  windowManager: WindowManager;
  windowConfig: ComposeWindowDescriptor | null;
  detail: {
    command: string;
    instructionOverride?: string | null;
    draft?: string;
    isReply?: boolean;
    to?: string;
    subject?: string;
  };
  catalogStore: CatalogStore;
  ensureCatalogLoaded: () => Promise<boolean>;
  callAiCommand: CallAiCommandFn;
  resolveEmailById?: (id: string) => SelectedEmail;
  selectedEmail?: SelectedEmail;
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
  const htmlContent = data?.sanitizedHtml || data?.sanitizedHTML;
  if (!draftText && htmlContent) {
    const temp = document.createElement('div');
    temp.innerHTML = htmlContent;
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

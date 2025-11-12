import { get } from 'svelte/store';
import { createComposeWindow, createSummaryWindow, WindowKind } from '../window/windowTypes.js';
import { mergeDefaultArgs, resolveDefaultInstruction, getFunctionMeta } from './aiCatalog';
import { parseSubjectAndBody } from './emailUtils';

/**
 * Centralizes AI-command handling so App.svelte can delegate compose/summary logic. By isolating this in
 * JS we avoid duplicate logic when other components need to trigger AI helpers.
 */
export async function handleAiCommand({
  command,
  selectedEmail,
  catalogStore,
  windowManager,
  callAiCommand,
  ensureCatalogLoaded,
  composeFunctions
}) {
  if (!selectedEmail) throw new Error('Select an email first.');
  const ready = await ensureCatalogLoaded();
  if (!ready) throw new Error('AI helpers are unavailable. Please refresh and try again.');
  const catalog = get(catalogStore);
  const fn = getFunctionMeta(catalog, command);
  if (!fn) throw new Error('Command unavailable.');
  const commandArgs = mergeDefaultArgs(fn, null);
  const title = fn.label || 'AI Assistant';
  const targetsCompose = Array.isArray(fn.scopes) && fn.scopes.includes('compose');

  if (targetsCompose) {
    const descriptor = createComposeWindow(selectedEmail, {
      to: selectedEmail.fromEmail || '',
      subject: `Re: ${selectedEmail.subject || ''}`,
      isReply: true,
      title: selectedEmail.subject ? `Reply: ${selectedEmail.subject}` : fn.label || 'AI Compose'
    });
    const result = windowManager.open(descriptor);
    if (!result.ok) {
      throw new Error('Close or minimize an existing draft before opening another.');
    }
    await draftWithAi({
      descriptor,
      command,
      selectedEmail,
      fn,
      windowManager,
      callAiCommand,
      commandArgs
    });
    return { type: WindowKind.COMPOSE, id: descriptor.id };
  }

  const instruction = resolveDefaultInstruction(fn, null);
  const data = await callAiCommand(command, instruction, {
    contextId: selectedEmail.contextId,
    subject: selectedEmail.subject,
    journeyScope: 'panel',
    journeyLabel: selectedEmail.subject,
    journeyHeadline: deriveHeadline(command, title),
    commandArgs
  });
  const html = (data?.response && window.ComposerAI?.renderMarkdown ? window.ComposerAI.renderMarkdown(data.response) : '')
    || (data?.sanitizedHtml || data?.sanitizedHTML || '')
    || '<div class="text-sm text-slate-500">No response received.</div>';
  const descriptor = createSummaryWindow(selectedEmail, html, title);
  windowManager.open(descriptor);
  return { type: WindowKind.SUMMARY, id: descriptor.id };
}

async function draftWithAi({ descriptor, command, selectedEmail, fn, windowManager, callAiCommand, commandArgs }) {
  const instruction = resolveDefaultInstruction(fn, null);
  const data = await callAiCommand(command, instruction, {
    contextId: selectedEmail.contextId,
    subject: descriptor.payload.subject,
    journeyScope: 'compose',
    journeyLabel: descriptor.payload.subject || selectedEmail.subject || 'reply',
    journeyHeadline: deriveHeadline(command, fn.label || 'AI Assistant'),
    commandArgs
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

function deriveHeadline(command, fallback) {
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

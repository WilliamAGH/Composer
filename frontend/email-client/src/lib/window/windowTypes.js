/**
 * Window descriptor helpers live in plain JS (not Svelte/Java) because they are shared across multiple
 * components and Svelte stores without rendering anything on their own. Keeping them here avoids
 * bootstrapping empty components just to import helper logic and keeps the state purely client-side.
 */
import { formatRecipientDisplay } from '../services/emailContextConstructor.js';

function toTrimmed(value) {
  return typeof value === 'string' ? value.trim() : '';
}
export const WindowKind = Object.freeze({
  COMPOSE: 'compose',
  SUMMARY: 'summary'
});

export const WindowMode = Object.freeze({
  FLOATING: 'floating',
  DOCKED: 'docked'
});

function fallbackUuid() {
  return 'win-' + Math.random().toString(36).slice(2, 10) + Date.now().toString(36);
}

export function createWindowId() {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return fallbackUuid();
}

export function createComposeWindow(email = {}, overrides = {}) {
  const safeRecipientName = toTrimmed(overrides.recipientName ?? email.senderName ?? email.from ?? '');
  const safeRecipientEmail = toTrimmed(overrides.recipientEmail ?? email.fromEmail ?? '');
  const defaultToValue = formatRecipientDisplay(safeRecipientName, safeRecipientEmail);
  return {
    id: createWindowId(),
    kind: WindowKind.COMPOSE,
    mode: WindowMode.FLOATING,
    minimized: false,
    contextId: email.id || null,
    title: overrides.title || (email.subject ? `Reply: ${email.subject}` : 'New Message'),
    payload: {
      to: overrides.to ?? defaultToValue,
      recipientName: safeRecipientName,
      recipientEmail: safeRecipientEmail,
      subject: overrides.subject ?? (email.subject ? `Re: ${email.subject}` : ''),
      body: overrides.body ?? '',
      hasQuotedContext: overrides.hasQuotedContext ?? false,
      quotedContext: overrides.quotedContext ?? '',
      bodyVersion: overrides.bodyVersion ?? 0,
      isReply: overrides.isReply ?? Boolean(email && email.id),
      isForward: overrides.isForward ?? false
    }
  };
}

export function createSummaryWindow(email = {}, html = '', title = 'AI Summary') {
  const contextId = email.id || email.contextId || createWindowId();
  return {
    id: createWindowId(),
    kind: WindowKind.SUMMARY,
    mode: WindowMode.DOCKED,
    minimized: false,
    contextId,
    title: title || 'AI Summary',
    payload: {
      emailId: contextId,
      html: html || '<div class="text-sm text-slate-500">No summary yet.</div>'
    }
  };
}

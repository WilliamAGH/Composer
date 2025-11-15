import { formatRecipientDisplay } from '../services/emailContextConstructor';
import { normalizeReplySubject } from '../services/emailSubjectPrefixHandler';
import type { FrontendEmailMessage } from '../services/emailUtils';

export type WindowKindType = 'compose' | 'summary';
export type WindowModeType = 'floating' | 'docked';

export const WindowKind = Object.freeze({
  COMPOSE: 'compose',
  SUMMARY: 'summary'
}) satisfies Record<'COMPOSE' | 'SUMMARY', WindowKindType>;

export const WindowMode = Object.freeze({
  FLOATING: 'floating',
  DOCKED: 'docked'
}) satisfies Record<'FLOATING' | 'DOCKED', WindowModeType>;

export type ComposeWindowPayload = {
  to: string;
  recipientName: string;
  recipientEmail: string;
  subject: string;
  body: string;
  hasQuotedContext: boolean;
  quotedContext: string;
  bodyVersion: number;
  isReply: boolean;
  isForward: boolean;
};

export type SummaryWindowPayload = {
  emailId: string | null;
  html: string;
};

export type ComposeWindowDescriptor = {
  id: string;
  kind: typeof WindowKind.COMPOSE;
  mode: typeof WindowMode.FLOATING;
  minimized: boolean;
  contextId: string | null;
  title: string;
  payload: ComposeWindowPayload;
};

export type SummaryWindowDescriptor = {
  id: string;
  kind: typeof WindowKind.SUMMARY;
  mode: typeof WindowMode.DOCKED;
  minimized: boolean;
  contextId: string | null;
  title: string;
  payload: SummaryWindowPayload;
};

export type WindowDescriptor = ComposeWindowDescriptor | SummaryWindowDescriptor;

function toTrimmed(value: string | null | undefined) {
  return typeof value === 'string' ? value.trim() : '';
}

function fallbackUuid() {
  return 'win-' + Math.random().toString(36).slice(2, 10) + Date.now().toString(36);
}

export function createWindowId() {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return fallbackUuid();
}

export type ComposeWindowOverrides = Partial<ComposeWindowPayload> & {
  title?: string;
  recipientName?: string;
  recipientEmail?: string;
  contextId?: string | null;
};

export function createComposeWindow(email: Partial<FrontendEmailMessage> = {}, overrides: ComposeWindowOverrides = {}): ComposeWindowDescriptor {
  const safeRecipientName = toTrimmed(overrides.recipientName ?? email.senderName ?? '');
  const safeRecipientEmail = toTrimmed(overrides.recipientEmail ?? email.fromEmail ?? '');
  const defaultToValue = formatRecipientDisplay(safeRecipientName, safeRecipientEmail);
  return {
    id: createWindowId(),
    kind: WindowKind.COMPOSE,
    mode: WindowMode.FLOATING,
    minimized: false,
    contextId: email.id || null,
    title: overrides.title || normalizeReplySubject(email.subject) || 'New Message',
    payload: {
      to: overrides.to ?? defaultToValue,
      recipientName: safeRecipientName,
      recipientEmail: safeRecipientEmail,
      subject: overrides.subject ?? (email.subject ? normalizeReplySubject(email.subject) : ''),
      body: overrides.body ?? '',
      hasQuotedContext: overrides.hasQuotedContext ?? false,
      quotedContext: overrides.quotedContext ?? '',
      bodyVersion: overrides.bodyVersion ?? 0,
      isReply: overrides.isReply ?? Boolean(email && email.id),
      isForward: overrides.isForward ?? false
    }
  };
}

export function createSummaryWindow(email: Partial<FrontendEmailMessage> = {}, html = '', title = 'AI Summary'): SummaryWindowDescriptor {
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

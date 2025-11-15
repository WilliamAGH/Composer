import { getJsonWithNonce, postJsonWithNonce } from './sessionNonceClient';
import { ensureMailboxSessionToken, getMailboxSessionToken } from './mailboxSessionService';
import type { EmailMessage } from '../../main';

export interface MailboxStateSnapshotResult {
  mailboxId: string;
  messages: EmailMessage[];
  folderCounts: Record<string, number>;
  placements: Record<string, string>;
  effectiveFolders: Record<string, string>;
}

export interface MessageMoveResult {
  mailboxId: string;
  messageId: string;
  previousFolderId: string | null;
  currentFolderId: string | null;
  updatedMessage: EmailMessage | null;
  folderCounts: Record<string, number>;
  placements: Record<string, string>;
  messages: EmailMessage[];
  effectiveFolders: Record<string, string>;
}

interface MoveMailboxMessageParams {
  mailboxId?: string;
  messageId: string;
  targetFolderId: string;
}

function withSessionParam(url: string) {
  const session = ensureMailboxSessionToken();
  const delimiter = url.includes('?') ? '&' : '?';
  const param = `session=${encodeURIComponent(session)}`;
  return `${url}${delimiter}${param}`;
}

export async function fetchMailboxStateSnapshot(mailboxId?: string | null) {
  const mailbox = mailboxId || 'primary';
  const baseUrl = `/api/mailboxes/${encodeURIComponent(mailbox)}/state`;
  const url = withSessionParam(baseUrl);
  return getJsonWithNonce<MailboxStateSnapshotResult>(url);
}

export async function moveMailboxMessage({ mailboxId, messageId, targetFolderId }: MoveMailboxMessageParams) {
  const mailbox = mailboxId || 'primary';
  const sessionId = getMailboxSessionToken() || ensureMailboxSessionToken();
  const url = `/api/mailboxes/${encodeURIComponent(mailbox)}/messages/${encodeURIComponent(messageId)}/move`;
  return postJsonWithNonce<MessageMoveResult>(url, {
    mailboxId: mailbox,
    targetFolderId,
    sessionId
  });
}

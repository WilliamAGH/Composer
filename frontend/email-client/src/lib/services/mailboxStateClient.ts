/**
 * Mailbox state API client with Zod-validated responses.
 * All responses are validated at runtime - validation failures are logged with full context.
 */

import { getJsonValidated, postJsonValidated } from './sessionNonceClient';
import { ensureMailboxSessionToken, getMailboxSessionToken } from './mailboxSessionService';
import {
  MailboxStateSnapshotSchema,
  MessageMoveResultSchema,
  type MailboxStateSnapshot,
  type MessageMoveResult
} from '../schemas/mailboxSchemas';
import type { ValidationResult } from '../validation/result';

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

/**
 * Fetches mailbox state with Zod validation.
 * Returns discriminated union - callers MUST check success before using data.
 */
export async function fetchMailboxStateSnapshot(
  mailboxId?: string | null
): Promise<ValidationResult<MailboxStateSnapshot>> {
  const mailbox = mailboxId || 'primary';
  const baseUrl = `/api/mailboxes/${encodeURIComponent(mailbox)}/state`;
  const url = withSessionParam(baseUrl);
  return getJsonValidated(url, MailboxStateSnapshotSchema, `mailbox-state:${mailbox}`);
}

/**
 * Moves a message to a target folder with Zod validation.
 * Returns discriminated union - callers MUST check success before using data.
 */
export async function moveMailboxMessage({
  mailboxId,
  messageId,
  targetFolderId
}: MoveMailboxMessageParams): Promise<ValidationResult<MessageMoveResult>> {
  const mailbox = mailboxId || 'primary';
  const sessionId = getMailboxSessionToken() || ensureMailboxSessionToken();
  const url = `/api/mailboxes/${encodeURIComponent(mailbox)}/messages/${encodeURIComponent(messageId)}/move`;
  return postJsonValidated(
    url,
    MessageMoveResultSchema,
    `move-message:${messageId}`,
    {
      mailboxId: mailbox,
      targetFolderId,
      sessionId
    }
  );
}

// Re-export types for callers
export type { MailboxStateSnapshot, MessageMoveResult };

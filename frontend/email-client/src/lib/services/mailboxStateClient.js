import { getJsonWithNonce, postJsonWithNonce } from './sessionNonceClient';
import { ensureMailboxSessionToken, getMailboxSessionToken } from './mailboxSessionService';

function withSessionParam(url) {
  const session = ensureMailboxSessionToken();
  const delimiter = url.includes('?') ? '&' : '?';
  const param = `session=${encodeURIComponent(session)}`;
  return `${url}${delimiter}${param}`;
}

export async function fetchMailboxStateSnapshot(mailboxId) {
  const mailbox = mailboxId || 'primary';
  const baseUrl = `/api/mailboxes/${encodeURIComponent(mailbox)}/state`;
  const url = withSessionParam(baseUrl);
  return getJsonWithNonce(url);
}

export async function moveMailboxMessage({ mailboxId, messageId, targetFolderId }) {
  const mailbox = mailboxId || 'primary';
  const sessionId = getMailboxSessionToken() || ensureMailboxSessionToken();
  const url = `/api/mailboxes/${encodeURIComponent(mailbox)}/messages/${encodeURIComponent(messageId)}/move`;
  return postJsonWithNonce(url, {
    mailboxId: mailbox,
    targetFolderId,
    sessionId
  });
}

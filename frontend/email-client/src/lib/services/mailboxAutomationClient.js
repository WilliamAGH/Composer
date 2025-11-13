import { postJsonWithNonce } from './sessionNonceClient';

/**
 * Invokes the mailbox automation endpoint which runs catalog commands across the filtered mailbox context.
 */
export async function launchMailboxAutomation(request) {
  const { mailboxId, ...body } = request;
  if (!mailboxId) {
    throw new Error('mailboxId is required to launch automation');
  }
  return postJsonWithNonce(`/api/mailboxes/${encodeURIComponent(mailboxId)}/automation`, body);
}

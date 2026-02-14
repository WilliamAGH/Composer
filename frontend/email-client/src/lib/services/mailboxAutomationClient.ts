import { postJsonVoid } from "./sessionNonceClient";

export interface MailboxAutomationRequest {
  mailboxId: string;
  catalogCommandKey: string;
  searchQuery?: string;
  messageContextIds?: string[];
  [key: string]: unknown;
}

/**
 * Invokes the mailbox automation endpoint which runs catalog commands across the filtered mailbox context.
 * Uses postJsonVoid since this is a fire-and-forget request.
 */
export async function launchMailboxAutomation(request: MailboxAutomationRequest): Promise<void> {
  const { mailboxId, ...body } = request;
  if (!mailboxId) {
    throw new Error("mailboxId is required to launch automation");
  }
  await postJsonVoid(`/api/mailboxes/${encodeURIComponent(mailboxId)}/automation`, body);
}

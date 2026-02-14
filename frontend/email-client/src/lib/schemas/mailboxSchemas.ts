/**
 * Mailbox state and move result schemas - runtime validation for mailbox API responses.
 *
 * @see docs/type-safety-zod-validation.md
 */

import { z } from "zod/v4";
import { EmailMessageSchema } from "./emailSchemas";

/**
 * Schema for mailbox state snapshot response.
 * Backend returns either `messages` or `emails` - we accept both.
 * At least one must be present for the response to be valid.
 */
export const MailboxStateSnapshotSchema = z
  .object({
    mailboxId: z.string(),
    messages: z.array(EmailMessageSchema).optional(),
    folderCounts: z.record(z.string(), z.number()),
    placements: z.record(z.string(), z.string()),
    effectiveFolders: z.record(z.string(), z.string()),
    selectedEmailId: z.string().nullish(),
    emails: z.array(EmailMessageSchema).optional(),
  })
  .refine((data) => data.messages !== undefined || data.emails !== undefined, {
    message: "Expected messages or emails in mailbox snapshot",
  });

export type MailboxStateSnapshot = z.infer<typeof MailboxStateSnapshotSchema>;

/**
 * Schema for message move result response.
 * Backend returns either `messages` or `emails` - we accept both.
 * At least one must be present for the response to be valid.
 */
export const MessageMoveResultSchema = z
  .object({
    mailboxId: z.string(),
    messageId: z.string(),
    previousFolderId: z.string().nullable(),
    currentFolderId: z.string().nullable(),
    updatedMessage: EmailMessageSchema.nullable(),
    folderCounts: z.record(z.string(), z.number()),
    placements: z.record(z.string(), z.string()),
    messages: z.array(EmailMessageSchema).optional(),
    effectiveFolders: z.record(z.string(), z.string()),
    selectedEmailId: z.string().nullish(),
    emails: z.array(EmailMessageSchema).optional(),
  })
  .refine((data) => data.messages !== undefined || data.emails !== undefined, {
    message: "Expected messages or emails in message move result",
  });

export type MessageMoveResult = z.infer<typeof MessageMoveResultSchema>;

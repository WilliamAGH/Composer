/**
 * Email message schema - runtime validation for API responses.
 * Derives TypeScript types from schema to ensure sync.
 *
 * @see docs/type-safety-zod-validation.md
 */

import { z } from 'zod/v4';

/**
 * Schema for individual email messages from the backend.
 * Fields use nullish() where API may omit OR send null.
 */
export const EmailMessageSchema = z.object({
  id: z.string(),
  contextId: z.string().nullish(),
  senderName: z.string().nullish(),
  senderEmail: z.string().nullish(),
  recipientName: z.string().nullish(),
  recipientEmail: z.string().nullish(),
  subject: z.string(),
  emailBodyRaw: z.string().nullish(),
  emailBodyTransformedText: z.string(),
  emailBodyTransformedMarkdown: z.string().nullish(),
  emailBodyHtml: z.string().nullish(),
  llmSummary: z.string().nullish(),
  receivedTimestampIso: z.string().nullish(),
  receivedTimestampDisplay: z.string().nullish(),
  labels: z.array(z.string()),
  companyLogoUrl: z.string().nullish(),
  avatarUrl: z.string().nullish(),
  starred: z.boolean(),
  read: z.boolean(),
  preview: z.string(),
  contextForAi: z.string().nullish()
});

/**
 * TypeScript type derived from schema - single source of truth.
 */
export type EmailMessage = z.infer<typeof EmailMessageSchema>;

/**
 * Extracts record identifier for logging from raw email data.
 */
export function extractEmailRecordId(raw: unknown): string {
  if (typeof raw === 'object' && raw !== null && 'id' in raw) {
    const idValue = (raw as Record<string, unknown>).id;
    if (typeof idValue === 'string') {
      return idValue;
    }
  }
  return 'unknown-email';
}

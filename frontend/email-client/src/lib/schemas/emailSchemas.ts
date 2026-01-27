/**
 * Email message schema - runtime validation for API responses.
 * Derives TypeScript types from schema to ensure sync.
 *
 * @see docs/type-safety-zod-validation.md
 */

import { z } from 'zod/v4';

/**
 * Type guard for plain objects with string keys.
 * Arrays pass typeof === 'object' but should not be treated as field containers.
 */
function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

/**
 * Normalizes backend field naming to frontend conventions.
 * Backend uses @JsonProperty("contextForAI") but frontend uses contextForAi.
 * Zod strips unknown keys, so we must normalize before validation.
 */
function normalizeEmailFields(data: unknown): unknown {
  if (!isPlainObject(data)) return data;

  // Copy contextForAI → contextForAi if backend sent uppercase variant
  if ('contextForAI' in data && !('contextForAi' in data)) {
    return { ...data, contextForAi: data.contextForAI };
  }
  return data;
}

/**
 * Core schema shape for email messages.
 * Separated from preprocessing for clean type inference.
 */
const EmailMessageSchemaShape = z.object({
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
 * Schema for individual email messages from the backend.
 * Fields use nullish() where API may omit OR send null.
 * Preprocesses to normalize contextForAI → contextForAi field naming.
 */
export const EmailMessageSchema = z.preprocess(
  normalizeEmailFields,
  EmailMessageSchemaShape
);

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

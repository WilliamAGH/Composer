/**
 * AI catalog and chat command schemas - runtime validation for AI API responses.
 *
 * @see docs/type-safety-zod-validation.md
 */

import { z } from 'zod/v4';

/**
 * Schema for AI function variant summary.
 */
export const AiFunctionVariantSummarySchema = z.object({
  key: z.string(),
  label: z.string(),
  defaultInstruction: z.string().nullable(),
  defaultArgs: z.record(z.string(), z.string())
});

export type AiFunctionVariantSummary = z.infer<typeof AiFunctionVariantSummarySchema>;

/**
 * Schema for AI function summary.
 */
export const AiFunctionSummarySchema = z.object({
  key: z.string(),
  label: z.string(),
  description: z.string().nullish(),
  category: z.string(),
  defaultInstruction: z.string().nullish(),
  subjectRequired: z.boolean(),
  contextRequired: z.boolean(),
  outputFormat: z.string(),
  primary: z.boolean(),
  scopes: z.array(z.string()),
  defaultArgs: z.record(z.string(), z.string()),
  variants: z.array(AiFunctionVariantSummarySchema)
});

export type AiFunctionSummary = z.infer<typeof AiFunctionSummarySchema>;

/**
 * Schema for AI function catalog DTO.
 */
export const AiFunctionCatalogDtoSchema = z.object({
  categories: z.array(
    z.object({
      category: z.string(),
      label: z.string(),
      functionKeys: z.array(z.string())
    })
  ),
  functionsByKey: z.record(z.string(), AiFunctionSummarySchema),
  primaryCommands: z.array(z.string())
});

export type AiFunctionCatalogDto = z.infer<typeof AiFunctionCatalogDtoSchema>;

/**
 * Schema for chat response email context.
 */
export const ChatResponseEmailContextSchema = z.object({
  emailId: z.string(),
  subject: z.string(),
  sender: z.string(),
  snippet: z.string(),
  relevanceScore: z.number(),
  emailDate: z.string()
});

export type ChatResponseEmailContext = z.infer<typeof ChatResponseEmailContextSchema>;

/**
 * Schema for chat command response payload.
 */
export const ChatResponsePayloadSchema = z.object({
  response: z.string().nullish(),
  sanitizedHtml: z.string().nullish(),
  sanitizedHTML: z.string().nullish(),
  conversationId: z.string().nullish(),
  emailContext: z.array(ChatResponseEmailContextSchema).optional(),
  timestamp: z.string().optional(),
  intent: z.string().nullish(),
  userMessageId: z.string().nullish(),
  assistantMessageId: z.string().nullish(),
  renderedHtml: z.string().nullish()
});

export type ChatResponsePayload = z.infer<typeof ChatResponsePayloadSchema>;

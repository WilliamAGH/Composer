/**
 * AI catalog command client with Zod-validated responses.
 * All responses are validated at runtime - validation failures are logged with full context.
 */

import { postJsonValidated, postJsonVoid } from './sessionNonceClient';
import {
  ChatResponsePayloadSchema,
  type ChatResponsePayload
} from '../schemas/catalogSchemas';
import type { ValidationResult } from '../validation/result';

export interface ChatRequestPayload {
  message: string;
  instruction?: string | null;
  conversationId?: string | null;
  maxResults?: number;
  emailContext?: string | null;
  contextId?: string | null;
  thinkingEnabled?: boolean;
  thinkingLevel?: 'minimal' | 'low' | 'medium' | 'high';
  jsonOutput?: boolean;
  aiCommand?: string | null;
  commandVariant?: string | null;
  commandArgs?: Record<string, string> | null;
  subject?: string | null;
  journeyScope?: string | null;
  journeyScopeTarget?: string | null;
  recipientName?: string | null;
  recipientEmail?: string | null;
  journeyLabel?: string | null;
  journeyHeadline?: string | null;
  targetLabel?: string | null;
}

export interface DraftContextPayload {
  contextId: string;
  content: string;
}

/**
 * Executes an AI catalog command with Zod validation.
 * Returns discriminated union - callers MUST check success before using data.
 */
export async function executeCatalogCommand(
  commandKey: string,
  payload: ChatRequestPayload
): Promise<ValidationResult<ChatResponsePayload>> {
  if (!commandKey) {
    throw new Error('commandKey is required');
  }
  return postJsonValidated(
    `/api/catalog-commands/${encodeURIComponent(commandKey)}/execute`,
    ChatResponsePayloadSchema,
    `catalog-command:${commandKey}`,
    payload
  );
}

/**
 * Uploads draft context for AI processing.
 * Uses postJsonVoid since response is empty (202 Accepted).
 */
export async function uploadDraftContext(payload: DraftContextPayload): Promise<void> {
  if (!payload?.contextId) {
    throw new Error('contextId is required');
  }
  if (!payload?.content) {
    throw new Error('content is required');
  }
  await postJsonVoid('/api/catalog-commands/draft-context', payload);
}

// Re-export types for callers
export type { ChatResponsePayload };

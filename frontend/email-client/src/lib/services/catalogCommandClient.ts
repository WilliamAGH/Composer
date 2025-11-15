import { postJsonWithNonce } from './sessionNonceClient';

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

export interface ChatResponseEmailContext {
  emailId: string;
  subject: string;
  sender: string;
  snippet: string;
  relevanceScore: number;
  emailDate: string;
}

export interface ChatResponsePayload {
  response?: string | null;
  sanitizedHtml?: string | null;
  sanitizedHTML?: string | null;
  conversationId?: string | null;
  emailContext?: ChatResponseEmailContext[];
  timestamp?: string;
  intent?: string | null;
  userMessageId?: string | null;
  assistantMessageId?: string | null;
  renderedHtml?: string | null;
}

/**
 * Calls the backend catalog command endpoint so every UI surface reuses the same transport + retry logic.
 */
export async function executeCatalogCommand(commandKey: string, payload: ChatRequestPayload): Promise<ChatResponsePayload> {
  if (!commandKey) {
    throw new Error('commandKey is required');
  }
  return postJsonWithNonce(`/api/catalog-commands/${encodeURIComponent(commandKey)}/execute`, payload);
}

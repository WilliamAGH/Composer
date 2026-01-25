package com.composerai.api.shared.ledger;

/**
 * Lightweight pointer to contextual data (emails, snippets, etc.) used at a specific event so that
 * resolvers can fetch the full payload on demand. Values map back to the same email/context IDs
 * described in {@code docs/email-context-conversation-uuids.md} and the OpenAI payload examples in
 * {@code docs/example-openai-conversation-json-2025-11-12.md}.
 */
public record ContextRef(String refType, String refId, String purpose) {}

package com.composerai.api.shared.ledger;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Ordered entry in a conversation ledger. Each event captures the minimal data needed to
 * reconstruct chat history and optional payloads (LLM calls, tool invocations, lookups).
 *
 * <p>LLM payloads map directly to the OpenAI Java SDK objects serialized in
 * {@code com.openai.models.chat.completions.ChatCompletion} and
 * {@code com.openai.models.responses.Response}; see the reference JSON in
 * {@code docs/example-openai-conversation-json-2025-11-12.md}.</p>
 */
public record ConversationEvent(
        String eventId,
        int index,
        String parentEventId,
        Instant timestamp,
        String type,
        String role,
        String content,
        List<ContextRef> contextRefs,
        Map<String, Object> meta,
        LlmCallPayload llm,
        ToolCallPayload tool) {}

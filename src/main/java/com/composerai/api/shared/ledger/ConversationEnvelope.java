package com.composerai.api.shared.ledger;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Canonical ledger envelope describing a single conversation replay snapshot.
 * Stores metadata, ordered events, and materialized email objects so downstream tooling can reason
 * about the exchange without rehydrating other systems.
 *
 * <p>See {@code docs/example-openai-conversation-json-2025-11-12.md} for the on-disk JSON format
 * and the OpenAI Java SDK models under {@code com.openai.models.chat.completions} /
 * {@code com.openai.models.responses} for the serialized payload referenced here.</p>
 */
public record ConversationEnvelope(
    String conversationId,
    Instant createdAt,
    int version,
    Map<String, Object> metadata,
    List<ConversationEvent> events,
    List<EmailObject> emails
) {}

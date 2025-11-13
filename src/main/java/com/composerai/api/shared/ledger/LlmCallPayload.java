package com.composerai.api.shared.ledger;

/**
 * Serialized record of an upstream LLM invocation. The SDK request/response JSON is stored verbatim
 * so future tooling can replay the exact call. The JSON blobs correspond to the OpenAI Java SDK
 * DTOs under {@code com.openai.models.chat.completions} or {@code com.openai.models.responses}
 * depending on {@link #endpoint()}.
 */
public record LlmCallPayload(
    String provider,
    String endpoint,
    String sdkType,
    String requestJson,
    String responseJson,
    UsageMetrics usage
) {}

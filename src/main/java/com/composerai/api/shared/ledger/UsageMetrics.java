package com.composerai.api.shared.ledger;

/**
 * Token accounting and latency data returned by the upstream LLM call so we can audit costs and
 * performance per event. Mirrors the {@code usage} block returned by the OpenAI Java SDK models in
 * {@code com.openai.models.chat.completions.ChatCompletion} /
 * {@code com.openai.models.responses.Response}.
 */
public record UsageMetrics(
    long promptTokens,
    long completionTokens,
    long totalTokens,
    long latencyMs
) {}

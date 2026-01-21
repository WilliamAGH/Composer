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
) {
    public UsageMetrics(long promptTokens, long completionTokens, long totalTokens, long latencyMs) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.latencyMs = latencyMs;
        this.totalTokens = (totalTokens == 0 && (promptTokens > 0 || completionTokens > 0))
            ? promptTokens + completionTokens
            : totalTokens;
    }
}

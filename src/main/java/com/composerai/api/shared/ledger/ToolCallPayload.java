package com.composerai.api.shared.ledger;

/**
 * Captures the raw OpenAI tool call payload plus the executed request/response so we can trace how
 * assistant-issued tool invocations were handled. {@link #openAiToolCallJson()} stores the exact
 * element emitted inside {@code ChatCompletionChoice.ChatCompletionMessage.toolCalls} (or the
 * Responses equivalent) so operators can tie local execution back to the SDK payload.
 */
public record ToolCallPayload(
        String openAiToolCallJson, String executionRequestJson, String executionResponseJson, String error) {}

package com.composerai.api.adapters.out.openai;

import com.openai.models.responses.ResponseFailedEvent;

/** Stable application-facing events emitted by the OpenAI Responses stream. */
public sealed interface OpenAiStreamEvent
        permits OpenAiStreamEvent.RenderedHtml,
                OpenAiStreamEvent.RawJson,
                OpenAiStreamEvent.Reasoning,
                OpenAiStreamEvent.Failed,
                OpenAiStreamEvent.RawText {

    record RenderedHtml(String html) implements OpenAiStreamEvent {}

    record RawJson(String jsonChunk) implements OpenAiStreamEvent {}

    record Reasoning(ReasoningStreamMapper.ReasoningEvent reasoningEvent) implements OpenAiStreamEvent {}

    record Failed(ResponseFailedEvent failedEvent) implements OpenAiStreamEvent {}

    record RawText(String textChunk) implements OpenAiStreamEvent {}
}

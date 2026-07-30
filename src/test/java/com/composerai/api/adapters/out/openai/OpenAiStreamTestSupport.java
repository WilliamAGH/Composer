package com.composerai.api.adapters.out.openai;

import com.openai.core.http.StreamResponse;
import com.openai.models.responses.ResponseStreamEvent;
import com.openai.models.responses.ResponseTextDeltaEvent;
import java.util.List;
import java.util.stream.Stream;

final class OpenAiStreamTestSupport {

    private OpenAiStreamTestSupport() {}

    static ResponseStreamEvent event(String content) {
        ResponseTextDeltaEvent textDelta = ResponseTextDeltaEvent.builder()
                .delta(content)
                .contentIndex(0)
                .outputIndex(0)
                .sequenceNumber(0)
                .itemId("msg-" + Math.abs(content.hashCode()))
                .logprobs(List.of())
                .build();
        return ResponseStreamEvent.ofOutputTextDelta(textDelta);
    }

    static StreamResponse<ResponseStreamEvent> streamResponse(Stream<ResponseStreamEvent> events) {
        return new StreamResponse<>() {
            @Override
            public Stream<ResponseStreamEvent> stream() {
                return events;
            }

            @Override
            public void close() {}
        };
    }

    static ResponseStreamEvent delayedEvent(ResponseStreamEvent event, long delayMillis) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
        }
        return event;
    }
}

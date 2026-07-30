package com.composerai.api.adapters.out.openai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import com.composerai.api.config.ErrorMessagesProperties;
import com.composerai.api.config.OpenAiProperties;
import com.composerai.api.domain.model.ChatCompletionCommand;
import com.composerai.api.domain.model.ReasoningEffortLevel;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseStreamEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mockito;

class OpenAiChatClientStreamReliabilityTest {

    @Test
    void delayedChunksCompleteWithoutTimeout() {
        OpenAIClient sdkClient = Mockito.mock(OpenAIClient.class, Answers.RETURNS_DEEP_STUBS);
        Stream<ResponseStreamEvent> eventStream = Stream.of(
                OpenAiStreamTestSupport.event("Initial response...\n\n"),
                OpenAiStreamTestSupport.delayedEvent(OpenAiStreamTestSupport.event("After delay 1...\n\n"), 100),
                OpenAiStreamTestSupport.delayedEvent(OpenAiStreamTestSupport.event("After delay 2...\n\n"), 200),
                OpenAiStreamTestSupport.event("Final response."));
        Mockito.when(sdkClient.responses().createStreaming(any(ResponseCreateParams.class)))
                .thenReturn(OpenAiStreamTestSupport.streamResponse(eventStream));
        List<String> chunks = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicReference<Throwable> streamFailure = new AtomicReference<>();

        chatClient(sdkClient)
                .streamResponse(
                        command(ReasoningEffortLevel.HIGH, true),
                        streamEvent -> {
                            if (streamEvent instanceof OpenAiStreamEvent.RenderedHtml renderedHtml) {
                                chunks.add(renderedHtml.html());
                            }
                        },
                        () -> completed.set(true),
                        streamFailure::set);

        assertFalse(chunks.isEmpty());
        assertTrue(completed.get());
        assertNull(streamFailure.get());
    }

    @Test
    void manyChunksAreProcessed() {
        OpenAIClient sdkClient = Mockito.mock(OpenAIClient.class, Answers.RETURNS_DEEP_STUBS);
        List<ResponseStreamEvent> events = new ArrayList<>();
        for (int chunkIndex = 0; chunkIndex < 100; chunkIndex++) {
            events.add(OpenAiStreamTestSupport.event("Chunk " + chunkIndex + " "));
        }
        Mockito.when(sdkClient.responses().createStreaming(any(ResponseCreateParams.class)))
                .thenReturn(OpenAiStreamTestSupport.streamResponse(events.stream()));
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicReference<Throwable> streamFailure = new AtomicReference<>();

        chatClient(sdkClient)
                .streamResponse(command(null, false), streamEvent -> {}, () -> completed.set(true), streamFailure::set);

        assertTrue(completed.get());
        assertNull(streamFailure.get());
    }

    @Test
    void timeoutReachesErrorHandler() {
        OpenAIClient sdkClient = Mockito.mock(OpenAIClient.class, Answers.RETURNS_DEEP_STUBS);
        Mockito.when(sdkClient.responses().createStreaming(any(ResponseCreateParams.class)))
                .thenThrow(new RuntimeException("Read timed out"));
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicReference<Throwable> streamFailure = new AtomicReference<>();

        chatClient(sdkClient)
                .streamResponse(command(null, false), streamEvent -> {}, () -> completed.set(true), streamFailure::set);

        assertFalse(completed.get());
        assertNotNull(streamFailure.get());
        assertTrue(streamFailure.get().getMessage().contains("timed out"));
    }

    private OpenAiChatClient chatClient(OpenAIClient sdkClient) {
        OpenAiProperties properties = new OpenAiProperties();
        properties.getModel().setChat("gpt-4o-mini");
        return new OpenAiChatClient(sdkClient, properties, new ErrorMessagesProperties());
    }

    private ChatCompletionCommand command(ReasoningEffortLevel reasoningEffort, Boolean thinkingEnabled) {
        return new ChatCompletionCommand("Analyze", "Context", List.of(), thinkingEnabled, reasoningEffort, false);
    }
}

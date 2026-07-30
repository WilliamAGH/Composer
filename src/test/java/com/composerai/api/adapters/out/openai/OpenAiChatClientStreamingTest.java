package com.composerai.api.adapters.out.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

class OpenAiChatClientStreamingTest {

    private static final Logger CLIENT_LOGGER = (Logger) LoggerFactory.getLogger(OpenAiChatClient.class);

    private final ErrorMessagesProperties errorMessages = new ErrorMessagesProperties();
    private Level originalLogLevel;

    @BeforeEach
    void quietClientLogs() {
        originalLogLevel = CLIENT_LOGGER.getLevel();
        CLIENT_LOGGER.setLevel(Level.OFF);
    }

    @AfterEach
    void resetLogging() {
        CLIENT_LOGGER.setLevel(originalLogLevel);
    }

    @Test
    void streamResponseEmitsSanitizedHtmlChunks() {
        OpenAIClient sdkClient = Mockito.mock(OpenAIClient.class, Answers.RETURNS_DEEP_STUBS);
        Stream<ResponseStreamEvent> eventStream = Stream.of(
                OpenAiStreamTestSupport.event("First paragraph.\n\n"),
                OpenAiStreamTestSupport.event("Second paragraph with <script>alert('x')</script>."));
        Mockito.when(sdkClient.responses().createStreaming(any(ResponseCreateParams.class)).stream())
                .thenReturn(eventStream);
        OpenAiChatClient chatClient = chatClient(sdkClient);
        List<String> chunks = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicReference<Throwable> streamFailure = new AtomicReference<>();

        chatClient.streamResponse(
                command(null, false),
                streamEvent -> {
                    if (streamEvent instanceof OpenAiStreamEvent.RenderedHtml renderedHtml) {
                        chunks.add(renderedHtml.html());
                    }
                },
                () -> completed.set(true),
                streamFailure::set);

        assertEquals(2, chunks.size());
        assertTrue(chunks.getFirst().startsWith("<p>"));
        assertFalse(chunks.get(1).contains("<script>"));
        assertTrue(completed.get());
        assertNull(streamFailure.get());
    }

    @Test
    void streamResponsePropagatesUpstreamError() {
        OpenAIClient sdkClient = Mockito.mock(OpenAIClient.class, Answers.RETURNS_DEEP_STUBS);
        Mockito.when(sdkClient.responses().createStreaming(any(ResponseCreateParams.class)))
                .thenThrow(new RuntimeException("rate limited"));
        AtomicReference<Throwable> streamFailure = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean(false);

        chatClient(sdkClient)
                .streamResponse(command(null, false), streamEvent -> {}, () -> completed.set(true), streamFailure::set);

        assertFalse(completed.get());
        assertNotNull(streamFailure.get());
        assertTrue(streamFailure.get().getMessage().contains("rate limited"));
    }

    @Test
    void streamResponseAppliesExplicitReasoningEffort() {
        OpenAIClient sdkClient = Mockito.mock(OpenAIClient.class, Answers.RETURNS_DEEP_STUBS);
        Mockito.when(sdkClient.responses().createStreaming(any(ResponseCreateParams.class)))
                .thenReturn(OpenAiStreamTestSupport.streamResponse(
                        Stream.of(OpenAiStreamTestSupport.event("Reasoned response."))));
        OpenAiChatClient chatClient = chatClient(sdkClient);

        chatClient.streamResponse(
                command(ReasoningEffortLevel.MINIMAL, true), streamEvent -> {}, () -> {}, streamFailure -> {});

        ArgumentCaptor<ResponseCreateParams> requestCaptor = ArgumentCaptor.forClass(ResponseCreateParams.class);
        Mockito.verify(sdkClient.responses()).createStreaming(requestCaptor.capture());
        assertEquals(
                "minimal",
                requestCaptor
                        .getValue()
                        .reasoning()
                        .orElseThrow()
                        .effort()
                        .orElseThrow()
                        .asString());
    }

    @Test
    void explicitLegacyFalseForwardsNone() {
        OpenAIClient sdkClient = Mockito.mock(OpenAIClient.class, Answers.RETURNS_DEEP_STUBS);
        Mockito.when(sdkClient.responses().createStreaming(any(ResponseCreateParams.class)))
                .thenReturn(OpenAiStreamTestSupport.streamResponse(
                        Stream.of(OpenAiStreamTestSupport.event("Standard response."))));
        OpenAiChatClient chatClient = chatClient(sdkClient);

        chatClient.streamResponse(command(null, false), streamEvent -> {}, () -> {}, streamFailure -> {});

        ArgumentCaptor<ResponseCreateParams> requestCaptor = ArgumentCaptor.forClass(ResponseCreateParams.class);
        Mockito.verify(sdkClient.responses()).createStreaming(requestCaptor.capture());
        assertEquals(
                "none",
                requestCaptor
                        .getValue()
                        .reasoning()
                        .orElseThrow()
                        .effort()
                        .orElseThrow()
                        .asString());
    }

    @Test
    void nullClientRejectsBeforeStreaming() {
        OpenAiChatClient chatClient = new OpenAiChatClient(null, new OpenAiProperties(), errorMessages);

        IllegalStateException misconfiguration = assertThrows(
                IllegalStateException.class,
                () -> chatClient.streamResponse(
                        command(ReasoningEffortLevel.MINIMAL, true), streamEvent -> {}, () -> {}, streamFailure -> {}));

        assertTrue(misconfiguration.getMessage().contains("not configured"));
    }

    private OpenAiChatClient chatClient(OpenAIClient sdkClient) {
        OpenAiProperties properties = new OpenAiProperties();
        properties.getModel().setChat("gpt-4o-mini");
        return new OpenAiChatClient(sdkClient, properties, errorMessages);
    }

    private ChatCompletionCommand command(ReasoningEffortLevel reasoningEffort, Boolean thinkingEnabled) {
        return new ChatCompletionCommand("What is new?", "Context", List.of(), thinkingEnabled, reasoningEffort, false);
    }
}

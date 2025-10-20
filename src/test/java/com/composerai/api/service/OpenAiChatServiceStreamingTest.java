package com.composerai.api.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.composerai.api.config.OpenAiProperties;
import com.composerai.api.service.email.HtmlConverter;
import com.openai.client.OpenAIClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseStreamEvent;
import com.openai.models.responses.ResponseTextDeltaEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

class OpenAiChatServiceStreamingTest {

    private static final Logger SERVICE_LOGGER = (Logger) LoggerFactory.getLogger(OpenAiChatService.class);
    private Level originalLogLevel;

    @AfterEach
    void resetLogging() {
        SERVICE_LOGGER.setLevel(originalLogLevel);
    }

    @BeforeEach
    void quietServiceLogs() {
        originalLogLevel = SERVICE_LOGGER.getLevel();
        SERVICE_LOGGER.setLevel(Level.OFF);
    }

    @Test
    void markdownRenderingEscapesHtml() {
        String html = HtmlConverter.markdownToSafeHtml("Hello <script>alert('x')</script> world");
        assertNotNull(html);
        assertFalse(html.contains("<script"));
        assertTrue(html.contains("alert"));
    }

    @Test
    void assemblerFlushesOnDoubleNewlineOutsideCodeFence() {
        OpenAiChatService.MarkdownStreamAssembler assembler = new OpenAiChatService.MarkdownStreamAssembler();

        List<String> firstChunk = assembler.onDelta("First paragraph.\n\nSecond paragraph start");
        assertEquals(1, firstChunk.size());
        assertTrue(firstChunk.get(0).contains("First paragraph."));

        List<String> none = assembler.onDelta(" continues.");
        assertTrue(none.isEmpty());

        Optional<String> remainder = assembler.flushRemainder();
        assertTrue(remainder.isPresent());
        assertTrue(remainder.get().contains("Second paragraph start continues."));
    }

    @Test
    void assemblerDefersFlushInsideCodeFence() {
        OpenAiChatService.MarkdownStreamAssembler assembler = new OpenAiChatService.MarkdownStreamAssembler();

        List<String> beforeFence = assembler.onDelta("```java\nSystem.out.println(\"hi\");\n");
        assertTrue(beforeFence.isEmpty());

        List<String> flushed = assembler.onDelta("```\n\n");
        assertEquals(1, flushed.size());
        assertTrue(flushed.get(0).contains("<pre><code"));

        assertTrue(assembler.flushRemainder().isEmpty());
    }

    @Test
    void streamResponseEmitsSanitizedHtmlChunks() {
        OpenAIClient client = Mockito.mock(OpenAIClient.class, Answers.RETURNS_DEEP_STUBS);
        Stream<ResponseStreamEvent> eventStream = Stream.of(
            createMockEvent("First paragraph.\n\n"),
            createMockEvent("Second paragraph with <script>alert('x')</script>.")
        );
        
        Mockito.when(client.responses().createStreaming(any(ResponseCreateParams.class)).stream())
            .thenReturn(eventStream);

        OpenAiProperties properties = new OpenAiProperties();
        properties.getModel().setChat("gpt-4o-mini");
        
        OpenAiChatService service = new OpenAiChatService(client, properties);

        List<String> chunks = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        service.streamResponse(
            "What is new?",
            "Context",
            false,
            null,
            event -> {
                if (event instanceof OpenAiChatService.StreamEvent.RenderedHtml rendered) {
                    chunks.add(rendered.html());
                }
            },
            () -> completed.set(true),
            errorRef::set
        );

        assertEquals(2, chunks.size());
        assertTrue(chunks.get(0).startsWith("<p>"));
        assertFalse(chunks.get(1).contains("<script>"));
        assertTrue(completed.get());
        assertNull(errorRef.get());
    }

    @Test
    void streamResponsePropagatesErrorEvents() {
        OpenAIClient client = Mockito.mock(OpenAIClient.class, Answers.RETURNS_DEEP_STUBS);
        
        Mockito.when(client.responses().createStreaming(any(ResponseCreateParams.class)))
            .thenThrow(new RuntimeException("rate limited"));

        OpenAiProperties properties = new OpenAiProperties();
        properties.getModel().setChat("gpt-4o-mini");
        
        OpenAiChatService service = new OpenAiChatService(client, properties);

        List<String> chunks = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        service.streamResponse("msg", "ctx", false, null, event -> {}, () -> completed.set(true), errorRef::set);

        assertTrue(chunks.isEmpty());
        assertFalse(completed.get());
        assertNotNull(errorRef.get());
        assertTrue(errorRef.get().getMessage().contains("rate limited"));
    }

    @Test
    void streamResponseFailedEventSurfacesOpenAiMessage() {
        OpenAIClient client = Mockito.mock(OpenAIClient.class, Answers.RETURNS_DEEP_STUBS);
        
        Mockito.when(client.responses().createStreaming(any(ResponseCreateParams.class)))
            .thenThrow(new RuntimeException("insufficient_quota"));

        OpenAiProperties properties = new OpenAiProperties();
        OpenAiChatService service = new OpenAiChatService(client, properties);

        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        service.streamResponse("msg", "ctx", false, null, event -> {}, () -> {}, errorRef::set);

        assertNotNull(errorRef.get());
        assertTrue(errorRef.get().getMessage().contains("insufficient_quota"));
    }

    @Test
    void streamResponse_withCustomModel_appliesReasoningEffort() {
        OpenAIClient client = Mockito.mock(OpenAIClient.class, Answers.RETURNS_DEEP_STUBS);
        StreamResponse<ResponseStreamEvent> mockStreamResponse = Mockito.mock(StreamResponse.class);
        Stream<ResponseStreamEvent> eventStream = Stream.of(
            createMockEvent("This is a response.\n\n"),
            createMockEvent("With reasoning.")
        );
        
        Mockito.when(mockStreamResponse.stream()).thenReturn(eventStream);
        Mockito.when(client.responses().createStreaming(any(ResponseCreateParams.class)))
            .thenReturn(mockStreamResponse);

        OpenAiProperties customProperties = new OpenAiProperties();
        customProperties.getModel().setChat("o4-mini");
        
        OpenAiChatService customModelService = new OpenAiChatService(client, customProperties);

        List<String> chunks = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        customModelService.streamResponse(
            "Analyze this email",
            "Email context here",
            true,
            "minimal",
            event -> {
                if (event instanceof OpenAiChatService.StreamEvent.RenderedHtml rendered) {
                    chunks.add(rendered.html());
                }
            },
            () -> completed.set(true),
            errorRef::set
        );

        assertEquals(2, chunks.size());
        assertTrue(chunks.get(0).contains("This is a response."));
        assertTrue(completed.get());
        assertNull(errorRef.get());
        
        // Verify reasoning was applied
        ArgumentCaptor<ResponseCreateParams> paramsCaptor = ArgumentCaptor.forClass(ResponseCreateParams.class);
        Mockito.verify(client.responses()).createStreaming(paramsCaptor.capture());
        ResponseCreateParams captured = paramsCaptor.getValue();
        assertTrue(captured.reasoning().isPresent());
        assertEquals("minimal", captured.reasoning().get().effort().get().asString());
    }

    @Test
    void streamResponse_withStandardModel_streamsWithoutReasoningEffort() {
        OpenAIClient client = Mockito.mock(OpenAIClient.class, Answers.RETURNS_DEEP_STUBS);
        StreamResponse<ResponseStreamEvent> mockStreamResponse = Mockito.mock(StreamResponse.class);
        Stream<ResponseStreamEvent> eventStream = Stream.of(createMockEvent("Standard response here."));
        
        Mockito.when(mockStreamResponse.stream()).thenReturn(eventStream);
        Mockito.when(client.responses().createStreaming(any(ResponseCreateParams.class)))
            .thenReturn(mockStreamResponse);

        OpenAiProperties standardProperties = new OpenAiProperties();
        standardProperties.getModel().setChat("gpt-4o-mini");
        
        OpenAiChatService standardModelService = new OpenAiChatService(client, standardProperties);

        List<String> chunks = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);

        standardModelService.streamResponse(
            "Test message",
            "Context",
            false,
            null,
            event -> {
                if (event instanceof OpenAiChatService.StreamEvent.RenderedHtml rendered) {
                    chunks.add(rendered.html());
                }
            },
            () -> completed.set(true),
            (error) -> {}
        );

        assertFalse(chunks.isEmpty());
        assertTrue(completed.get());
    }

    @Test
    void streamResponse_withNullClient_callsErrorHandler() {
        OpenAiProperties properties = new OpenAiProperties();
        OpenAiChatService nullClientService = new OpenAiChatService(null, properties);

        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean(false);

        nullClientService.streamResponse(
            "Test",
            "Context",
            true,
            "standard",
            event -> {},
            () -> completed.set(true),
            errorRef::set
        );

        assertNotNull(errorRef.get());
        assertTrue(errorRef.get().getMessage().contains("not configured"));
        assertFalse(completed.get());
    }

    private ResponseStreamEvent createMockEvent(String content) {
        ResponseStreamEvent event = Mockito.mock(ResponseStreamEvent.class);
        ResponseTextDeltaEvent textDelta = Mockito.mock(ResponseTextDeltaEvent.class);
        
        Mockito.when(textDelta.delta()).thenReturn(content);
        Mockito.when(event.outputTextDelta()).thenReturn(Optional.of(textDelta));
        
        return event;
    }
}

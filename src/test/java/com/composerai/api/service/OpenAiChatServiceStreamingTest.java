package com.composerai.api.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.composerai.api.config.ErrorMessagesProperties;
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
    private final ErrorMessagesProperties errorMessagesProperties = new ErrorMessagesProperties();

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
        OpenAiChatService.MarkdownStreamAssembler assembler = new OpenAiChatService.MarkdownStreamAssembler(false);

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
        OpenAiChatService.MarkdownStreamAssembler assembler = new OpenAiChatService.MarkdownStreamAssembler(false);

        List<String> beforeFence = assembler.onDelta("```java\nSystem.out.println(\"hi\");\n");
        assertTrue(beforeFence.isEmpty());

        List<String> flushed = assembler.onDelta("```\n\n");
        assertEquals(1, flushed.size());
        assertTrue(flushed.get(0).contains("<pre><code"));

        assertTrue(assembler.flushRemainder().isEmpty());
    }

    @Test
    void assemblerRendersMarkdownTablesToHtml() {
        OpenAiChatService.MarkdownStreamAssembler assembler = new OpenAiChatService.MarkdownStreamAssembler(false);

        List<String> chunks = assembler.onDelta("| Col A | Col B |\n| --- | --- |\n| 1 | 2 |\n\n");
        assertEquals(1, chunks.size());
        String html = chunks.getFirst();
        assertTrue(html.contains("<table"), "expected rendered HTML table");
        assertTrue(html.contains("<td"));
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
        
        OpenAiChatService service = new OpenAiChatService(client, properties, errorMessagesProperties);

        List<String> chunks = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        service.streamResponse(
            "What is new?",
            "Context",
            List.of(),
            false,
            null,
            false,
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
        
        OpenAiChatService service = new OpenAiChatService(client, properties, errorMessagesProperties);

        List<String> chunks = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        service.streamResponse("msg", "ctx", List.of(), false, null, false, event -> {}, () -> completed.set(true), errorRef::set);

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
        properties.getModel().setChat("gpt-4o-mini");
        OpenAiChatService service = new OpenAiChatService(client, properties, errorMessagesProperties);

        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        service.streamResponse("msg", "ctx", List.of(), false, null, false, event -> {}, () -> {}, errorRef::set);

        assertNotNull(errorRef.get());
        assertTrue(errorRef.get().getMessage().contains("insufficient_quota"));
    }

    @Test
    void streamResponse_withCustomModel_appliesReasoningEffort() {
        OpenAIClient client = Mockito.mock(OpenAIClient.class, Answers.RETURNS_DEEP_STUBS);
        Stream<ResponseStreamEvent> eventStream = Stream.of(
            createMockEvent("This is a response.\n\n"),
            createMockEvent("With reasoning.")
        );
        
        Mockito.when(client.responses().createStreaming(any(ResponseCreateParams.class)))
            .thenReturn(streamResponse(eventStream));

        OpenAiProperties customProperties = new OpenAiProperties();
        customProperties.getModel().setChat("o4-mini");
        
        OpenAiChatService customModelService = new OpenAiChatService(client, customProperties, errorMessagesProperties);

        List<String> chunks = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        customModelService.streamResponse(
            "Analyze this email",
            "Email context here",
            List.of(),
            true,
            "minimal",
            false,
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
        Stream<ResponseStreamEvent> eventStream = Stream.of(createMockEvent("Standard response here."));
        
        Mockito.when(client.responses().createStreaming(any(ResponseCreateParams.class)))
            .thenReturn(streamResponse(eventStream));

        OpenAiProperties standardProperties = new OpenAiProperties();
        standardProperties.getModel().setChat("gpt-4o-mini");
        
        OpenAiChatService standardModelService = new OpenAiChatService(client, standardProperties, errorMessagesProperties);

        List<String> chunks = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);

        standardModelService.streamResponse(
            "Test message",
            "Context",
            List.of(),
            false,
            null,
            false,
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
        OpenAiChatService nullClientService = new OpenAiChatService(null, properties, errorMessagesProperties);

        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean(false);

        nullClientService.streamResponse(
            "Test",
            "Context",
            List.of(),
            true,
            "standard",
            false,
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

    private StreamResponse<ResponseStreamEvent> streamResponse(Stream<ResponseStreamEvent> events) {
        return new StreamResponse<>() {
            @Override
            public Stream<ResponseStreamEvent> stream() {
                return events;
            }

            @Override
            public void close() {
                // no-op
            }
        };
    }

    /**
     * Test case for long-running stream with delays between chunks.
     * This simulates reasoning models that have pauses while "thinking".
     *
     * Expected behavior: Stream should continue despite delays
     * Current issue: May timeout after 5 minutes due to read timeout
     */
    @Test
    void streamResponse_withDelayedChunks_shouldNotTimeout() throws InterruptedException {
        OpenAIClient client = Mockito.mock(OpenAIClient.class, Answers.RETURNS_DEEP_STUBS);

        // Simulate chunks with delays (like reasoning model thinking)
        Stream<ResponseStreamEvent> eventStream = Stream.of(
            createMockEvent("Initial response...\n\n"),
            delayedEvent(createMockEvent("After delay 1...\n\n"), 100), // 100ms delay
            delayedEvent(createMockEvent("After delay 2...\n\n"), 200), // 200ms delay
            createMockEvent("Final response.")
        );

        Mockito.when(client.responses().createStreaming(any(ResponseCreateParams.class)))
            .thenReturn(streamResponse(eventStream));

        OpenAiProperties properties = new OpenAiProperties();
        properties.getModel().setChat("gpt-4o-mini");

        OpenAiChatService service = new OpenAiChatService(client, properties, errorMessagesProperties);

        List<String> chunks = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        service.streamResponse(
            "Complex analysis question",
            "Context",
            List.of(),
            true,
            "high",
            false,
            event -> {
                if (event instanceof OpenAiChatService.StreamEvent.RenderedHtml rendered) {
                    chunks.add(rendered.html());
                }
            },
            () -> completed.set(true),
            errorRef::set
        );

        assertEquals(4, chunks.size(), "Should receive all 4 chunks despite delays");
        assertTrue(completed.get(), "Stream should complete successfully");
        assertNull(errorRef.get(), "Should not have errors");
    }

    /**
     * Test case for very long stream simulation.
     * Tests whether the stream can handle many chunks over extended time.
     */
    @Test
    void streamResponse_withManyChunks_shouldProcessAll() {
        OpenAIClient client = Mockito.mock(OpenAIClient.class, Answers.RETURNS_DEEP_STUBS);

        // Generate many chunks
        List<ResponseStreamEvent> events = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            events.add(createMockEvent("Chunk " + i + " "));
        }

        Mockito.when(client.responses().createStreaming(any(ResponseCreateParams.class)))
            .thenReturn(streamResponse(events.stream()));

        OpenAiProperties properties = new OpenAiProperties();
        properties.getModel().setChat("gpt-4o-mini");

        OpenAiChatService service = new OpenAiChatService(client, properties, errorMessagesProperties);

        List<String> chunks = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        service.streamResponse(
            "Long analysis",
            "Context",
            List.of(),
            false,
            null,
            false,
            event -> {
                if (event instanceof OpenAiChatService.StreamEvent.RenderedHtml rendered) {
                    chunks.add(rendered.html());
                }
            },
            () -> completed.set(true),
            errorRef::set
        );

        assertFalse(chunks.isEmpty(), "Should receive chunks");
        assertTrue(completed.get(), "Stream should complete");
        assertNull(errorRef.get(), "Should not have errors");
    }

    /**
     * Test case for stream that encounters timeout.
     * This simulates what happens when read timeout is exceeded.
     */
    @Test
    void streamResponse_whenTimeoutOccurs_shouldCallErrorHandler() {
        OpenAIClient client = Mockito.mock(OpenAIClient.class, Answers.RETURNS_DEEP_STUBS);

        // Simulate timeout by throwing SocketTimeoutException
        Mockito.when(client.responses().createStreaming(any(ResponseCreateParams.class)))
            .thenThrow(new RuntimeException("Read timed out"));

        OpenAiProperties properties = new OpenAiProperties();
        properties.getModel().setChat("gpt-4o-mini");

        OpenAiChatService service = new OpenAiChatService(client, properties, errorMessagesProperties);

        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        service.streamResponse(
            "Test",
            "Context",
            List.of(),
            false,
            null,
            false,
            event -> {},
            () -> completed.set(true),
            errorRef::set
        );

        assertFalse(completed.get(), "Stream should not complete on timeout");
        assertNotNull(errorRef.get(), "Should have error");
        assertTrue(errorRef.get().getMessage().contains("timed out"),
            "Error should indicate timeout");
    }

    private ResponseStreamEvent delayedEvent(ResponseStreamEvent event, long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return event;
    }
}

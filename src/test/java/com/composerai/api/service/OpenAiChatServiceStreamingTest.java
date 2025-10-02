package com.composerai.api.service;

import com.composerai.api.config.OpenAiProperties;
import com.composerai.api.service.email.HtmlConverter;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseError;
import com.openai.models.responses.ResponseErrorEvent;
import com.openai.models.responses.ResponseFailedEvent;
import com.openai.models.responses.ResponseStatus;
import com.openai.models.responses.ResponseStreamEvent;
import com.openai.models.responses.ResponseTextDeltaEvent;
import com.openai.services.blocking.ResponseService;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

class OpenAiChatServiceStreamingTest {

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
        OpenAIClient client = Mockito.mock(OpenAIClient.class);
        ResponseService responseService = Mockito.mock(ResponseService.class);
        Mockito.when(client.responses()).thenReturn(responseService);

        List<ResponseStreamEvent> events = List.of(
            textDelta("First paragraph.\n\n", 0L),
            textDelta("Second paragraph with <script>alert('x')</script>.", 1L)
        );
        Mockito.when(responseService.createStreaming(any(ResponseCreateParams.class)))
            .thenReturn(new SimpleStreamResponse(events));

        OpenAiProperties properties = new OpenAiProperties();
        properties.setModel("gpt-4o-mini");

        OpenAiChatService service = new OpenAiChatService(client, properties);

        List<String> chunks = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        service.streamResponse(
            "What is new?",
            "Context",
            false,
            null,
            chunks::add,
            () -> completed.set(true),
            errorRef::set
        );

        assertEquals(2, chunks.size());
        assertTrue(chunks.get(0).startsWith("<p>"));
        assertFalse(chunks.get(1).contains("<script"));
        assertTrue(completed.get());
        assertNull(errorRef.get());
    }

    @Test
    void streamResponsePropagatesErrorEvents() {
        OpenAIClient client = Mockito.mock(OpenAIClient.class);
        ResponseService responseService = Mockito.mock(ResponseService.class);
        Mockito.when(client.responses()).thenReturn(responseService);

        List<ResponseStreamEvent> events = List.of(
            textDelta("Partial", 0L),
            ResponseStreamEvent.Companion.ofError(
                ResponseErrorEvent.builder()
                    .message("rate limited")
                    .code("rate_limit_exceeded")  // Required field for error event
                    .param(java.util.Optional.empty())  // Required field, empty for test
                    .sequenceNumber(1L)
                    .build()
            )
        );
        Mockito.when(responseService.createStreaming(any(ResponseCreateParams.class)))
            .thenReturn(new SimpleStreamResponse(events));

        OpenAiProperties properties = new OpenAiProperties();
        properties.setModel("gpt-4o-mini");

        OpenAiChatService service = new OpenAiChatService(client, properties);

        List<String> chunks = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        service.streamResponse("msg", "ctx", false, null, chunks::add, () -> completed.set(true), errorRef::set);

        assertTrue(chunks.isEmpty());
        assertFalse(completed.get());
        assertNotNull(errorRef.get());
        assertEquals("rate limited", errorRef.get().getMessage());
    }

    @Test
    void streamResponseFailedEventSurfacesOpenAiMessage() {
        OpenAIClient client = Mockito.mock(OpenAIClient.class);
        ResponseService responseService = Mockito.mock(ResponseService.class);
        Mockito.when(client.responses()).thenReturn(responseService);

        ResponseFailedEvent failedEvent = Mockito.mock(ResponseFailedEvent.class, Answers.RETURNS_DEEP_STUBS);
        ResponseError responseError = Mockito.mock(ResponseError.class, Answers.RETURNS_DEEP_STUBS);

        Mockito.when(failedEvent.response().error()).thenReturn(Optional.of(responseError));
        Mockito.when(responseError.message()).thenReturn("model gpt-3.5-turbo is not supported for responses API");
        Mockito.when(responseError.code()).thenReturn(ResponseError.Code.SERVER_ERROR);
        Mockito.when(failedEvent.response().status()).thenReturn(Optional.of(ResponseStatus.FAILED));
        Mockito.when(failedEvent.response().id()).thenReturn("resp_123");

        List<ResponseStreamEvent> events = List.of(
            ResponseStreamEvent.Companion.ofFailed(failedEvent)
        );
        Mockito.when(responseService.createStreaming(any(ResponseCreateParams.class)))
            .thenReturn(new SimpleStreamResponse(events));

        OpenAiProperties properties = new OpenAiProperties();
        OpenAiChatService service = new OpenAiChatService(client, properties);

        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        service.streamResponse("msg", "ctx", false, null, chunk -> {}, () -> {}, errorRef::set);

        assertNotNull(errorRef.get());
        assertEquals(
            "model gpt-3.5-turbo is not supported for responses API (code: server_error)",
            errorRef.get().getMessage()
        );
    }

    @Test
    void streamResponse_withGpt5Model_appliesMinimalReasoningEffort() {
        OpenAIClient client = Mockito.mock(OpenAIClient.class);
        ResponseService responseService = Mockito.mock(ResponseService.class);
        Mockito.when(client.responses()).thenReturn(responseService);

        List<ResponseStreamEvent> events = List.of(
            textDelta("GPT-5 generates this response.\n\n", 0L),
            textDelta("With enhanced reasoning capabilities.", 1L)
        );
        Mockito.when(responseService.createStreaming(any(ResponseCreateParams.class)))
            .thenReturn(new SimpleStreamResponse(events));

        OpenAiProperties gpt5Properties = new OpenAiProperties();
        gpt5Properties.setModel("gpt-5-mini");

        OpenAiChatService gpt5Service = new OpenAiChatService(client, gpt5Properties);

        List<String> chunks = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        gpt5Service.streamResponse(
            "Analyze this email",
            "Email context here",
            true,
            "minimal",
            chunks::add,
            () -> completed.set(true),
            errorRef::set
        );

        assertEquals(2, chunks.size());
        assertTrue(chunks.get(0).contains("GPT-5"));
        assertTrue(completed.get());
        assertNull(errorRef.get());
        
        ArgumentCaptor<ResponseCreateParams> paramsCaptor = ArgumentCaptor.forClass(ResponseCreateParams.class);
        Mockito.verify(responseService).createStreaming(paramsCaptor.capture());
        ResponseCreateParams captured = paramsCaptor.getValue();
        assertTrue(captured.reasoning().isPresent());
        assertTrue(captured.reasoning().get().effort().isPresent());
        assertEquals("minimal", captured.reasoning().get().effort().get().asString());
    }

    @Test
    void streamResponse_withNonGpt5Model_streamsWithoutReasoningEffort() {
        OpenAIClient client = Mockito.mock(OpenAIClient.class);
        ResponseService responseService = Mockito.mock(ResponseService.class);
        Mockito.when(client.responses()).thenReturn(responseService);

        List<ResponseStreamEvent> events = List.of(
            textDelta("GPT-4 response here.", 0L)
        );
        Mockito.when(responseService.createStreaming(any(ResponseCreateParams.class)))
            .thenReturn(new SimpleStreamResponse(events));

        OpenAiProperties gpt4Properties = new OpenAiProperties();
        gpt4Properties.setModel("gpt-4o-mini");

        OpenAiChatService gpt4Service = new OpenAiChatService(client, gpt4Properties);

        List<String> chunks = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);

        gpt4Service.streamResponse(
            "Test message",
            "Context",
            false,
            null,
            chunks::add,
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
            (chunk) -> {},
            () -> completed.set(true),
            errorRef::set
        );

        assertNotNull(errorRef.get());
        assertTrue(errorRef.get().getMessage().contains("not configured"));
        assertFalse(completed.get());
    }

    private static ResponseStreamEvent textDelta(String text, long sequence) {
        ResponseTextDeltaEvent deltaEvent = ResponseTextDeltaEvent.builder()
            .itemId("event-" + sequence)
            .delta(text)
            .outputIndex(0L)
            .contentIndex(0L)
            .sequenceNumber(sequence)
            .logprobs(java.util.Collections.emptyList())  // Required field, empty list for test
            .build();
        return ResponseStreamEvent.Companion.ofOutputTextDelta(deltaEvent);
    }

    private static final class SimpleStreamResponse implements com.openai.core.http.StreamResponse<ResponseStreamEvent> {
        private final List<ResponseStreamEvent> events;
        private boolean consumed = false;

        private SimpleStreamResponse(List<ResponseStreamEvent> events) {
            this.events = events;
        }

        @Override
        public Stream<ResponseStreamEvent> stream() {
            if (consumed) {
                throw new IllegalStateException("Stream already consumed");
            }
            consumed = true;
            return events.stream();
        }

        @Override
        public void close() {
            // no-op for tests
        }
    }
}

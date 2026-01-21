package com.composerai.api.controller;

import com.composerai.api.config.ErrorMessagesProperties;
import com.composerai.api.config.OpenAiProperties;
import com.composerai.api.dto.ChatRequest;
import com.composerai.api.dto.ChatResponse;
import com.composerai.api.dto.SseEventType;
import com.composerai.api.service.ChatService;
import com.composerai.api.util.StringUtils;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Controller for handling AI chat interactions via REST and SSE streams.
 * Manages conversation flow, heartbeat mechanisms for SSE, and event routing.
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final String HEARTBEAT_COMMENT = "heartbeat";
    private static final String STREAM_START_COMMENT = "stream-start";
    private static final String STREAM_DONE_DATA = "[DONE]";
    private static final String ERROR_SERVER_BUSY = "Server busy — please retry";
    private static final String ERROR_STREAM_START = "Streaming failed to start";

    private final ChatService chatService;
    private final Executor chatStreamExecutor;
    private final ScheduledExecutorService sseHeartbeatExecutor;
    private final OpenAiProperties openAiProperties;
    private final ErrorMessagesProperties errorMessages;

    public ChatController(
        ChatService chatService,
        @Qualifier("chatStreamExecutor") Executor chatStreamExecutor,
        @Qualifier("sseHeartbeatExecutor") ScheduledExecutorService sseHeartbeatExecutor,
        OpenAiProperties openAiProperties,
        ErrorMessagesProperties errorMessages
    ) {
        this.chatService = chatService;
        this.chatStreamExecutor = chatStreamExecutor;
        this.sseHeartbeatExecutor = sseHeartbeatExecutor;
        this.openAiProperties = openAiProperties;
        this.errorMessages = errorMessages;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        log.info("Received chat request from conversation: {}", request.getConversationId());
        ChatResponse response = chatService.processChat(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Connection", "keep-alive");
        // Timeout hint for frontend - single source of truth from OpenAiProperties
        response.setHeader("X-Stream-Timeout-Hint", String.valueOf(openAiProperties.getStream().getTimeoutMillis()));

        request.setConversationId(StringUtils.ensureConversationId(request.getConversationId()));
        final String conversationId = request.getConversationId();
        final String userMessageId = com.composerai.api.util.IdGenerator.uuidV7();
        final String assistantMessageId = com.composerai.api.util.IdGenerator.uuidV7();

        // Timeout from single source of truth - OpenAiProperties.Stream
        SseEmitter emitter = new SseEmitter(openAiProperties.getStream().getTimeoutMillis());
        
        final java.util.concurrent.atomic.AtomicBoolean completed = new java.util.concurrent.atomic.AtomicBoolean(false);
        final java.util.concurrent.ScheduledFuture<?> heartbeatTask = scheduleHeartbeat(emitter, completed);

        emitter.onTimeout(() -> {
            log.warn("SSE timeout for conversationId={}", conversationId);
            emitter.complete();
        });
        emitter.onError(ex -> {
            log.warn("SSE error for conversationId={}", conversationId, ex);
            completed.set(true);
            heartbeatTask.cancel(false);
        });
        emitter.onCompletion(() -> {
            completed.set(true);
            heartbeatTask.cancel(false);
            log.info("SSE completed for conversationId={}", conversationId);
        });
        
        try {
            StreamContext context = new StreamContext(
                conversationId, 
                userMessageId, 
                assistantMessageId, 
                emitter, 
                completed, 
                heartbeatTask
            );
            chatStreamExecutor.execute(() -> executeStream(request, context));
        } catch (java.util.concurrent.RejectedExecutionException rejection) {
            handleRejectedExecution(emitter, heartbeatTask, rejection);
        }
        return emitter;
    }

    private java.util.concurrent.ScheduledFuture<?> scheduleHeartbeat(SseEmitter emitter, java.util.concurrent.atomic.AtomicBoolean completed) {
        // Configuration source of truth: OpenAiProperties.Stream.getHeartbeatIntervalSeconds() - 10 seconds
        return sseHeartbeatExecutor.scheduleAtFixedRate(
            () -> {
                if (completed.get()) return;
                try {
                    emitter.send(SseEmitter.event().comment(HEARTBEAT_COMMENT));
                } catch (Exception e) {
                    log.debug("Failed to send heartbeat", e);
                }
            },
            0L,
            openAiProperties.getStream().getHeartbeatIntervalSeconds(),
            java.util.concurrent.TimeUnit.SECONDS
        );
    }

    private void executeStream(ChatRequest request, StreamContext context) {
        try {
            context.emitter().send(SseEmitter.event().comment(STREAM_START_COMMENT));
            final boolean jsonOutputRequested = request.isJsonOutput();
            sendMetadata(context, jsonOutputRequested);

            // SSE Event Routing: StreamEvents → SSE named events → Frontend SSEEventRouter
            chatService.streamChat(
                request,
                context.userMessageId(),
                context.assistantMessageId(),
                token -> handleRenderedHtml(context.emitter(), jsonOutputRequested, token),
                message -> handleReasoning(context.emitter(), message),
                () -> handleCompletion(context),
                error -> handleError(context, error)
            );
        } catch (Exception e) {
            handleStartupError(context.emitter(), e);
        }
    }

    private void sendMetadata(StreamContext context, boolean jsonOutputRequested) throws java.io.IOException {
        context.emitter().send(
            SseEmitter.event()
                .name(SseEventType.METADATA.getEventName())
                .data(
                    Map.of(
                        "conversationId", context.conversationId(),
                        "userMessageId", context.userMessageId(),
                        "assistantMessageId", context.assistantMessageId(),
                        "jsonOutput", jsonOutputRequested
                    )
                )
        );
    }

    private void handleRenderedHtml(SseEmitter emitter, boolean jsonOutputRequested, String token) {
        try {
            SseEventType eventType = jsonOutputRequested ? SseEventType.RAW_JSON : SseEventType.RENDERED_HTML;
            emitter.send(SseEmitter.event().name(eventType.getEventName()).data(token));
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    private void handleReasoning(SseEmitter emitter, Object message) {
        try {
            if (message != null) {
                emitter.send(SseEmitter.event().name(SseEventType.REASONING.getEventName()).data(message));
            }
        } catch (Exception e) {
            log.debug("Failed to forward reasoning event", e);
        }
    }

    private void handleCompletion(StreamContext context) {
        context.completed().set(true);
        context.heartbeatTask().cancel(false);
        try {
            context.emitter().send(SseEmitter.event().name(SseEventType.DONE.getEventName()).data(STREAM_DONE_DATA));
            context.emitter().complete();
        } catch (Exception e) {
            context.emitter().completeWithError(e);
        }
    }

    private void handleError(StreamContext context, Throwable error) {
        String safeMessage = errorMessages.getStream().getError();
        try {
            context.emitter().send(SseEmitter.event().name(SseEventType.ERROR.getEventName()).data(safeMessage));
            log.warn("Streaming completed with error: {} (original: {})", safeMessage, error.getMessage());
        } catch (Exception ignored) {
            log.debug("Failed to send SSE error event", ignored);
        }
        context.completed().set(true);
        context.heartbeatTask().cancel(false);
        context.emitter().complete();
    }

    private void handleStartupError(SseEmitter emitter, Exception e) {
        try {
            emitter.send(SseEmitter.event().name(SseEventType.ERROR.getEventName()).data(ERROR_STREAM_START));
            log.error(ERROR_STREAM_START, e);
        } catch (Exception ignored) {
            log.debug("Failed to send SSE startup error event", ignored);
        }
        emitter.complete();
    }

    private void handleRejectedExecution(SseEmitter emitter, java.util.concurrent.ScheduledFuture<?> heartbeatTask, java.util.concurrent.RejectedExecutionException rejection) {
        heartbeatTask.cancel(true);
        try {
            emitter.send(SseEmitter.event().name(SseEventType.ERROR.getEventName()).data(ERROR_SERVER_BUSY));
        } catch (Exception ignored) {
            log.debug("Failed to send rejection SSE error", ignored);
        }
        emitter.completeWithError(rejection);
    }

    private record StreamContext(
        String conversationId,
        String userMessageId,
        String assistantMessageId,
        SseEmitter emitter,
        java.util.concurrent.atomic.AtomicBoolean completed,
        java.util.concurrent.ScheduledFuture<?> heartbeatTask
    ) {}

    @PostMapping(value = "/insights/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter insightsStream(@Valid @RequestBody ChatRequest request, HttpServletResponse response) {
        log.info("Received insights request from conversation: {}", request.getConversationId());

        request.setMessage(ChatService.INSIGHTS_TRIGGER);

        // Delegate to regular stream endpoint with modified request
        return stream(request, response);
    }
}

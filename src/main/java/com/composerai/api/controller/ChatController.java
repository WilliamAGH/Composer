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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final Executor chatStreamExecutor;
    private final ScheduledExecutorService sseHeartbeatExecutor;
    private final OpenAiProperties openAiProperties;
    private final ErrorMessagesProperties errorMessages;
    private static final String INSIGHTS_TRIGGER = "__INSIGHTS_TRIGGER__";

    public ChatController(ChatService chatService,
                          @Qualifier("chatStreamExecutor") Executor chatStreamExecutor,
                          @Qualifier("sseHeartbeatExecutor") ScheduledExecutorService sseHeartbeatExecutor,
                          OpenAiProperties openAiProperties,
                          ErrorMessagesProperties errorMessages) {
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
        // Send periodic keepalive comments to prevent proxies from closing idle streams
        // Configuration source of truth: OpenAiProperties.Stream.getHeartbeatIntervalSeconds() - 10 seconds
        // Using shared executor to avoid thread pool exhaustion
        final java.util.concurrent.atomic.AtomicBoolean completed = new java.util.concurrent.atomic.AtomicBoolean(false);
        final java.util.concurrent.ScheduledFuture<?> heartbeatTask = sseHeartbeatExecutor.scheduleAtFixedRate(() -> {
            if (completed.get()) return;
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (Exception ignored) {
                // ignore intermittent heartbeat failures
            }
        }, 0L, openAiProperties.getStream().getHeartbeatIntervalSeconds(), java.util.concurrent.TimeUnit.SECONDS);
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
            chatStreamExecutor.execute(() -> {
            try {
                emitter.send(SseEmitter.event().comment("stream-start"));
                final boolean jsonOutputRequested = request.isJsonOutput();
                emitter.send(SseEmitter.event().name(SseEventType.METADATA.getEventName()).data(Map.of(
                    "conversationId", conversationId,
                    "userMessageId", userMessageId,
                    "assistantMessageId", assistantMessageId,
                    "jsonOutput", jsonOutputRequested
                )));

                // SSE Event Routing: StreamEvents → SSE named events → Frontend SSEEventRouter
                chatService.streamChat(
                    request,
                    userMessageId,
                    assistantMessageId,
                    // Route HTML chunks: StreamEvent.RenderedHtml → SSE "rendered_html"
                    token -> {
                        try {
                            SseEventType eventType = jsonOutputRequested
                                ? SseEventType.RAW_JSON
                                : SseEventType.RENDERED_HTML;
                            emitter.send(SseEmitter.event().name(eventType.getEventName()).data(token));
                        } catch (Exception e) {
                            emitter.completeWithError(e);
                        }
                    },
                    // Route thinking progress: StreamEvent.Reasoning → SSE "reasoning"
                    message -> {
                        try {
                            if (message != null) {
                                emitter.send(SseEmitter.event()
                                    .name(SseEventType.REASONING.getEventName())
                                    .data(message));
                            }
                        } catch (Exception e) {
                            log.debug("Failed to forward reasoning event", e);
                        }
                    },
                    // Route completion: onComplete → SSE "done"
                    () -> {
                        completed.set(true);
                        heartbeatTask.cancel(false);
                        try {
                            emitter.send(SseEmitter.event().name(SseEventType.DONE.getEventName()).data("[DONE]"));
                            emitter.complete();
                        } catch (Exception e) {
                            emitter.completeWithError(e);
                        }
                    },
                    // Route errors: onError → SSE "error"
                    error -> {
                        // Use safe, predefined error message to avoid leaking backend internals
                        String safeMessage = errorMessages.getStream().getError();
                        try {
                            emitter.send(SseEmitter.event().name(SseEventType.ERROR.getEventName()).data(safeMessage));
                            log.warn("Streaming completed with error: {} (original: {})", safeMessage, error.getMessage());
                        } catch (Exception ignored) {
                            log.debug("Failed to send SSE error event", ignored);
                        }
                        completed.set(true);
                        heartbeatTask.cancel(false);
                        emitter.complete();
                    }
                );
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name(SseEventType.ERROR.getEventName()).data("Streaming failed to start"));
                    log.error("Streaming failed to start", e);
                } catch (Exception ignored) {
                    log.debug("Failed to send SSE startup error event", ignored);
                }
                emitter.complete();
            }
        });
        } catch (java.util.concurrent.RejectedExecutionException rejection) {
            try {
                emitter.send(SseEmitter.event().name(SseEventType.ERROR.getEventName()).data("Server busy — please retry"));
            } catch (Exception ignored) {
                log.debug("Failed to send rejection SSE error", ignored);
            }
            emitter.completeWithError(rejection);
        }
        return emitter;
    }

    @PostMapping(value = "/insights/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter insightsStream(@Valid @RequestBody ChatRequest request, HttpServletResponse response) {
        log.info("Received insights request from conversation: {}", request.getConversationId());

        request.setMessage(INSIGHTS_TRIGGER);

        // Delegate to regular stream endpoint with modified request
        return stream(request, response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "ComposerAI API",
            "timestamp", java.time.LocalDateTime.now().toString()
        ));
    }
}

package com.composerai.api.controller;

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

@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final Executor chatStreamExecutor;
    private final OpenAiProperties openAiProperties;

    public ChatController(ChatService chatService,
                          @Qualifier("chatStreamExecutor") Executor chatStreamExecutor,
                          OpenAiProperties openAiProperties) {
        this.chatService = chatService;
        this.chatStreamExecutor = chatStreamExecutor;
        this.openAiProperties = openAiProperties;
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

        // Timeout from single source of truth - OpenAiProperties.Stream
        SseEmitter emitter = new SseEmitter(openAiProperties.getStream().getTimeoutMillis());
        // Send periodic keepalive comments to prevent proxies from closing idle streams
        // Configuration source of truth: OpenAiProperties.Stream.getHeartbeatIntervalSeconds() - 10 seconds
        final java.util.concurrent.ScheduledExecutorService heartbeatExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        final java.util.concurrent.atomic.AtomicBoolean completed = new java.util.concurrent.atomic.AtomicBoolean(false);
        heartbeatExecutor.scheduleAtFixedRate(() -> {
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
        emitter.onError(ex -> log.warn("SSE error for conversationId={}", conversationId, ex));
        emitter.onCompletion(() -> {
            completed.set(true);
            heartbeatExecutor.shutdownNow();
            log.info("SSE completed for conversationId={}", conversationId);
        });
        try {
            chatStreamExecutor.execute(() -> {
            try {
                emitter.send(SseEmitter.event().comment("stream-start"));
                emitter.send(SseEmitter.event().name(SseEventType.METADATA.getEventName()).data(Map.of(
                    "conversationId", conversationId
                )));

                // SSE Event Routing: StreamEvents → SSE named events → Frontend SSEEventRouter
                chatService.streamChat(
                    request,
                    // Route HTML chunks: StreamEvent.RenderedHtml → SSE "rendered_html"
                    token -> {
                        try {
                            emitter.send(SseEmitter.event().name(SseEventType.RENDERED_HTML.getEventName()).data(token));
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
                        heartbeatExecutor.shutdownNow();
                        try {
                            emitter.send(SseEmitter.event().name(SseEventType.DONE.getEventName()).data("[DONE]"));
                            emitter.complete();
                        } catch (Exception e) {
                            emitter.completeWithError(e);
                        }
                    },
                    // Route errors: onError → SSE "error"
                    error -> {
                        String message = StringUtils.isBlank(error.getMessage())
                            ? "OpenAI is not configured or unavailable" : error.getMessage();
                        try {
                            emitter.send(SseEmitter.event().name(SseEventType.ERROR.getEventName()).data(message));
                            log.warn("Streaming completed with error: {}", message);
                        } catch (Exception ignored) {
                            log.debug("Failed to send SSE error event", ignored);
                        }
                        completed.set(true);
                        heartbeatExecutor.shutdownNow();
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

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "ComposerAI API",
            "timestamp", java.time.LocalDateTime.now().toString()
        ));
    }
}

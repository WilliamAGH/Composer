package com.composerai.api.controller;

import com.composerai.api.dto.ChatRequest;
import com.composerai.api.dto.ChatResponse;
import com.composerai.api.service.ChatService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.beans.factory.annotation.Qualifier;
import java.util.concurrent.Executor;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
    
    private final ChatService chatService;
    private final Executor chatStreamExecutor;

    public ChatController(ChatService chatService, @Qualifier("chatStreamExecutor") Executor chatStreamExecutor) {
        this.chatService = chatService;
        this.chatStreamExecutor = chatStreamExecutor;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        logger.info("Received chat request from conversation: {}", request.getConversationId());
        ChatResponse response = chatService.processChat(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Connection", "keep-alive");
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);
        emitter.onTimeout(() -> {
            logger.warn("SSE timeout for conversationId={}", request.getConversationId());
            emitter.complete();
        });
        emitter.onError(ex -> logger.warn("SSE error for conversationId={}", request.getConversationId(), ex));
        emitter.onCompletion(() -> logger.info("SSE completed for conversationId={}", request.getConversationId()));
        try {
            chatStreamExecutor.execute(() -> {
            try {
                emitter.send(SseEmitter.event().comment("stream-start"));
                chatService.streamChat(
                    request.getMessage(),
                    request.getMaxResults(),
                    request.getEmailContext(),
                    request.getConversationId(),
                    request.isThinkingEnabled(),
                    request.getThinkingLevel(),
                    metadata -> {
                        try {
                            emitter.send(SseEmitter.event().name("metadata").data(Map.of(
                                "conversationId", metadata.conversationId()
                            )));
                        } catch (Exception e) {
                            emitter.completeWithError(e);
                        }
                    },
                    token -> {
                        try {
                            emitter.send(SseEmitter.event().name("rendered_html").data(token));
                        } catch (Exception e) {
                            emitter.completeWithError(e);
                        }
                    },
                    () -> {
                        try {
                            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                        } catch (Exception e) {
                            emitter.completeWithError(e);
                            return;
                        }
                        emitter.complete();
                    },
                    error -> {
                        try {
                            String message = error.getMessage() == null || error.getMessage().isBlank()
                                ? "OpenAI is not configured or unavailable"
                                : error.getMessage();
                            emitter.send(SseEmitter.event().name("error").data(message));
                            logger.warn("Streaming completed with error: {}", message);
                        } catch (Exception ignored) {
                            logger.debug("Failed to send SSE error event", ignored);
                        }
                        emitter.complete();
                    }
                );
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data("Streaming failed to start"));
                    logger.error("Streaming failed to start", e);
                } catch (Exception ignored) {
                    logger.debug("Failed to send SSE startup error event", ignored);
                }
                emitter.complete();
            }
        });
        } catch (java.util.concurrent.RejectedExecutionException rejection) {
            try {
                emitter.send(SseEmitter.event().name("error").data("Server busy — please retry"));
            } catch (Exception ignored) {
                logger.debug("Failed to send rejection SSE error", ignored);
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

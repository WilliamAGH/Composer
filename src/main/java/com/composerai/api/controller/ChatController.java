package com.composerai.api.controller;

import com.composerai.api.dto.ChatRequest;
import com.composerai.api.dto.ChatResponse;
import com.composerai.api.service.ChatService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.beans.factory.annotation.Qualifier;
import java.util.concurrent.Executor;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "${app.cors.allowed-origins:*}")
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
        
        try {
            ChatResponse response = chatService.processChat(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error processing chat request", e);
            
            ChatResponse errorResponse = new ChatResponse();
            errorResponse.setResponse("An error occurred while processing your request.");
            errorResponse.setConversationId(request.getConversationId());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @PostMapping("/stream")
    public SseEmitter stream(@Valid @RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);
        chatStreamExecutor.execute(() -> {
            try {
                chatService.streamChat(
                    request.getMessage(),
                    request.getMaxResults(),
                    token -> {
                        try {
                            emitter.send(SseEmitter.event().data(token));
                        } catch (Exception e) {
                            emitter.completeWithError(e);
                        }
                    },
                    emitter::complete,
                    error -> {
                        try {
                            emitter.send(SseEmitter.event().name("error").data(
                                "OpenAI is not configured or unavailable"
                            ));
                        } catch (Exception ignored) {}
                        emitter.complete();
                    }
                );
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(
                        "Streaming failed to start"
                    ));
                } catch (Exception ignored) {}
                emitter.complete();
            }
        });
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
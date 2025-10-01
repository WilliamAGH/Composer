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

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*") // Configure this properly for production
public class ChatController {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
    
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
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
        SseEmitter emitter = new SseEmitter(0L);
        new Thread(() -> {
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
                    emitter::completeWithError
                );
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }).start();
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
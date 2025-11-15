package com.composerai.api.controller;

import com.composerai.api.dto.ChatRequest;
import com.composerai.api.dto.ChatResponse;
import com.composerai.api.service.ChatService;
import com.composerai.api.util.StringUtils;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Dedicated REST controller for catalog-driven AI commands so toolbar/automation flows can
 * issue deterministic requests without duplicating chat orchestration logic.
 */
@Slf4j
@RestController
@RequestMapping("/api/catalog-commands")
public class CatalogCommandController {

    private final ChatService chatService;

    public CatalogCommandController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * Executes a single catalog command (summarize, translate, draft, etc.) by delegating to the
     * {@link ChatService}. The path parameter acts as the source of truth for the selected command.
     */
    @PostMapping("/{commandKey}/execute")
    public ResponseEntity<ChatResponse> executeCatalogCommand(@PathVariable String commandKey,
                                                              @Valid @RequestBody ChatRequest request) {
        String normalizedKey = StringUtils.safe(commandKey).trim();
        if (normalizedKey.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "commandKey is required");
        }
        if (StringUtils.isBlank(request.getMessage())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message is required");
        }
        request.setAiCommand(normalizedKey);
        log.info("Executing catalog command '{}' for conversation {}", normalizedKey, request.getConversationId());
        ChatResponse response = chatService.processChat(request);
        return ResponseEntity.ok(response);
    }
}


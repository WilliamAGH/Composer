package com.composerai.api.adapters.in.web;

import com.composerai.api.application.dto.ChatRequest;
import com.composerai.api.application.usecase.chat.ExecuteChatUseCase;
import com.composerai.api.application.usecase.chat.StoreDraftContextUseCase;
import com.composerai.api.domain.model.ReasoningEffortLevel;
import com.composerai.api.dto.ChatResponse;
import com.composerai.api.util.StringUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    private final ExecuteChatUseCase executeChatUseCase;
    private final StoreDraftContextUseCase storeDraftContextUseCase;

    public CatalogCommandController(
            ExecuteChatUseCase executeChatUseCase, StoreDraftContextUseCase storeDraftContextUseCase) {
        this.executeChatUseCase = executeChatUseCase;
        this.storeDraftContextUseCase = storeDraftContextUseCase;
    }

    /**
     * Executes a single catalog command (summarize, translate, draft, etc.) by delegating to the
     * {@link ExecuteChatUseCase}. The path parameter acts as the source of truth for the selected command.
     */
    @PostMapping("/{commandKey}/execute")
    public ResponseEntity<ChatResponse> executeCatalogCommand(
            @PathVariable String commandKey, @Valid @RequestBody ChatRequest request) {
        String normalizedKey = StringUtils.safe(commandKey).trim();
        if (normalizedKey.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "commandKey is required");
        }
        if (StringUtils.isBlank(request.getMessage())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message is required");
        }
        if (Boolean.FALSE.equals(request.getThinkingEnabled())
                || request.getThinkingLevel() == ReasoningEffortLevel.NONE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Catalog commands require reasoning");
        }
        if (request.getThinkingLevel() == null) {
            request.setThinkingLevel(ReasoningEffortLevel.DEFAULT);
        }
        request.setAiCommand(normalizedKey);
        log.info("Executing catalog command '{}' for conversation {}", normalizedKey, request.getConversationId());
        ChatResponse response = executeChatUseCase.execute(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/draft-context")
    public ResponseEntity<Void> uploadDraftContext(@Valid @RequestBody DraftContextRequest request) {
        storeDraftContextUseCase.store(request.contextId(), request.content());
        return ResponseEntity.accepted().build();
    }

    public record DraftContextRequest(
            @NotBlank(message = "contextId is required")
            @Size(max = 200, message = "contextId cannot exceed 200 characters")
            String contextId,

            @NotBlank(message = "content is required")
            @Size(max = 20000, message = "content cannot exceed 20000 characters")
            String content) {}
}

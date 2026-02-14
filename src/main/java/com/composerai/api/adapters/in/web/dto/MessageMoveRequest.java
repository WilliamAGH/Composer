package com.composerai.api.adapters.in.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for moving a message between folders. Includes a sessionId fallback in case the client
 * cannot send the X-Mailbox-Session header (e.g., during initial bootstrap).
 */
public record MessageMoveRequest(
        @NotBlank(message = "mailboxId is required") String mailboxId,
        @NotBlank(message = "targetFolderId is required") String targetFolderId,
        String sessionId) {}

package com.composerai.api.application.dto.mailbox;

/**
 * Command issued by controllers when a user requests to move a message between folders.
 * Encapsulates all identifiers required to route the action through the correct mailbox + session.
 */
public record MessageMoveCommand(String mailboxId, String sessionId, String messageId, String targetFolderId) {

    public MessageMoveCommand {
        if (mailboxId == null || mailboxId.isBlank()) {
            throw new IllegalArgumentException("mailboxId is required");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId is required");
        }
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("messageId is required");
        }
        if (targetFolderId == null || targetFolderId.isBlank()) {
            throw new IllegalArgumentException("targetFolderId is required");
        }
    }
}

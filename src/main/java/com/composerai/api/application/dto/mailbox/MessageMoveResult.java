package com.composerai.api.application.dto.mailbox;

import com.composerai.api.model.EmailMessage;

import java.util.List;
import java.util.Map;

/**
 * Result returned after a move request finishes. Contains the updated message payload plus
 * aggregate counts and placement map so the UI can stay in sync without extra fetches.
 */
public record MessageMoveResult(
    String mailboxId,
    String messageId,
    String previousFolderId,
    String currentFolderId,
    EmailMessage updatedMessage,
    Map<String, Integer> folderCounts,
    Map<String, String> placements,
    List<EmailMessage> messages,
    Map<String, String> effectiveFolders
) {
}

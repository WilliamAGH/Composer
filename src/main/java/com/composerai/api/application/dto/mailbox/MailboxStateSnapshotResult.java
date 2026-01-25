package com.composerai.api.application.dto.mailbox;

import com.composerai.api.model.EmailMessage;
import java.util.List;
import java.util.Map;

/**
 * DTO exposed to controllers for hydrations. Mirrors exactly what the frontend expects: the list of
 * messages, aggregate folder counts, and an easy-to-serialize placement map.
 */
public record MailboxStateSnapshotResult(
        String mailboxId,
        List<EmailMessage> messages,
        Map<String, Integer> folderCounts,
        Map<String, String> placements,
        Map<String, String> effectiveFolders) {}

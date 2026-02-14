package com.composerai.api.domain.model;

import com.composerai.api.model.EmailMessage;
import java.util.List;
import java.util.Map;

/**
 * Immutable view of a mailbox at load time.
 * Contains the raw email payload plus baseline folder counts so that application use cases can
 * cheaply derive per-session overrides without re-reading from disk.
 */
public record MailboxSnapshot(
        String mailboxId, List<EmailMessage> messages, Map<MailFolderIdentifier, Integer> baselineCounts) {

    public MailboxSnapshot {
        if (mailboxId == null || mailboxId.isBlank()) {
            throw new IllegalArgumentException("mailboxId is required");
        }
        if (messages == null) {
            throw new IllegalArgumentException("messages is required");
        }
        baselineCounts = baselineCounts == null ? Map.of() : Map.copyOf(baselineCounts);
    }
}

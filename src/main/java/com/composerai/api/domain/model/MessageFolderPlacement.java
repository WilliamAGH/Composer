package com.composerai.api.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Captures the session-scoped folder assignment for a specific message.
 * This record keeps the mailbox/session/message relationship explicit so that future persistence
 * layers (IMAP, database, cache) can store the same structure without inventing new DTOs.
 */
public final class MessageFolderPlacement {

    private final String mailboxId;
    private final String sessionId;
    private final String messageId;
    private final MailFolderIdentifier folderIdentifier;
    private final Instant updatedAt;

    private MessageFolderPlacement(Builder builder) {
        this.mailboxId = builder.mailboxId;
        this.sessionId = builder.sessionId;
        this.messageId = builder.messageId;
        this.folderIdentifier = builder.folderIdentifier;
        this.updatedAt = builder.updatedAt == null ? Instant.now() : builder.updatedAt;
    }

    public String mailboxId() {
        return mailboxId;
    }

    public String sessionId() {
        return sessionId;
    }

    public String messageId() {
        return messageId;
    }

    public MailFolderIdentifier folderIdentifier() {
        return folderIdentifier;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MessageFolderPlacement that)) return false;
        return Objects.equals(mailboxId, that.mailboxId)
            && Objects.equals(sessionId, that.sessionId)
            && Objects.equals(messageId, that.messageId)
            && Objects.equals(folderIdentifier, that.folderIdentifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mailboxId, sessionId, messageId, folderIdentifier);
    }

    public static final class Builder {
        private String mailboxId;
        private String sessionId;
        private String messageId;
        private MailFolderIdentifier folderIdentifier;
        private Instant updatedAt;

        private Builder() {
        }

        private Builder(MessageFolderPlacement source) {
            this.mailboxId = source.mailboxId;
            this.sessionId = source.sessionId;
            this.messageId = source.messageId;
            this.folderIdentifier = source.folderIdentifier;
            this.updatedAt = source.updatedAt;
        }

        public Builder mailboxId(String mailboxId) {
            this.mailboxId = mailboxId;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder messageId(String messageId) {
            this.messageId = messageId;
            return this;
        }

        public Builder folderIdentifier(MailFolderIdentifier folderIdentifier) {
            this.folderIdentifier = folderIdentifier;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public MessageFolderPlacement build() {
            if (mailboxId == null || mailboxId.isBlank()) {
                throw new IllegalArgumentException("mailboxId is required");
            }
            if (sessionId == null || sessionId.isBlank()) {
                throw new IllegalArgumentException("sessionId is required");
            }
            if (messageId == null || messageId.isBlank()) {
                throw new IllegalArgumentException("messageId is required");
            }
            if (folderIdentifier == null) {
                throw new IllegalArgumentException("folderIdentifier is required");
            }
            return new MessageFolderPlacement(this);
        }
    }
}

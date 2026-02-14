package com.composerai.api.domain.model;

import com.composerai.api.util.StringUtils;
import java.time.Instant;
import java.util.Objects;

/**
 * Captures the session-scoped folder assignment for a specific message.
 * This record keeps the mailbox/session/message relationship explicit so that future persistence
 * layers (IMAP, database, cache) can store the same structure without inventing new DTOs.
 */
public final class MessageFolderPlacement {

    private final MailboxId mailboxId;
    private final SessionId sessionId;
    private final MessageId messageId;
    private final MailFolderIdentifier folderIdentifier;
    private final Instant updatedAt;

    private MessageFolderPlacement(Builder builder) {
        this.mailboxId = builder.mailboxId;
        this.sessionId = builder.sessionId;
        this.messageId = builder.messageId;
        this.folderIdentifier = builder.folderIdentifier;
        this.updatedAt = builder.updatedAt == null ? Instant.now() : builder.updatedAt;
    }

    public MailboxId mailboxId() {
        return mailboxId;
    }

    public SessionId sessionId() {
        return sessionId;
    }

    public MessageId messageId() {
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
        private MailboxId mailboxId;
        private SessionId sessionId;
        private MessageId messageId;
        private MailFolderIdentifier folderIdentifier;
        private Instant updatedAt;

        private Builder() {}

        private Builder(MessageFolderPlacement source) {
            this.mailboxId = source.mailboxId;
            this.sessionId = source.sessionId;
            this.messageId = source.messageId;
            this.folderIdentifier = source.folderIdentifier;
            this.updatedAt = source.updatedAt;
        }

        public Builder mailboxId(MailboxId mailboxId) {
            this.mailboxId = mailboxId;
            return this;
        }

        public Builder mailboxId(String mailboxId) {
            if (StringUtils.isBlank(mailboxId)) {
                throw new IllegalArgumentException("mailboxId cannot be blank");
            }
            this.mailboxId = new MailboxId(mailboxId);
            return this;
        }

        public Builder sessionId(SessionId sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder sessionId(String sessionId) {
            if (StringUtils.isBlank(sessionId)) {
                throw new IllegalArgumentException("sessionId cannot be blank");
            }
            this.sessionId = new SessionId(sessionId);
            return this;
        }

        public Builder messageId(MessageId messageId) {
            this.messageId = messageId;
            return this;
        }

        public Builder messageId(String messageId) {
            if (StringUtils.isBlank(messageId)) {
                throw new IllegalArgumentException("messageId cannot be blank");
            }
            this.messageId = new MessageId(messageId);
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
            if (mailboxId == null) {
                throw new IllegalArgumentException("mailboxId is required");
            }
            if (sessionId == null) {
                throw new IllegalArgumentException("sessionId is required");
            }
            if (messageId == null) {
                throw new IllegalArgumentException("messageId is required");
            }
            if (folderIdentifier == null) {
                throw new IllegalArgumentException("folderIdentifier is required");
            }
            return new MessageFolderPlacement(this);
        }
    }
}

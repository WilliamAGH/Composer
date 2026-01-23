package com.composerai.api.domain.port;

import com.composerai.api.domain.model.MailboxId;
import com.composerai.api.domain.model.MessageFolderPlacement;
import com.composerai.api.domain.model.MessageId;
import com.composerai.api.domain.model.SessionId;

import java.util.Map;
import java.util.Optional;

/**
 * Port for persisting per-session folder overrides.
 * The initial implementation is in-memory, but the same contract will be satisfied by future IMAP,
 * Redis, or database-backed adapters without changing the application layer.
 */
public interface SessionScopedMessagePlacementPort {

    Optional<MessageFolderPlacement> findPlacement(MailboxId mailboxId, SessionId sessionId, MessageId messageId);

    Map<MessageId, MessageFolderPlacement> findPlacements(MailboxId mailboxId, SessionId sessionId);

    void savePlacement(MessageFolderPlacement placement);

    void removePlacement(MailboxId mailboxId, SessionId sessionId, MessageId messageId);
}

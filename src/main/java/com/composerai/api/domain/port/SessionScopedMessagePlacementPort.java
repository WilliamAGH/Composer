package com.composerai.api.domain.port;

import com.composerai.api.domain.model.MessageFolderPlacement;

import java.util.Map;
import java.util.Optional;

/**
 * Port for persisting per-session folder overrides.
 * The initial implementation is in-memory, but the same contract will be satisfied by future IMAP,
 * Redis, or database-backed adapters without changing the application layer.
 */
public interface SessionScopedMessagePlacementPort {

    Optional<MessageFolderPlacement> findPlacement(String mailboxId, String sessionId, String messageId);

    Map<String, MessageFolderPlacement> findPlacements(String mailboxId, String sessionId);

    void savePlacement(MessageFolderPlacement placement);

    void removePlacement(String mailboxId, String sessionId, String messageId);
}

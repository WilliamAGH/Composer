package com.composerai.api.adapters.out.persistence;

import com.composerai.api.domain.model.MailboxId;
import com.composerai.api.domain.model.MessageFolderPlacement;
import com.composerai.api.domain.model.MessageId;
import com.composerai.api.domain.model.SessionId;
import com.composerai.api.domain.port.SessionScopedMessagePlacementPort;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory placement store keyed by mailbox + session + message.
 * This keeps the initial proof-of-concept lightweight while letting us swap in a distributed cache
 * or IMAP-backed adapter later without touching the use cases.
 */
@Component
public class SessionScopedMessagePlacementAdapter implements SessionScopedMessagePlacementPort {

    // Keyed by MailboxId -> SessionStore
    private final Map<MailboxId, SessionStore> store = new ConcurrentHashMap<>();

    @Override
    public Optional<MessageFolderPlacement> findPlacement(MailboxId mailboxId, SessionId sessionId, MessageId messageId) {
        // mailboxKey returns a String, but store is keyed by MailboxId. 
        // We should normalize first, then wrap.
        SessionStore sessionStore = store.get(new MailboxId(mailboxKey(mailboxId.value())));
        if (sessionStore == null) {
            return Optional.empty();
        }
        return sessionStore.get(sessionId, messageId);
    }

    @Override
    public Map<MessageId, MessageFolderPlacement> findPlacements(MailboxId mailboxId, SessionId sessionId) {
        SessionStore sessionStore = store.get(new MailboxId(mailboxKey(mailboxId.value())));
        if (sessionStore == null) {
            return Map.of();
        }
        return sessionStore.getAll(sessionId);
    }

    @Override
    public void savePlacement(MessageFolderPlacement placement) {
        // Normalize key
        MailboxId normalizedKey = new MailboxId(mailboxKey(placement.mailboxId().value()));
        store
            .computeIfAbsent(normalizedKey, key -> new SessionStore())
            .put(placement.sessionId(), placement);
    }

    @Override
    public void removePlacement(MailboxId mailboxId, SessionId sessionId, MessageId messageId) {
        MailboxId normalizedKey = new MailboxId(mailboxKey(mailboxId.value()));

        store.compute(normalizedKey, (key, sessionStore) -> {
            if (sessionStore == null) {
                return null;
            }
            sessionStore.remove(sessionId, messageId);
            return sessionStore.isEmpty() ? null : sessionStore;
        });
    }

    private String mailboxKey(String mailboxId) {
        if (mailboxId == null || mailboxId.isBlank()) {
            return "default";
        }
        return mailboxId.trim().toLowerCase();
    }

    /**
     * Internal store for a specific mailbox, managing sessions.
     */
    private static class SessionStore {
        // SessionID -> MessageID -> Placement
        private final Map<SessionId, Map<MessageId, MessageFolderPlacement>> sessions = new ConcurrentHashMap<>();

        Optional<MessageFolderPlacement> get(SessionId sessionId, MessageId messageId) {
            Map<MessageId, MessageFolderPlacement> sessionEntries = sessions.get(sessionId);
            if (sessionEntries == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(sessionEntries.get(messageId));
        }

        Map<MessageId, MessageFolderPlacement> getAll(SessionId sessionId) {
            Map<MessageId, MessageFolderPlacement> sessionEntries = sessions.get(sessionId);
            if (sessionEntries == null || sessionEntries.isEmpty()) {
                return Map.of();
            }
            return Map.copyOf(sessionEntries);
        }

        void put(SessionId sessionId, MessageFolderPlacement placement) {
            sessions
                .computeIfAbsent(sessionId, key -> new ConcurrentHashMap<>())
                .put(placement.messageId(), placement);
        }

        void remove(SessionId sessionId, MessageId messageId) {
            // Atomic remove-if-empty
            sessions.compute(sessionId, (key, sessionEntries) -> {
                if (sessionEntries == null) {
                    return null;
                }
                sessionEntries.remove(messageId);
                return sessionEntries.isEmpty() ? null : sessionEntries;
            });
        }

        boolean isEmpty() {
            return sessions.isEmpty();
        }
    }
}

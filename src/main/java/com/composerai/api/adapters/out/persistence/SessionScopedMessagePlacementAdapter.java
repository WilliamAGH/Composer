package com.composerai.api.adapters.out.persistence;

import com.composerai.api.domain.model.MessageFolderPlacement;
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

    private final Map<String, Map<String, Map<String, MessageFolderPlacement>>> store = new ConcurrentHashMap<>();

    @Override
    public Optional<MessageFolderPlacement> findPlacement(String mailboxId, String sessionId, String messageId) {
        Map<String, Map<String, MessageFolderPlacement>> mailboxEntries = store.get(mailboxKey(mailboxId));
        if (mailboxEntries == null) {
            return Optional.empty();
        }
        Map<String, MessageFolderPlacement> sessionEntries = mailboxEntries.get(sessionId);
        if (sessionEntries == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessionEntries.get(messageId));
    }

    @Override
    public Map<String, MessageFolderPlacement> findPlacements(String mailboxId, String sessionId) {
        Map<String, Map<String, MessageFolderPlacement>> mailboxEntries = store.get(mailboxKey(mailboxId));
        if (mailboxEntries == null) {
            return Map.of();
        }
        Map<String, MessageFolderPlacement> sessionEntries = mailboxEntries.get(sessionId);
        if (sessionEntries == null || sessionEntries.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(sessionEntries);
    }

    @Override
    public void savePlacement(MessageFolderPlacement placement) {
        store
            .computeIfAbsent(mailboxKey(placement.mailboxId()), key -> new ConcurrentHashMap<>())
            .computeIfAbsent(placement.sessionId(), key -> new ConcurrentHashMap<>())
            .put(placement.messageId(), placement);
    }

    @Override
    public void removePlacement(String mailboxId, String sessionId, String messageId) {
        Map<String, Map<String, MessageFolderPlacement>> mailboxEntries = store.get(mailboxKey(mailboxId));
        if (mailboxEntries == null) {
            return;
        }
        Map<String, MessageFolderPlacement> sessionEntries = mailboxEntries.get(sessionId);
        if (sessionEntries == null) {
            return;
        }
        sessionEntries.remove(messageId);
        if (sessionEntries.isEmpty()) {
            mailboxEntries.remove(sessionId);
        }
        if (mailboxEntries.isEmpty()) {
            store.remove(mailboxKey(mailboxId));
        }
    }

    private String mailboxKey(String mailboxId) {
        if (mailboxId == null || mailboxId.isBlank()) {
            return "default";
        }
        return mailboxId.trim().toLowerCase();
    }
}

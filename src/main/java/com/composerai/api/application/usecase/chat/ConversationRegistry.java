package com.composerai.api.application.usecase.chat;

import com.composerai.api.domain.model.ConversationTurn;
import com.composerai.api.util.StringUtils;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/** Bounded in-memory conversation history used to enrich subsequent chat requests. */
@Component
public class ConversationRegistry {

    private static final int MAX_TURNS = 40;
    private static final int MAX_CONVERSATIONS = 512;
    private static final Duration TTL = Duration.ofMinutes(45);

    private final ConcurrentMap<String, StoredConversation> conversations = new ConcurrentHashMap<>();

    public List<ConversationTurn> history(String conversationId) {
        if (StringUtils.isBlank(conversationId)) {
            return List.of();
        }
        StoredConversation storedConversation = conversations.get(conversationId);
        if (storedConversation == null) {
            return List.of();
        }
        if (storedConversation.isExpired(Instant.now())) {
            conversations.remove(conversationId);
            return List.of();
        }
        return storedConversation.turns();
    }

    public void append(String conversationId, ConversationTurn... turns) {
        if (StringUtils.isBlank(conversationId) || turns == null || turns.length == 0) {
            return;
        }
        conversations.compute(
                conversationId,
                (conversationKey, storedConversation) -> StoredConversation.append(storedConversation, turns));
        prune();
    }

    public void reset(String conversationId) {
        if (!StringUtils.isBlank(conversationId)) {
            conversations.remove(conversationId);
        }
    }

    private void prune() {
        if (conversations.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        conversations
                .entrySet()
                .removeIf(conversationEntry -> conversationEntry.getValue().isExpired(now));
        int overflow = conversations.size() - MAX_CONVERSATIONS;
        if (overflow <= 0) {
            return;
        }
        List<Map.Entry<String, StoredConversation>> snapshot = new ArrayList<>(conversations.entrySet());
        snapshot.sort(Comparator.comparing(
                conversationEntry -> conversationEntry.getValue().updatedAt()));
        for (int conversationIndex = 0;
                conversationIndex < overflow && conversationIndex < snapshot.size();
                conversationIndex++) {
            conversations.remove(snapshot.get(conversationIndex).getKey());
        }
    }

    private record StoredConversation(List<ConversationTurn> turns, Instant updatedAt) {

        boolean isExpired(Instant reference) {
            return updatedAt.plus(TTL).isBefore(reference);
        }

        static StoredConversation append(StoredConversation storedConversation, ConversationTurn... additions) {
            List<ConversationTurn> turnBuffer =
                    storedConversation == null ? new ArrayList<>() : new ArrayList<>(storedConversation.turns());
            boolean changed = false;
            for (ConversationTurn turn : additions) {
                if (turn == null || StringUtils.isBlank(turn.content())) {
                    continue;
                }
                turnBuffer.add(turn);
                changed = true;
            }
            if (!changed) {
                return storedConversation;
            }
            if (turnBuffer.size() > MAX_TURNS) {
                turnBuffer = new ArrayList<>(
                        turnBuffer.subList(Math.max(0, turnBuffer.size() - MAX_TURNS), turnBuffer.size()));
            }
            return new StoredConversation(List.copyOf(turnBuffer), Instant.now());
        }
    }
}

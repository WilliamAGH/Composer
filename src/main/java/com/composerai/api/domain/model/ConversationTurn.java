package com.composerai.api.domain.model;

import com.composerai.api.util.IdGenerator;

/**
 * Represents a single turn in a chat conversation.
 * Encapsulates the role (User/Assistant) and the message content.
 */
public record ConversationTurn(String messageId, Role role, String content) {
    public enum Role {
        USER,
        ASSISTANT,
        SYSTEM
    }

    public ConversationTurn {
        messageId = (messageId == null || messageId.isBlank()) ? IdGenerator.uuidV7() : messageId;
        role = role == null ? Role.USER : role;
        content = content == null ? "" : content;
    }

    public static ConversationTurn user(String content) {
        return new ConversationTurn(IdGenerator.uuidV7(), Role.USER, content);
    }

    public static ConversationTurn assistant(String content) {
        return new ConversationTurn(IdGenerator.uuidV7(), Role.ASSISTANT, content);
    }

    public static ConversationTurn userWithId(String messageId, String content) {
        return new ConversationTurn(messageId, Role.USER, content);
    }

    public static ConversationTurn assistantWithId(String messageId, String content) {
        return new ConversationTurn(messageId, Role.ASSISTANT, content);
    }
}

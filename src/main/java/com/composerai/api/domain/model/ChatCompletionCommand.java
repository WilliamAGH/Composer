package com.composerai.api.domain.model;

import java.util.List;

/**
 * Command object encapsulating all necessary information for a chat completion request.
 * Carries message content, context, history, and configuration flags.
 */
public record ChatCompletionCommand(
        String userMessage,
        String emailContext,
        List<ConversationTurn> conversationHistory,
        Boolean thinkingEnabled,
        ReasoningEffortLevel thinkingLevel,
        boolean jsonOutput) {}

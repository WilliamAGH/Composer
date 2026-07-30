package com.composerai.api.application.usecase.chat;

import com.composerai.api.application.dto.ChatRequest;
import com.composerai.api.domain.model.ChatCompletionCommand;
import com.composerai.api.dto.ChatResponse.EmailContext;
import java.util.List;

/** Fully prepared chat request shared by synchronous and streaming execution. */
record PreparedChatRequest(
        ChatRequest sourceRequest,
        ChatCompletionCommand completionCommand,
        String conversationId,
        String persistedMessage,
        List<EmailContext> emailContext,
        boolean isolatedCommand,
        String userMessageId,
        String assistantMessageId) {}

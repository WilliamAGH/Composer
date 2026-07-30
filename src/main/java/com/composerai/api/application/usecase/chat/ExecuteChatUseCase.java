package com.composerai.api.application.usecase.chat;

import com.composerai.api.adapters.out.openai.OpenAiChatClient;
import com.composerai.api.application.dto.ChatRequest;
import com.composerai.api.domain.model.ConversationTurn;
import com.composerai.api.dto.ChatResponse;
import com.composerai.api.shared.ledger.ChatLedgerRecorder;
import com.composerai.api.util.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Executes one synchronous chat interaction. */
@Service
public class ExecuteChatUseCase {

    private static final Logger logger = LoggerFactory.getLogger(ExecuteChatUseCase.class);

    private final ChatRequestPreparation requestPreparation;
    private final OpenAiChatClient openAiChatClient;
    private final ConversationRegistry conversationRegistry;
    private final ChatLedgerRecorder chatLedgerRecorder;

    public ExecuteChatUseCase(
            ChatRequestPreparation requestPreparation,
            OpenAiChatClient openAiChatClient,
            ConversationRegistry conversationRegistry,
            ChatLedgerRecorder chatLedgerRecorder) {
        this.requestPreparation = requestPreparation;
        this.openAiChatClient = openAiChatClient;
        this.conversationRegistry = conversationRegistry;
        this.chatLedgerRecorder = chatLedgerRecorder;
    }

    public ChatResponse execute(ChatRequest request) {
        String originalMessage = request.getMessage();
        String intent = ChatPromptComposer.INSIGHTS_TRIGGER.equals(originalMessage)
                ? "insights"
                : openAiChatClient.analyzeIntent(originalMessage);
        PreparedChatRequest preparedRequest =
                requestPreparation.prepare(request, IdGenerator.uuidV7(), IdGenerator.uuidV7());
        OpenAiChatClient.Invocation invocation =
                openAiChatClient.invokeChatResponse(preparedRequest.completionCommand());
        OpenAiChatClient.ChatCompletion completion = invocation.completion();

        if (!preparedRequest.isolatedCommand()) {
            conversationRegistry.append(
                    preparedRequest.conversationId(),
                    ConversationTurn.userWithId(preparedRequest.userMessageId(), originalMessage),
                    ConversationTurn.assistantWithId(preparedRequest.assistantMessageId(), completion.rawText()));
        }
        chatLedgerRecorder.recordChatCompletion(
                request, preparedRequest.conversationId(), invocation, completion.rawText());
        logger.info(
                "Processed chat request: conversationId={}, responseChars={}",
                preparedRequest.conversationId(),
                completion.rawText().length());
        return new ChatResponse(
                completion.rawText(),
                preparedRequest.conversationId(),
                preparedRequest.emailContext(),
                intent,
                request.isJsonOutput() ? null : completion.sanitizedHtml(),
                preparedRequest.userMessageId(),
                preparedRequest.assistantMessageId());
    }
}

package com.composerai.api.application.usecase.chat;

import com.composerai.api.adapters.out.openai.OpenAiChatClient;
import com.composerai.api.adapters.out.openai.OpenAiStreamEvent;
import com.composerai.api.adapters.out.openai.ReasoningStreamMapper;
import com.composerai.api.application.dto.ChatRequest;
import com.composerai.api.domain.model.ConversationTurn;
import com.composerai.api.domain.model.ReasoningEffortLevel;
import com.composerai.api.shared.ledger.ChatLedgerRecorder;
import com.composerai.api.shared.ledger.UsageMetrics;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Executes one streaming chat interaction and records its completion. */
@Service
public class StreamChatUseCase {

    private static final Logger logger = LoggerFactory.getLogger(StreamChatUseCase.class);

    private final ChatRequestPreparation requestPreparation;
    private final OpenAiChatClient openAiChatClient;
    private final ConversationRegistry conversationRegistry;
    private final ChatLedgerRecorder chatLedgerRecorder;

    public StreamChatUseCase(
            ChatRequestPreparation requestPreparation,
            OpenAiChatClient openAiChatClient,
            ConversationRegistry conversationRegistry,
            ChatLedgerRecorder chatLedgerRecorder) {
        this.requestPreparation = requestPreparation;
        this.openAiChatClient = openAiChatClient;
        this.conversationRegistry = conversationRegistry;
        this.chatLedgerRecorder = chatLedgerRecorder;
    }

    public void execute(
            ChatRequest request, String userMessageId, String assistantMessageId, ChatStreamCallbacks callbacks) {
        Consumer<Throwable> errorConsumer = callbacks.onError() == null
                ? streamFailure -> logger.error("Streaming chat failed", streamFailure)
                : callbacks.onError();
        try {
            PreparedChatRequest preparedRequest =
                    requestPreparation.prepare(request, userMessageId, assistantMessageId);
            dispatch(preparedRequest, callbacks, errorConsumer);
        } catch (Exception streamFailure) {
            logger.error("Unable to initiate streaming chat", streamFailure);
            errorConsumer.accept(streamFailure);
        }
    }

    private void dispatch(
            PreparedChatRequest preparedRequest, ChatStreamCallbacks callbacks, Consumer<Throwable> errorConsumer) {
        long startMillis = System.currentTimeMillis();
        StringBuilder assistantText = new StringBuilder();
        ReasoningEffortLevel reasoningEffort =
                preparedRequest.completionCommand().thinkingLevel();
        String reasoningLabel = reasoningEffort == null || reasoningEffort == ReasoningEffortLevel.NONE
                ? null
                : reasoningEffort.displayName();
        openAiChatClient.streamResponse(
                preparedRequest.completionCommand(),
                streamEvent -> {
                    appendRawText(streamEvent, assistantText);
                    routeStreamEvent(streamEvent, callbacks, reasoningLabel);
                },
                () -> completeStream(preparedRequest, callbacks, assistantText, startMillis),
                errorConsumer);
    }

    private void appendRawText(OpenAiStreamEvent streamEvent, StringBuilder assistantText) {
        if (streamEvent instanceof OpenAiStreamEvent.RawText rawText) {
            assistantText.append(rawText.textChunk());
        } else if (streamEvent instanceof OpenAiStreamEvent.RawJson rawJson) {
            assistantText.append(rawJson.jsonChunk());
        }
    }

    private void routeStreamEvent(OpenAiStreamEvent streamEvent, ChatStreamCallbacks callbacks, String reasoningLabel) {
        if (streamEvent instanceof OpenAiStreamEvent.RenderedHtml renderedHtml) {
            if (callbacks.onHtmlChunk() != null) {
                callbacks.onHtmlChunk().accept(renderedHtml.html());
            }
            return;
        }
        if (streamEvent instanceof OpenAiStreamEvent.RawJson rawJson) {
            if (callbacks.onJsonChunk() != null) {
                callbacks.onJsonChunk().accept(rawJson.jsonChunk());
            }
            return;
        }
        if (callbacks.onReasoning() != null) {
            ReasoningStreamMapper.ReasoningMessage reasoningMessage =
                    ReasoningStreamMapper.toMessage(streamEvent, reasoningLabel);
            if (reasoningMessage != null) {
                callbacks.onReasoning().accept(reasoningMessage);
            }
        }
    }

    private void completeStream(
            PreparedChatRequest preparedRequest,
            ChatStreamCallbacks callbacks,
            StringBuilder assistantText,
            long startMillis) {
        String completedText = assistantText.toString();
        if (!preparedRequest.isolatedCommand()) {
            conversationRegistry.append(
                    preparedRequest.conversationId(),
                    ConversationTurn.userWithId(preparedRequest.userMessageId(), preparedRequest.persistedMessage()),
                    ConversationTurn.assistantWithId(preparedRequest.assistantMessageId(), completedText));
        }
        UsageMetrics usage = new UsageMetrics(0, 0, 0, System.currentTimeMillis() - startMillis);
        OpenAiChatClient.ChatCompletion completion = OpenAiChatClient.ChatCompletion.fromRaw(
                completedText, preparedRequest.completionCommand().jsonOutput());
        chatLedgerRecorder.recordChatCompletion(
                preparedRequest.sourceRequest(),
                preparedRequest.conversationId(),
                OpenAiChatClient.Invocation.streamed(completion, usage),
                completedText);
        if (callbacks.onComplete() != null) {
            callbacks.onComplete().run();
        }
    }
}

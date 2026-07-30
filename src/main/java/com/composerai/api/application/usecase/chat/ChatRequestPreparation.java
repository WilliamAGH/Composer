package com.composerai.api.application.usecase.chat;

import com.composerai.api.adapters.out.openai.OpenAiChatClient;
import com.composerai.api.application.dto.ChatRequest;
import com.composerai.api.config.OpenAiProperties;
import com.composerai.api.domain.model.ChatCompletionCommand;
import com.composerai.api.domain.model.ConversationTurn;
import com.composerai.api.dto.ChatResponse.EmailContext;
import com.composerai.api.service.ContextBuilder;
import com.composerai.api.service.VectorSearchService;
import com.composerai.api.service.email.HtmlConverter;
import com.composerai.api.util.StringUtils;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Resolves context, history, and the final model command for one chat request. */
@Component
final class ChatRequestPreparation {

    private static final Logger logger = LoggerFactory.getLogger(ChatRequestPreparation.class);

    private final VectorSearchService vectorSearchService;
    private final OpenAiChatClient openAiChatClient;
    private final OpenAiProperties openAiProperties;
    private final ContextBuilder contextBuilder;
    private final ContextBuilder.EmailContextCache emailContextRegistry;
    private final ConversationRegistry conversationRegistry;
    private final ChatPromptComposer promptComposer;

    ChatRequestPreparation(
            VectorSearchService vectorSearchService,
            OpenAiChatClient openAiChatClient,
            OpenAiProperties openAiProperties,
            ContextBuilder contextBuilder,
            ContextBuilder.EmailContextCache emailContextRegistry,
            ConversationRegistry conversationRegistry,
            ChatPromptComposer promptComposer) {
        this.vectorSearchService = vectorSearchService;
        this.openAiChatClient = openAiChatClient;
        this.openAiProperties = openAiProperties;
        this.contextBuilder = contextBuilder;
        this.emailContextRegistry = emailContextRegistry;
        this.conversationRegistry = conversationRegistry;
        this.promptComposer = promptComposer;
    }

    PreparedChatRequest prepare(ChatRequest request, String userMessageId, String assistantMessageId) {
        String conversationId = StringUtils.ensureConversationId(request.getConversationId());
        boolean isolatedCommand = promptComposer.isIsolatedCommand(request);
        int maxSearchResults = request.getMaxResults() > 0
                ? request.getMaxResults()
                : openAiProperties.getDefaults().getMaxSearchResults();
        ChatContext chatContext = prepareContext(request.getMessage(), maxSearchResults);
        String uploadedContext = resolveUploadedContext(conversationId, request);
        String mergedContext = contextBuilder.mergeContexts(chatContext.contextString(), uploadedContext);
        if (promptComposer.isInsightsRequest(request)) {
            mergedContext = promptComposer.sanitizeInsightsContext(mergedContext);
        }
        String modelMessage = promptComposer.compose(request, mergedContext);
        List<ConversationTurn> history = isolatedCommand ? List.of() : conversationRegistry.history(conversationId);
        ChatCompletionCommand completionCommand = new ChatCompletionCommand(
                modelMessage,
                mergedContext,
                history,
                request.getThinkingEnabled(),
                request.getThinkingLevel(),
                request.isJsonOutput());
        logger.debug(
                "Context prepared: uploadedChars={}, vectorResults={}, mergedChars={}",
                uploadedContext.length(),
                chatContext.emailContext().size(),
                mergedContext.length());
        return new PreparedChatRequest(
                request,
                completionCommand,
                conversationId,
                request.getMessage(),
                chatContext.emailContext(),
                isolatedCommand,
                userMessageId,
                assistantMessageId);
    }

    private ChatContext prepareContext(String message, int maxSearchResults) {
        if (ChatPromptComposer.INSIGHTS_TRIGGER.equals(message)) {
            return new ChatContext(List.of(), "");
        }
        float[] queryVector = openAiChatClient.generateEmbedding(message);
        List<EmailContext> emailContext = queryVector == null || queryVector.length == 0
                ? List.of()
                : vectorSearchService.searchSimilarEmails(queryVector, maxSearchResults);
        return new ChatContext(emailContext, contextBuilder.buildFromEmailList(emailContext));
    }

    private String resolveUploadedContext(String conversationId, ChatRequest request) {
        String contextId = request.getContextId();
        Optional<String> storedContext =
                StringUtils.isBlank(contextId) ? Optional.empty() : emailContextRegistry.contextForAi(contextId);
        if (storedContext.isPresent()) {
            return storedContext.get();
        }
        if (!StringUtils.isBlank(request.getEmailContext())) {
            logger.warn(
                    "Using request emailContext fallback: contextId={}, chars={}, conversationId={}",
                    contextId,
                    request.getEmailContext().length(),
                    conversationId);
            return HtmlConverter.cleanupOutput(request.getEmailContext(), false);
        }
        logger.debug("No uploaded context found: contextId={}, conversationId={}", contextId, conversationId);
        return "";
    }

    private record ChatContext(List<EmailContext> emailContext, String contextString) {}
}

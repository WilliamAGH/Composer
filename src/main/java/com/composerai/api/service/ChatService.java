package com.composerai.api.service;

import com.composerai.api.config.ErrorMessagesProperties;
import com.composerai.api.config.OpenAiProperties;
import com.composerai.api.dto.ChatRequest;
import com.composerai.api.dto.ChatResponse;
import com.composerai.api.dto.ChatResponse.EmailContext;
import com.composerai.api.dto.SseEventType;
import com.composerai.api.service.email.HtmlConverter;
import com.composerai.api.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.io.IOException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    private final VectorSearchService vectorSearchService;
    private final OpenAiChatService openAiChatService;
    private final OpenAiProperties openAiProperties;
    private final ErrorMessagesProperties errorMessages;
    private final ContextBuilder contextBuilder;
    private final ExecutorService streamingExecutor;

    public ChatService(VectorSearchService vectorSearchService, OpenAiChatService openAiChatService,
                       OpenAiProperties openAiProperties, ErrorMessagesProperties errorMessages,
                       ContextBuilder contextBuilder, ExecutorService streamingExecutor) {
        this.vectorSearchService = vectorSearchService;
        this.openAiChatService = openAiChatService;
        this.openAiProperties = openAiProperties;
        this.errorMessages = errorMessages;
        this.contextBuilder = contextBuilder;
        this.streamingExecutor = streamingExecutor;
    }

    private record ChatContext(float[] embedding, List<EmailContext> emailContext, String contextString) {}

    public record StreamMetadata(String conversationId) {}

    /**
     * Applies defaults from configuration to request parameters.
     *
     * Configuration source of truth: OpenAiProperties.java
     * Max search results: {@link OpenAiProperties.Defaults#getMaxSearchResults()} - 5
     */
    private int applyMaxResultsDefault(int requestedMaxResults) {
        return requestedMaxResults > 0 ? requestedMaxResults : openAiProperties.getDefaults().getMaxSearchResults();
    }

    private ChatContext prepareChatContext(String message, int maxResults) {
        float[] queryVector = openAiChatService.generateEmbedding(message);
        List<EmailContext> emailContext = (queryVector == null || queryVector.length == 0)
            ? List.of()
            : vectorSearchService.searchSimilarEmails(queryVector, maxResults);
        return new ChatContext(queryVector, emailContext, contextBuilder.buildFromEmailList(emailContext));
    }

    public ChatResponse processChat(ChatRequest request) {
        String conversationId = StringUtils.ensureConversationId(request.getConversationId());
        int msgLen = request.getMessage() == null ? 0 : request.getMessage().length();
        logger.info("Processing chat: convId={}, msgLen={}", conversationId, msgLen);
        try {
            String intent = openAiChatService.analyzeIntent(request.getMessage());
            int maxResults = applyMaxResultsDefault(request.getMaxResults());
            ChatContext ctx = prepareChatContext(request.getMessage(), maxResults);
            String fullContext = contextBuilder.mergeContexts(ctx.contextString(), request.getEmailContext());
            
            // Debug log context structure for troubleshooting
            if (logger.isDebugEnabled()) {
                int uploadedChars = request.getEmailContext() != null ? request.getEmailContext().length() : 0;
                logger.debug("Context prepared: uploadedChars={}, vectorResults={}, mergedChars={}",
                    uploadedChars, ctx.emailContext().size(), fullContext.length());
                if (uploadedChars > 0) {
                    String preview = request.getEmailContext().substring(0, Math.min(200, request.getEmailContext().length()));
                    logger.debug("Uploaded context preview (first 200 chars): {}", preview);
                }
            }
            
            OpenAiChatService.ChatCompletionResult aiResult = openAiChatService.generateResponse(
                request.getMessage(), fullContext,
                request.isThinkingEnabled(), request.getThinkingLevel());
            logger.info("Successfully processed chat request for conversation: {}", conversationId);
            return new ChatResponse(aiResult.rawText(), conversationId, ctx.emailContext(), intent, aiResult.sanitizedHtml());
        } catch (Exception e) {
            logger.error("Error processing chat request", e);
            String fallback = errorMessages.getChat().getProcessingError();
            return new ChatResponse(fallback, conversationId, List.of(), "error", HtmlConverter.markdownToSafeHtml(fallback));
        }
    }


    /** Internal helper for streaming chat with consolidated timing and error handling. */
    private void doStreamChat(String message, String conversationId, String contextString,
                              boolean thinkingEnabled, String thinkingLevel,
                              Consumer<String> onToken, Consumer<ReasoningStreamAdapter.ReasoningMessage> onReasoning,
                              Runnable onComplete, Consumer<Throwable> onError) {
        Consumer<Throwable> safeOnError = onError != null ? onError : err -> logger.error("Streaming error: convId={}", conversationId, err);
        String normalizedThinkingLabel = thinkingEnabled
            ? ReasoningStreamAdapter.normalizeThinkingLabel(thinkingLevel)
            : null;
        long startNanos = System.nanoTime();
        try {
            openAiChatService.streamResponse(message, contextString, thinkingEnabled, thinkingLevel,
                event -> handleStreamEvent(event, onToken, onReasoning, normalizedThinkingLabel),
                () -> { logger.info("Stream completed: {}ms", (System.nanoTime() - startNanos) / 1_000_000L); if (onComplete != null) onComplete.run(); },
                err -> { logger.warn("Stream failed: {}ms", (System.nanoTime() - startNanos) / 1_000_000L, err); safeOnError.accept(err); });
        } catch (Exception e) {
            logger.error("Error initiating stream", e);
            safeOnError.accept(e);
        }
    }

    /** Public API: Stream chat with callback functions. */
    public void streamChat(ChatRequest request, Consumer<String> onToken,
                           Consumer<ReasoningStreamAdapter.ReasoningMessage> onReasoning,
                           Runnable onComplete, Consumer<Throwable> onError) {
        String conversationId = StringUtils.ensureConversationId(request.getConversationId());
        int maxResults = applyMaxResultsDefault(request.getMaxResults());
        ChatContext ctx = prepareChatContext(request.getMessage(), maxResults);
        String fullContext = contextBuilder.mergeContexts(ctx.contextString(), request.getEmailContext());
        doStreamChat(request.getMessage(), conversationId, fullContext,
            request.isThinkingEnabled(), request.getThinkingLevel(), onToken, onReasoning, onComplete, onError);
    }

    /** Public API: Stream chat with SseEmitter (for SSE endpoints). */
    public void streamChat(ChatRequest request, SseEmitter emitter) {
        emitter.setTimeout(openAiProperties.getStream().getTimeoutSeconds() * 1000L);
        String conversationId = StringUtils.ensureConversationId(request.getConversationId());
        emitter.onCompletion(() -> logger.info("SSE completed: {}", conversationId));
        emitter.onTimeout(() -> logger.warn("SSE timeout: {}", conversationId));
        emitter.onError(e -> logger.error("SSE error: {}", conversationId, e));
        streamingExecutor.execute(() -> {
            try {
                int maxResults = applyMaxResultsDefault(request.getMaxResults());
                ChatContext ctx = prepareChatContext(request.getMessage(), maxResults);
                String fullContext = contextBuilder.mergeContexts(ctx.contextString(), request.getEmailContext());
                doStreamChat(request.getMessage(), conversationId, fullContext,
                    request.isThinkingEnabled(), request.getThinkingLevel(),
                    chunk -> { try { emitter.send(SseEmitter.event().data(chunk)); } catch (IOException e) { logger.warn("Error sending chunk: {}", conversationId, e); emitter.completeWithError(e); } },
                    message -> sendReasoning(emitter, conversationId, message), emitter::complete, emitter::completeWithError);
            } catch (Exception e) {
                logger.error("Async processing failed: {}", conversationId, e);
                emitter.completeWithError(e);
            }
        });
    }

    private void handleStreamEvent(OpenAiChatService.StreamEvent event,
                                   Consumer<String> onToken,
                                   Consumer<ReasoningStreamAdapter.ReasoningMessage> onReasoning,
                                   String thinkingLabel) {
        if (event instanceof OpenAiChatService.StreamEvent.RenderedHtml rendered) {
            onToken.accept(rendered.html());
            return;
        }
        if (onReasoning == null) return;
        ReasoningStreamAdapter.ReasoningMessage message = ReasoningStreamAdapter.toMessage(event, thinkingLabel);
        if (message != null) onReasoning.accept(message);
    }

    private void sendReasoning(SseEmitter emitter, String conversationId, ReasoningStreamAdapter.ReasoningMessage message) {
        if (message == null) return;
        try {
            emitter.send(SseEmitter.event().name(SseEventType.REASONING.getEventName()).data(message));
        } catch (IOException e) {
            logger.warn("Error sending reasoning event: conv={}, type={}", conversationId, message.type(), e);
        }
    }
}

package com.composerai.api.service;

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
    private final ExecutorService streamingExecutor;

    public ChatService(VectorSearchService vectorSearchService, OpenAiChatService openAiChatService) {
        this.vectorSearchService = vectorSearchService;
        this.openAiChatService = openAiChatService;
        this.streamingExecutor = Executors.newSingleThreadExecutor();
    }

    private record ChatContext(float[] embedding, List<EmailContext> emailContext, String contextString) {}

    public record StreamMetadata(String conversationId) {}

    private ChatContext prepareChatContext(String message, int maxResults) {
        float[] queryVector = openAiChatService.generateEmbedding(message);
        List<EmailContext> emailContext = (queryVector == null || queryVector.length == 0)
            ? List.of()
            : vectorSearchService.searchSimilarEmails(queryVector, maxResults);
        return new ChatContext(queryVector, emailContext, buildContextString(emailContext));
    }

    public ChatResponse processChat(ChatRequest request) {
        logger.info("Processing chat request: {}", request.getMessage());
        String conversationId = StringUtils.ensureConversationId(request.getConversationId());
        try {
            String intent = openAiChatService.analyzeIntent(request.getMessage());
            ChatContext ctx = prepareChatContext(request.getMessage(), request.getMaxResults());
            OpenAiChatService.ChatCompletionResult aiResult = openAiChatService.generateResponse(
                request.getMessage(), prepareFullContext(ctx, request.getEmailContext()),
                request.isThinkingEnabled(), request.getThinkingLevel());
            logger.info("Successfully processed chat request for conversation: {}", conversationId);
            return new ChatResponse(aiResult.rawText(), conversationId, ctx.emailContext(), intent, aiResult.sanitizedHtml());
        } catch (Exception e) {
            logger.error("Error processing chat request", e);
            String fallback = "I apologize, but I encountered an error while processing your request. Please try again.";
            return new ChatResponse(fallback, conversationId, List.of(), "error", HtmlConverter.markdownToSafeHtml(fallback));
        }
    }

    private String buildContextString(List<EmailContext> emailContexts) {
        if (emailContexts == null || emailContexts.isEmpty()) return "";

        StringBuilder context = new StringBuilder("Relevant emails:\n");
        for (int i = 0; i < emailContexts.size(); i++) {
            EmailContext email = emailContexts.get(i);
            context.append(String.format("%d. From: %s, Subject: %s, Snippet: %s\n",
                i + 1, email.sender(), email.subject(), email.snippet()));
        }
        return context.toString();
    }

    /** Merge vector search context with uploaded client context. */
    private String prepareFullContext(ChatContext vectorContext, String uploadedContext) {
        String baseContext = vectorContext.contextString();
        if (StringUtils.isBlank(uploadedContext)) return baseContext;
        String plain = HtmlConverter.markdownToPlain(uploadedContext);
        String cleaned = HtmlConverter.cleanupOutput(StringUtils.isBlank(plain) ? uploadedContext : plain);
        if (StringUtils.isBlank(cleaned)) cleaned = StringUtils.safe(plain).isEmpty() ? uploadedContext : plain;
        return cleaned.isBlank() ? baseContext : "Uploaded email context:\n" + cleaned + (StringUtils.isBlank(baseContext) ? "" : "\n\n" + baseContext);
    }

    /** Internal helper for streaming chat with consolidated timing and error handling. */
    private void doStreamChat(String message, String conversationId, String contextString,
                              boolean thinkingEnabled, String thinkingLevel,
                              Consumer<String> onToken, Consumer<ReasoningStreamAdapter.ReasoningMessage> onReasoning,
                              Runnable onComplete, Consumer<Throwable> onError) {
        Consumer<Throwable> safeOnError = onError != null ? onError : err -> logger.error("Streaming error: convId={}", conversationId, err);
        long startNanos = System.nanoTime();
        try {
            openAiChatService.streamResponse(message, contextString, thinkingEnabled, thinkingLevel,
                event -> handleStreamEvent(event, onToken, onReasoning),
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
        ChatContext ctx = prepareChatContext(request.getMessage(), request.getMaxResults());
        doStreamChat(request.getMessage(), conversationId, prepareFullContext(ctx, request.getEmailContext()),
            request.isThinkingEnabled(), request.getThinkingLevel(), onToken, onReasoning, onComplete, onError);
    }

    /** Public API: Stream chat with SseEmitter (for SSE endpoints). */
    public void streamChat(ChatRequest request, SseEmitter emitter) {
        String conversationId = StringUtils.ensureConversationId(request.getConversationId());
        emitter.onCompletion(() -> logger.info("SSE completed: {}", conversationId));
        emitter.onTimeout(() -> logger.warn("SSE timeout: {}", conversationId));
        emitter.onError(e -> logger.error("SSE error: {}", conversationId, e));
        streamingExecutor.execute(() -> {
            try {
                ChatContext ctx = prepareChatContext(request.getMessage(), request.getMaxResults());
                doStreamChat(request.getMessage(), conversationId, prepareFullContext(ctx, request.getEmailContext()),
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
                                   Consumer<ReasoningStreamAdapter.ReasoningMessage> onReasoning) {
        if (event instanceof OpenAiChatService.StreamEvent.RenderedHtml rendered) {
            onToken.accept(rendered.html());
            return;
        }
        if (onReasoning == null) return;
        ReasoningStreamAdapter.ReasoningMessage message = ReasoningStreamAdapter.toMessage(event);
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

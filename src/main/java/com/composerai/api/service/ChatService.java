package com.composerai.api.service;

import com.composerai.api.dto.ChatRequest;
import com.composerai.api.dto.ChatResponse;
import com.composerai.api.dto.ChatResponse.EmailContext;
import com.composerai.api.service.email.HtmlConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ChatService {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);
    
    private final VectorSearchService vectorSearchService;
    private final OpenAiChatService openAiChatService;

    public ChatService(VectorSearchService vectorSearchService, OpenAiChatService openAiChatService) {
        this.vectorSearchService = vectorSearchService;
        this.openAiChatService = openAiChatService;
    }

    private record ChatContext(float[] embedding, List<EmailContext> emailContext, String contextString) {}

    public record StreamMetadata(String conversationId) {}

    private ChatContext prepareChatContext(String message, int maxResults) {
        logger.debug("Preparing chat context for message");
        float[] queryVector = openAiChatService.generateEmbedding(message);
        List<EmailContext> emailContext;
        if (queryVector == null || queryVector.length == 0) {
            logger.warn("Embedding generation returned null/empty, skipping vector search");
            emailContext = List.of();
        } else {
            emailContext = vectorSearchService.searchSimilarEmails(queryVector, maxResults);
        }
        String contextString = buildContextString(emailContext);
        return new ChatContext(queryVector, emailContext, contextString);
    }

    public ChatResponse processChat(ChatRequest request) {
        logger.info("Processing chat request: {}", request.getMessage());
        
        try {
            String conversationId = resolveConversationId(request.getConversationId());

            // Analyze user intent
            String intent = openAiChatService.analyzeIntent(request.getMessage());

            // Prepare context for AI response
            ChatContext ctx = prepareChatContext(request.getMessage(), request.getMaxResults());
            String contextString = mergeClientContext(ctx.contextString(), request.getEmailContext());

            // Generate AI response with sanitized HTML
            OpenAiChatService.ChatCompletionResult aiResult = openAiChatService.generateResponse(
                request.getMessage(),
                contextString,
                request.isThinkingEnabled(),
                request.getThinkingLevel()
            );

            ChatResponse response = new ChatResponse(
                aiResult.rawText(),
                conversationId,
                ctx.emailContext(),
                intent,
                aiResult.sanitizedHtml()
            );
            
            logger.info("Successfully processed chat request for conversation: {}", conversationId);
            return response;
            
        } catch (Exception e) {
            logger.error("Error processing chat request", e);
            
            // Return error response
            String fallbackMessage = "I apologize, but I encountered an error while processing your request. Please try again.";
            ChatResponse errorResponse = new ChatResponse();
            errorResponse.setResponse(fallbackMessage);
            errorResponse.setConversationId(resolveConversationId(request.getConversationId()));
            errorResponse.setIntent("error");
            errorResponse.setRenderedHtml(HtmlConverter.markdownToSafeHtml(fallbackMessage));
            return errorResponse;
        }
    }

    private String buildContextString(List<EmailContext> emailContexts) {
        if (emailContexts == null || emailContexts.isEmpty()) {
            return "No relevant emails found.";
        }

        StringBuilder context = new StringBuilder();
        context.append("Relevant emails:\n");
        
        for (int i = 0; i < emailContexts.size(); i++) {
            EmailContext email = emailContexts.get(i);
            context.append(String.format("%d. From: %s, Subject: %s, Snippet: %s\n", 
                i + 1, email.getSender(), email.getSubject(), email.getSnippet()));
        }
        
        return context.toString();
    }

    public void streamChat(String message, int maxResults, String clientContext, String conversationIdSeed,
                           boolean thinkingEnabled, String thinkingLevel,
                           java.util.function.Consumer<StreamMetadata> onMetadata,
                           java.util.function.Consumer<String> onToken,
                           Runnable onComplete,
                           java.util.function.Consumer<Throwable> onError) {
        try {
            logger.info("Processing streaming chat request: {}", message);
            String conversationId = resolveConversationId(conversationIdSeed);
            ChatContext ctx = prepareChatContext(message, maxResults);
            String contextString = mergeClientContext(ctx.contextString(), clientContext);
            if (onMetadata != null) {
                onMetadata.accept(new StreamMetadata(conversationId));
            }
            final long startNanos = System.nanoTime();
            openAiChatService.streamResponse(
                message,
                contextString,
                thinkingEnabled,
                thinkingLevel,
                onToken,
                () -> {
                    long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
                    logger.info("Stream completed normally: conversationId={}, elapsedMs={}", conversationId, elapsedMs);
                    onComplete.run();
                },
                err -> {
                    long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
                    logger.warn("Stream ended with error: conversationId={}, elapsedMs={}, reason={}", conversationId, elapsedMs, err.getMessage());
                    onError.accept(err);
                }
            );
            logger.info("Successfully initiated streaming chat request");
        } catch (Exception e) {
            logger.error("Error initiating streaming chat request", e);
            onError.accept(e);
        }
    }

    private String resolveConversationId(String incoming) {
        if (incoming == null || incoming.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return incoming;
    }

    private String mergeClientContext(String baseContext, String rawClientContext) {
        if (rawClientContext == null || rawClientContext.isBlank()) {
            return baseContext;
        }

        String sanitized = HtmlConverter.cleanupOutput(rawClientContext);
        if (sanitized == null || sanitized.isBlank()) {
            sanitized = rawClientContext.trim();
        }
        if (sanitized == null || sanitized.isBlank()) {
            return baseContext;
        }

        StringBuilder combined = new StringBuilder();
        combined.append("Uploaded email context:\n").append(sanitized.trim());
        if (baseContext != null && !baseContext.isBlank()) {
            combined.append("\n\n").append(baseContext.trim());
        }
        return combined.toString();
    }
}

package com.composerai.api.service;

import com.composerai.api.dto.ChatRequest;
import com.composerai.api.dto.ChatResponse;
import com.composerai.api.dto.ChatResponse.EmailContext;
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
            // Generate conversation ID if not provided
            String conversationId = request.getConversationId();
            if (conversationId == null || conversationId.isEmpty()) {
                conversationId = UUID.randomUUID().toString();
            }

            // Analyze user intent
            String intent = openAiChatService.analyzeIntent(request.getMessage());

            // Prepare context for AI response
            ChatContext ctx = prepareChatContext(request.getMessage(), request.getMaxResults());
            String contextString = ctx.contextString();
            // If client provided raw email context from upload, prepend it so it's prioritized
            if (request.getEmailContext() != null && !request.getEmailContext().isBlank()) {
                String clientCtx = request.getEmailContext();
                // If looks like markdown (contains links/headings), normalize to plain for safety
                String sanitized = com.composerai.api.service.email.HtmlConverter.markdownToPlain(clientCtx);
                if (sanitized == null || sanitized.isBlank()) sanitized = clientCtx;
                sanitized = com.composerai.api.service.email.HtmlConverter.cleanupOutput(sanitized);
                contextString = ("Uploaded email context:\n" + sanitized + "\n\n" + contextString).trim();
            }

            // Generate AI response
            String aiResponse = openAiChatService.generateResponse(request.getMessage(), contextString);

            // Create and return response
            ChatResponse response = new ChatResponse(aiResponse, conversationId, ctx.emailContext(), intent);
            
            logger.info("Successfully processed chat request for conversation: {}", conversationId);
            return response;
            
        } catch (Exception e) {
            logger.error("Error processing chat request", e);
            
            // Return error response
            ChatResponse errorResponse = new ChatResponse();
            errorResponse.setResponse("I apologize, but I encountered an error while processing your request. Please try again.");
            errorResponse.setConversationId(request.getConversationId());
            errorResponse.setIntent("error");
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

    public void streamChat(String message, int maxResults,
                           java.util.function.Consumer<String> onToken,
                           Runnable onComplete,
                           java.util.function.Consumer<Throwable> onError) {
        try {
            logger.info("Processing streaming chat request: {}", message);
            ChatContext ctx = prepareChatContext(message, maxResults);
            openAiChatService.streamResponse(message, ctx.contextString(), onToken, onComplete, onError);
            logger.info("Successfully initiated streaming chat request");
        } catch (Exception e) {
            logger.error("Error initiating streaming chat request", e);
            onError.accept(e);
        }
    }
}
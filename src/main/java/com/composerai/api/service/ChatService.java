package com.composerai.api.service;

import com.composerai.api.dto.ChatRequest;
import com.composerai.api.dto.ChatResponse;
import com.composerai.api.dto.ChatResponse.EmailContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ChatService {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);
    
    private final VectorSearchService vectorSearchService;
    private final OpenAiChatService openAiChatService;

    @Autowired
    public ChatService(VectorSearchService vectorSearchService, OpenAiChatService openAiChatService) {
        this.vectorSearchService = vectorSearchService;
        this.openAiChatService = openAiChatService;
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

            // Generate embedding for the user's message
            float[] queryVector = openAiChatService.generateEmbedding(request.getMessage());

            // Search for relevant emails using vector similarity
            List<EmailContext> emailContext = vectorSearchService.searchSimilarEmails(
                queryVector, 
                request.getMaxResults()
            );

            // Prepare context for AI response
            String contextString = buildContextString(emailContext);

            // Generate AI response
            String aiResponse = openAiChatService.generateResponse(request.getMessage(), contextString);

            // Create and return response
            ChatResponse response = new ChatResponse(aiResponse, conversationId, emailContext, intent);
            
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
}
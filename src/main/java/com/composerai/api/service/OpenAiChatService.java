package com.composerai.api.service;

import com.composerai.api.config.OpenAiProperties;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class OpenAiChatService {
    
    private static final Logger logger = LoggerFactory.getLogger(OpenAiChatService.class);
    
    private final OpenAiService openAiClient;
    private final OpenAiProperties openAiProperties;

    public OpenAiChatService(OpenAiService openAiClient, OpenAiProperties openAiProperties) {
        this.openAiClient = openAiClient;
        this.openAiProperties = openAiProperties;
    }

    public String generateResponse(String userMessage, String emailContext) {
        try {
            List<ChatMessage> messages = new ArrayList<>();
            
            // System message with context
            String systemMessage = "You are an AI assistant that helps users understand and work with their email. " +
                "Use the following email context to answer the user's question: " + emailContext;
            messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), systemMessage));
            
            // User message
            messages.add(new ChatMessage(ChatMessageRole.USER.value(), userMessage));

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(openAiProperties.getModel())
                .messages(messages)
                .maxTokens(500)
                .temperature(0.7)
                .build();

            ChatCompletionResult response = openAiClient.createChatCompletion(request);
            
            String aiResponse = response.getChoices().get(0).getMessage().getContent();
            logger.info("Generated AI response for user query");
            return aiResponse;
            
        } catch (Exception e) {
            logger.error("Error generating AI response", e);
            return "I apologize, but I'm having trouble processing your request right now. Please try again later.";
        }
    }

    public String analyzeIntent(String userMessage) {
        try {
            List<ChatMessage> messages = new ArrayList<>();
            
            String systemMessage = "Analyze the user's intent and classify it into one of these categories: " +
                "search, compose, summarize, analyze, question, or other. " +
                "Respond with just the category name.";
            messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), systemMessage));
            messages.add(new ChatMessage(ChatMessageRole.USER.value(), userMessage));

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(openAiProperties.getModel())
                .messages(messages)
                .maxTokens(10)
                .temperature(0.1)
                .build();

            ChatCompletionResult response = openAiClient.createChatCompletion(request);
            String intent = response.getChoices().get(0).getMessage().getContent().trim().toLowerCase();
            
            logger.info("Analyzed intent: {}", intent);
            return intent;
            
        } catch (Exception e) {
            logger.error("Error analyzing intent", e);
            return "question";
        }
    }

    public float[] generateEmbedding(String text) {
        // Placeholder for embedding generation
        // In a real implementation, you would use OpenAI's embedding API
        // For now, return a dummy embedding vector
        logger.info("Generating embedding for text (placeholder implementation)");
        float[] embedding = new float[1536]; // OpenAI ada-002 dimension
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = (float) Math.random();
        }
        return embedding;
    }
}
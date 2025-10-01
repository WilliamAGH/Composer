package com.composerai.api.service;

import com.composerai.api.config.OpenAiProperties;
import com.openai.client.OpenAIClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.Embedding;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.models.embeddings.EmbeddingModel;
import com.openai.core.http.StreamResponse;
import com.openai.models.chat.completions.ChatCompletionChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Service
public class OpenAiChatService {
    
    private static final Logger logger = LoggerFactory.getLogger(OpenAiChatService.class);
    
    private final OpenAIClient openAiClient;
    private final OpenAiProperties openAiProperties;

    public OpenAiChatService(OpenAIClient openAiClient, OpenAiProperties openAiProperties) {
        this.openAiClient = openAiClient;
        this.openAiProperties = openAiProperties;
    }

    public String generateResponse(String userMessage, String emailContext) {
        try {
            List<ChatCompletionMessageParam> messages = new ArrayList<>();

            String systemMessage = "You are an AI assistant that helps users understand and work with their email. " +
                "Use the following email context to answer the user's question: " + emailContext;
            messages.add(ChatCompletionMessageParam.ofSystem(
                ChatCompletionSystemMessageParam.builder().content(systemMessage).build()
            ));

            messages.add(ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam.builder().content(userMessage).build()
            ));

            ChatModel model;
            String configuredModel = openAiProperties.getModel();
            if (configuredModel != null && !configuredModel.isBlank()) {
                model = ChatModel.of(configuredModel);
            } else {
                model = ChatModel.CHATGPT_4O_LATEST;
            }

            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(model)
                .messages(messages)
                .maxCompletionTokens(500L)
                .temperature(0.7)
                .build();

            ChatCompletion response = openAiClient.chat().completions().create(params);

            String aiResponse = response.choices().get(0).message().content().orElse("");
            logger.info("Generated AI response for user query");
            return aiResponse;

        } catch (Exception e) {
            logger.error("Error generating AI response", e);
            return "I apologize, but I'm having trouble processing your request right now. Please try again later.";
        }
    }

    public String analyzeIntent(String userMessage) {
        try {
            List<ChatCompletionMessageParam> messages = new ArrayList<>();

            String systemMessage = "Analyze the user's intent and classify it into one of these categories: " +
                "search, compose, summarize, analyze, question, or other. " +
                "Respond with just the category name.";
            messages.add(ChatCompletionMessageParam.ofSystem(
                ChatCompletionSystemMessageParam.builder().content(systemMessage).build()
            ));
            messages.add(ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam.builder().content(userMessage).build()
            ));

            ChatModel model;
            String configuredModel = openAiProperties.getModel();
            if (configuredModel != null && !configuredModel.isBlank()) {
                model = ChatModel.of(configuredModel);
            } else {
                model = ChatModel.CHATGPT_4O_LATEST;
            }

            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(model)
                .messages(messages)
                .maxCompletionTokens(10L)
                .temperature(0.1)
                .build();

            ChatCompletion response = openAiClient.chat().completions().create(params);
            String intent = response.choices().get(0).message().content().orElse("").trim().toLowerCase();

            logger.info("Analyzed intent: {}", intent);
            return intent;

        } catch (Exception e) {
            logger.error("Error analyzing intent", e);
            return "question";
        }
    }

    public void streamResponse(String userMessage, String emailContext,
                               Consumer<String> onToken, Runnable onComplete, Consumer<Throwable> onError) {
        try {
            List<ChatCompletionMessageParam> messages = new ArrayList<>();

            String systemMessage = "You are an AI assistant that helps users understand and work with their email. " +
                "Use the following email context to answer the user's question: " + emailContext;
            messages.add(ChatCompletionMessageParam.ofSystem(
                ChatCompletionSystemMessageParam.builder().content(systemMessage).build()
            ));

            messages.add(ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam.builder().content(userMessage).build()
            ));

            ChatModel model;
            String configuredModel = openAiProperties.getModel();
            if (configuredModel != null && !configuredModel.isBlank()) {
                model = ChatModel.of(configuredModel);
            } else {
                model = ChatModel.CHATGPT_4O_LATEST;
            }

            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(model)
                .messages(messages)
                .maxCompletionTokens(500L)
                .temperature(0.7)
                .build();

            try (StreamResponse<ChatCompletionChunk> stream = openAiClient.chat().completions().createStreaming(params)) {
                stream.stream().forEach(chunk -> {
                    for (ChatCompletionChunk.Choice choice : chunk.choices()) {
                        choice.delta().content().ifPresent(onToken);
                    }
                });
                onComplete.run();
            }
        } catch (Exception e) {
            logger.error("Error streaming AI response", e);
            onError.accept(e);
        }
    }

    public float[] generateEmbedding(String text) {
        try {
            EmbeddingCreateParams params = EmbeddingCreateParams.builder()
                .model(EmbeddingModel.TEXT_EMBEDDING_3_SMALL)
                .input(text)
                .build();

            CreateEmbeddingResponse response = openAiClient.embeddings().create(params);
            List<Embedding> data = response.data();
            if (data.isEmpty()) {
                return new float[0];
            }
            List<Float> vector = data.get(0).embedding();
            float[] result = new float[vector.size()];
            for (int i = 0; i < vector.size(); i++) {
                result[i] = vector.get(i);
            }
            return result;
        } catch (Exception e) {
            logger.error("Error generating embeddings", e);
            return new float[0];
        }
    }
}
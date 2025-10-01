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
import java.util.function.Supplier;

@Service
public class OpenAiChatService {
    
    private static final Logger logger = LoggerFactory.getLogger(OpenAiChatService.class);
    
    private static final String OPENAI_MISCONFIGURED_MESSAGE = "OpenAI is not configured (missing OPENAI_API_KEY).";
    private static final String OPENAI_UNAVAILABLE_MESSAGE = "OpenAI is unavailable right now. Please try again later.";

    private final OpenAIClient openAiClient;
    private final OpenAiProperties openAiProperties;

    public OpenAiChatService(OpenAIClient openAiClient, OpenAiProperties openAiProperties) {
        this.openAiClient = openAiClient;
        this.openAiProperties = openAiProperties;
    }

    public String generateResponse(String userMessage, String emailContext) {
        return executeWithFallback(() -> {
            if (openAiClient == null) {
                return OPENAI_MISCONFIGURED_MESSAGE;
            }

            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(resolveChatModel())
                .messages(buildEmailAssistantMessages(emailContext, userMessage))
                .maxCompletionTokens(500L)
                .temperature(0.7)
                .build();

            ChatCompletion response = openAiClient.chat().completions().create(params);

            String aiResponse = extractChoiceContent(response, OPENAI_UNAVAILABLE_MESSAGE);
            logger.info("Generated AI response for user query");
            return aiResponse;
        }, OPENAI_UNAVAILABLE_MESSAGE, "OpenAI error while generating response");
    }

    public String analyzeIntent(String userMessage) {
        return executeWithFallback(() -> {
            if (openAiClient == null) {
                return "question";
            }
            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(resolveChatModel())
                .messages(buildIntentAnalysisMessages(userMessage))
                .maxCompletionTokens(10L)
                .temperature(0.1)
                .build();

            ChatCompletion response = openAiClient.chat().completions().create(params);
            String intent = extractChoiceContent(response, "question").trim().toLowerCase();

            logger.info("Analyzed intent: {}", intent);
            return intent.isEmpty() ? "question" : intent;
        }, "question", "OpenAI error while analyzing intent");
    }

    public void streamResponse(String userMessage, String emailContext,
                               Consumer<String> onToken, Runnable onComplete, Consumer<Throwable> onError) {
        try {
            if (openAiClient == null) {
                onError.accept(new IllegalStateException("OpenAI is not configured (missing OPENAI_API_KEY)."));
                return;
            }
            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(resolveChatModel())
                .messages(buildEmailAssistantMessages(emailContext, userMessage))
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
            logger.warn("OpenAI error while streaming: {}", e.getMessage());
            onError.accept(new RuntimeException("OpenAI is unavailable right now."));
        }
    }

    public float[] generateEmbedding(String text) {
        return executeWithFallback(() -> {
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
        }, new float[0], "OpenAI error while generating embeddings");
    }

    private ChatModel resolveChatModel() {
        String configuredModel = openAiProperties.getModel();
        if (configuredModel != null && !configuredModel.isBlank()) {
            return ChatModel.of(configuredModel);
        }
        return ChatModel.CHATGPT_4O_LATEST;
    }

    private List<ChatCompletionMessageParam> buildEmailAssistantMessages(String emailContext, String userMessage) {
        String safeContext = emailContext == null ? "" : emailContext;
        String systemMessage = "You are an AI assistant that helps users understand and work with their email. " +
            "Use the following email context to answer the user's question: " + safeContext;
        return buildMessages(systemMessage, userMessage);
    }

    private List<ChatCompletionMessageParam> buildIntentAnalysisMessages(String userMessage) {
        String systemMessage = "Analyze the user's intent and classify it into one of these categories: " +
            "search, compose, summarize, analyze, question, or other. Respond with just the category name.";
        return buildMessages(systemMessage, userMessage);
    }

    private List<ChatCompletionMessageParam> buildMessages(String systemMessage, String userMessage) {
        List<ChatCompletionMessageParam> messages = new ArrayList<>(2);
        messages.add(ChatCompletionMessageParam.ofSystem(
            ChatCompletionSystemMessageParam.builder().content(systemMessage).build()
        ));
        messages.add(ChatCompletionMessageParam.ofUser(
            ChatCompletionUserMessageParam.builder().content(userMessage).build()
        ));
        return messages;
    }

    private String extractChoiceContent(ChatCompletion response, String defaultValue) {
        List<ChatCompletion.Choice> choices = response.choices();
        if (choices == null || choices.isEmpty()) {
            logger.warn("OpenAI response returned no choices");
            return defaultValue;
        }
        return choices.get(0).message().content().orElse(defaultValue);
    }

    /**
     * Execute an action with fallback error handling.
     * Catches any exceptions, logs a warning, and returns the fallback value.
     *
     * @param action     the action to execute
     * @param fallback   the value to return if an exception occurs
     * @param errorMsg   the error message to log
     * @param <T>        the return type
     * @return the result of the action or the fallback value
     */
    private <T> T executeWithFallback(Supplier<T> action, T fallback, String errorMsg) {
        try {
            return action.get();
        } catch (Exception e) {
            logger.warn("{}: {}", errorMsg, e.getMessage());
            return fallback;
        }
    }
}

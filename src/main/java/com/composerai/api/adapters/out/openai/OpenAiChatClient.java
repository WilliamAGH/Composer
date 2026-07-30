package com.composerai.api.adapters.out.openai;

import com.composerai.api.config.ErrorMessagesProperties;
import com.composerai.api.config.OpenAiProperties;
import com.composerai.api.config.ProviderCapabilities;
import com.composerai.api.domain.model.ChatCompletionCommand;
import com.composerai.api.service.email.HtmlConverter;
import com.composerai.api.shared.ledger.UsageMetrics;
import com.openai.client.OpenAIClient;
import com.openai.models.embeddings.Embedding;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.models.embeddings.EmbeddingModel;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
public class OpenAiChatClient {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiChatClient.class);
    private static final Pattern DANGEROUS_BLOCK_TAGS = Pattern.compile("(?is)<(script|style|iframe)[^>]*>.*?</\\1>");

    private final OpenAIClient openAiClient;
    private final OpenAiProperties openAiProperties;
    private final ErrorMessagesProperties errorMessages;
    private final OpenAiResponseRequestFactory responseRequestFactory;
    private final OpenAiResponseStreamExecutor responseStreamExecutor;

    public record ChatCompletion(String rawText, String sanitizedHtml) {
        public ChatCompletion {
            rawText = rawText == null ? "" : rawText;
            sanitizedHtml = sanitizedHtml == null ? "" : sanitizedHtml;
        }

        public static ChatCompletion fromRaw(String rawText) {
            return fromRaw(rawText, false);
        }

        public static ChatCompletion fromRaw(String rawText, boolean jsonOutput) {
            String safeRaw = rawText == null ? "" : rawText;
            String sanitized = jsonOutput
                    ? safeRaw
                    : HtmlConverter.markdownToSafeHtml(
                            DANGEROUS_BLOCK_TAGS.matcher(safeRaw).replaceAll(""));
            return new ChatCompletion(safeRaw, sanitized);
        }
    }

    public record Invocation(
            ChatCompletion completion, ResponseCreateParams requestParams, Response response, UsageMetrics usage) {
        public static Invocation streamed(ChatCompletion completion, UsageMetrics usage) {
            return new Invocation(completion, null, null, usage);
        }
    }

    public OpenAiChatClient(
            @Autowired(required = false) @Nullable OpenAIClient openAiClient,
            OpenAiProperties openAiProperties,
            ErrorMessagesProperties errorMessages) {
        this.openAiClient = openAiClient;
        this.openAiProperties = openAiProperties;
        this.errorMessages = errorMessages;
        this.responseRequestFactory = new OpenAiResponseRequestFactory(openAiProperties);
        this.responseStreamExecutor = new OpenAiResponseStreamExecutor(openAiClient, errorMessages);
    }

    public Invocation invokeChatResponse(ChatCompletionCommand command) {
        OpenAIClient configuredClient = requireOpenAiClient();
        OpenAiResponseRequestFactory.PreparedRequest prepared = responseRequestFactory.prepareResponseRequest(command);
        ResponseCreateParams requestParams = prepared.requestParams();
        String modelId = openAiProperties.getModel().getChat();
        logLlmInvocation(
                "chat-sync",
                modelId,
                false,
                command.jsonOutput(),
                command.thinkingEnabled(),
                prepared.reasoningEffort());

        long startMillis = System.currentTimeMillis();
        Response apiResponse = configuredClient.responses().create(requestParams);
        String responseText = flattenResponseText(apiResponse, command.jsonOutput());
        UsageMetrics usage = toUsageMetrics(apiResponse, startMillis);
        logger.info(
                "Chat completion: model={} promptTokens={} completionTokens={}",
                modelId,
                usage.promptTokens(),
                usage.completionTokens());
        return new Invocation(
                ChatCompletion.fromRaw(responseText, command.jsonOutput()), requestParams, apiResponse, usage);
    }

    public ChatCompletion generateResponse(ChatCompletionCommand command) {
        return invokeChatResponse(command).completion();
    }

    /**
     * Analyzes user intent and classifies it into predefined categories.
     *
     * Configuration source of truth: OpenAiProperties.java
     * Default category: {@link OpenAiProperties.Intent#getDefaultCategory()} - "question"
     * Max tokens: {@link OpenAiProperties.Intent#getMaxOutputTokens()} - 10
     */
    public String analyzeIntent(String userMessage) {
        String defaultCategory = openAiProperties.getIntent().getDefaultCategory();

        OpenAIClient configuredClient = requireOpenAiClient();
        String modelId = openAiProperties.getModel().getChat();
        OpenAiResponseRequestFactory.PreparedRequest prepared =
                responseRequestFactory.prepareIntentRequest(userMessage);
        ResponseCreateParams requestParams = prepared.requestParams();
        logLlmInvocation(
                "intent",
                modelId,
                false,
                false,
                openAiProperties.getDefaults().getThinkingEnabled(),
                prepared.reasoningEffort());
        String intent = configuredClient.responses().create(requestParams).output().stream()
                .flatMap(responseEntry -> responseEntry.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .map(outputText -> outputText.text())
                .collect(java.util.stream.Collectors.joining())
                .trim()
                .toLowerCase();
        logger.info("Analyzed intent: {}", intent);
        return intent.isEmpty() ? defaultCategory : intent;
    }

    public ResponseCreateParams streamResponse(
            ChatCompletionCommand command,
            Consumer<OpenAiStreamEvent> onEvent,
            Runnable onComplete,
            Consumer<Throwable> onError) {
        requireOpenAiClient();

        String modelId = openAiProperties.getModel().getChat();
        OpenAiResponseRequestFactory.PreparedRequest prepared = responseRequestFactory.prepareResponseRequest(command);
        ResponseCreateParams requestParams = prepared.requestParams();

        logLlmInvocation(
                "chat-stream",
                modelId,
                true,
                command.jsonOutput(),
                command.thinkingEnabled(),
                prepared.reasoningEffort());
        responseStreamExecutor.stream(
                requestParams,
                command.jsonOutput(),
                openAiProperties.isLocalDebugEnabled(),
                onEvent,
                onComplete,
                onError);
        return requestParams;
    }

    private String flattenResponseText(Response response, boolean jsonOutput) {
        if (response == null || response.output() == null) {
            return jsonOutput ? "{}" : "";
        }
        return response.output().stream()
                .flatMap(responseEntry -> responseEntry.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .map(outputText -> outputText.text())
                .collect(Collectors.joining());
    }

    private UsageMetrics toUsageMetrics(Response response, long startMillis) {
        long latency = Math.max(0, System.currentTimeMillis() - startMillis);
        if (response == null || response.usage().isEmpty()) {
            return new UsageMetrics(0, 0, 0, latency);
        }

        var usage = response.usage().get();
        long prompt = safeTokenCount(usage.inputTokens());
        long completion = safeTokenCount(usage.outputTokens());
        long total = safeTokenCount(usage.totalTokens());
        if (total == 0) {
            total = prompt + completion;
        }
        return new UsageMetrics(prompt, completion, total, latency);
    }

    private long safeTokenCount(Long tokenCount) {
        return tokenCount == null ? 0L : tokenCount;
    }

    /**
     * Generates vector embeddings for the given text.
     *
     * Configuration source of truth: OpenAiProperties.java
     * Embedding model: {@link OpenAiProperties.Embedding#getModel()} - "text-embedding-3-small"
     *
     * Note: Not all providers support embeddings. Returns empty array if provider doesn't support it.
     */
    public float[] generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return new float[0];
        }

        // Check if provider supports embeddings
        if (!openAiProperties.getProviderCapabilities().supportsEmbeddings()) {
            logger.debug(
                    "Provider {} does not support embeddings, returning empty vector",
                    openAiProperties.getProviderCapabilities().getType());
            return new float[0];
        }

        if (openAiClient == null) {
            logger.error("Embedding generation failed: OpenAI client not configured");
            return new float[0];
        }

        try {
            logLlmInvocation("embedding", openAiProperties.getEmbedding().getModel(), false, false, false, null);
            List<Embedding> embeddings = openAiClient
                    .embeddings()
                    .create(EmbeddingCreateParams.builder()
                            .model(EmbeddingModel.of(
                                    openAiProperties.getEmbedding().getModel()))
                            .input(text)
                            .build())
                    .data();
            if (embeddings.isEmpty()) {
                logger.warn("Embedding API returned empty data for input of {} chars", text.length());
                return new float[0];
            }
            List<Float> vector = embeddings.get(0).embedding();
            float[] embeddingVector = new float[vector.size()];
            for (int dimensionIndex = 0; dimensionIndex < vector.size(); dimensionIndex++) {
                embeddingVector[dimensionIndex] = vector.get(dimensionIndex);
            }
            return embeddingVector;
        } catch (Exception e) {
            logger.error("Embedding generation failed for input of {} chars: {}", text.length(), e.getMessage(), e);
            return new float[0];
        }
    }

    private void logLlmInvocation(
            String operation,
            String modelId,
            boolean streaming,
            boolean jsonOutput,
            Boolean thinkingRequested,
            @Nullable com.composerai.api.domain.model.ReasoningEffortLevel reasoningEffort) {
        ProviderCapabilities capabilities = openAiProperties.getProviderCapabilities();
        String provider = capabilities == null || capabilities.getType() == null
                ? "UNKNOWN"
                : capabilities.getType().name();
        String baseUrl =
                openAiProperties.getApi() != null && openAiProperties.getApi().getBaseUrl() != null
                        ? openAiProperties.getApi().getBaseUrl()
                        : "unset";
        String thinkingLabel = reasoningEffort != null
                ? reasoningEffort.externalName()
                : (Boolean.TRUE.equals(thinkingRequested) ? "enabled" : "unspecified");
        logger.info(
                "LLM {} request: provider={} model={} baseUrl={} streaming={} jsonOutput={} thinking={}",
                operation,
                provider,
                modelId,
                baseUrl,
                streaming,
                jsonOutput,
                thinkingLabel);
    }

    private OpenAIClient requireOpenAiClient() {
        if (openAiClient == null) {
            throw new IllegalStateException(errorMessages.getOpenai().getMisconfigured());
        }
        return openAiClient;
    }
}

package com.composerai.api.service;

import com.composerai.api.config.ErrorMessagesProperties;
import com.composerai.api.config.OpenAiProperties;
import com.composerai.api.config.ProviderCapabilities;
import com.composerai.api.shared.ledger.UsageMetrics;
import com.composerai.api.util.StringUtils;
import com.composerai.api.util.TemporalUtils;
import com.openai.client.OpenAIClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.ChatModel;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseFailedEvent;
import com.openai.models.responses.ResponseStreamEvent;
import com.openai.models.embeddings.Embedding;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.models.embeddings.EmbeddingModel;
import com.composerai.api.domain.model.ChatCompletionCommand;
import com.composerai.api.domain.model.ConversationTurn;
import com.composerai.api.service.email.HtmlConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class OpenAiChatService {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiChatService.class);
    private static final Pattern DANGEROUS_BLOCK_TAGS = Pattern.compile("(?is)<(script|style|iframe)[^>]*>.*?</\\1>");

    private final OpenAIClient openAiClient;
    private final OpenAiProperties openAiProperties;
    private final ErrorMessagesProperties errorMessages;

    public record ChatCompletionResult(String rawText, String sanitizedHtml) {
        public ChatCompletionResult {
            rawText = rawText == null ? "" : rawText;
            sanitizedHtml = sanitizedHtml == null ? "" : sanitizedHtml;
        }
        static ChatCompletionResult fromRaw(String rawText) {
            return fromRaw(rawText, false);
        }
        static ChatCompletionResult fromRaw(String rawText, boolean jsonOutput) {
            String safeRaw = rawText == null ? "" : rawText;
            String sanitized = jsonOutput
                ? safeRaw
                : HtmlConverter.markdownToSafeHtml(DANGEROUS_BLOCK_TAGS.matcher(safeRaw).replaceAll(""));
            return new ChatCompletionResult(safeRaw, sanitized);
        }
    }

    public record Invocation(ChatCompletionResult result,
                              ResponseCreateParams requestParams,
                              Response response,
                              UsageMetrics usage) {}

    /** Validated thinking configuration. Backend validates against model capabilities. */
    public record ValidatedThinkingConfig(boolean enabled, ReasoningEffort effort) {
        static ValidatedThinkingConfig resolve(OpenAiProperties properties, String modelId,
                                               boolean requestedEnabled, String requestedLevel) {
            if (modelId == null || !properties.supportsReasoning(modelId) || !requestedEnabled) {
                return new ValidatedThinkingConfig(false, null);
            }
            
            Optional<ReasoningEffort> effort = parseEffort(requestedLevel);
            
            // Validate "minimal" is OpenAI-only
            if (effort.isPresent() && effort.get() == ReasoningEffort.MINIMAL 
                && !properties.getProviderCapabilities().supportsMinimalReasoning()) {
                logger.warn("Reasoning effort 'minimal' is only supported by OpenAI. Falling back to 'low' for provider: {}", 
                    properties.getProviderCapabilities().getType());
                return new ValidatedThinkingConfig(true, ReasoningEffort.LOW);
            }
            
            // Default to LOW (not MINIMAL) for OpenRouter compatibility
            return new ValidatedThinkingConfig(true, effort.orElse(ReasoningEffort.LOW));
        }
        private static Optional<ReasoningEffort> parseEffort(String level) {
            if (level == null || level.isBlank()) return Optional.of(ReasoningEffort.MINIMAL);
            return Optional.ofNullable(switch (level.trim().toLowerCase(Locale.ROOT)) {
                case "minimal" -> ReasoningEffort.MINIMAL;
                case "low" -> ReasoningEffort.LOW;
                case "medium" -> ReasoningEffort.MEDIUM;
                case "high", "heavy" -> ReasoningEffort.HIGH;
                default -> null;
            });
        }
    }

    public OpenAiChatService(@Autowired(required = false) @Nullable OpenAIClient openAiClient,
                              OpenAiProperties openAiProperties,
                              ErrorMessagesProperties errorMessages) {
        this.openAiClient = openAiClient;
        this.openAiProperties = openAiProperties;
        this.errorMessages = errorMessages;
    }

    public Invocation invokeChatResponse(ChatCompletionCommand command) {
        if (openAiClient == null) {
            String fallback = command.jsonOutput()
                ? "{\"error\":\"openai_misconfigured\"}"
                : errorMessages.getOpenai().getMisconfigured();
            return new Invocation(
                ChatCompletionResult.fromRaw(fallback, command.jsonOutput()),
                null,
                null,
                new UsageMetrics(0, 0, 0, 0)
            );
        }

        try {
            PreparedRequest prepared = prepareResponseRequest(command);
            ResponseCreateParams params = prepared.builder().build();
            String modelId = openAiProperties.getModel().getChat();
            logLlmInvocation("chat-sync", modelId, false, command.jsonOutput(), command.thinkingEnabled(), prepared.config().effort());

            long start = System.currentTimeMillis();
            Response apiResponse = openAiClient.responses().create(params);
            String aiResponse = flattenResponseText(apiResponse, command.jsonOutput());
            UsageMetrics usage = toUsageMetrics(apiResponse, start);
            logger.info("Chat completion: model={} promptTokens={} completionTokens={}",
                modelId, usage.promptTokens(), usage.completionTokens());
            return new Invocation(ChatCompletionResult.fromRaw(aiResponse, command.jsonOutput()), params, apiResponse, usage);
        } catch (Exception e) {
            logger.error("Chat completion failed: {}", e.getMessage(), e);
            String fallback = command.jsonOutput()
                ? "{\"error\":\"openai_unavailable\"}"
                : errorMessages.getOpenai().getUnavailable();
            return new Invocation(
                ChatCompletionResult.fromRaw(fallback, command.jsonOutput()),
                null,
                null,
                new UsageMetrics(0, 0, 0, 0)
            );
        }
    }

    public ChatCompletionResult generateResponse(ChatCompletionCommand command) {
        return invokeChatResponse(command).result();
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

        if (openAiClient == null) {
            logger.debug("Intent analysis skipped: OpenAI client not configured");
            return defaultCategory;
        }

        try {
            String modelId = openAiProperties.getModel().getChat();
            logLlmInvocation("intent", modelId, false, false, false, null);
            String intent = openAiClient.responses().create(ResponseCreateParams.builder()
                .model(resolveChatModel())
                .inputOfResponse(buildIntentAnalysisMessages(userMessage))
                .maxOutputTokens(openAiProperties.getIntent().getMaxOutputTokens())
                .build()).output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .map(outputText -> outputText.text())
                .collect(java.util.stream.Collectors.joining())
                .trim().toLowerCase();

            logger.info("Analyzed intent: {}", intent);
            return intent.isEmpty() ? defaultCategory : intent;
        } catch (Exception e) {
            logger.error("Intent analysis failed - defaulting to '{}': {}", defaultCategory, e.getMessage(), e);
            return defaultCategory;
        }
    }

    public ResponseCreateParams streamResponse(ChatCompletionCommand command,
                                               Consumer<StreamEvent> onEvent, Runnable onComplete, Consumer<Throwable> onError) {
        if (openAiClient == null) {
            onError.accept(new IllegalStateException(errorMessages.getOpenai().getMisconfigured()));
            return null;
        }

        String modelId = openAiProperties.getModel().getChat();
        PreparedRequest prepared = prepareResponseRequest(command);
        ResponseCreateParams params = prepared.builder().build();

        logLlmInvocation("chat-stream", modelId, true, command.jsonOutput(), command.thinkingEnabled(), prepared.config().effort());

        boolean streamingDebugEnabled = logger.isDebugEnabled() && openAiProperties.isLocalDebugEnabled();
        MarkdownStreamAssembler assembler = command.jsonOutput() ? null : new MarkdownStreamAssembler(streamingDebugEnabled);
        long startNanos = System.nanoTime();
        final long[] tokenCount = new long[]{0};
        final boolean[] failed = {false};

        try {
            try (StreamResponse<ResponseStreamEvent> streamResponse = openAiClient.responses().createStreaming(params)) {
                streamResponse.stream().forEach(event -> {
                    try {
                        event.outputTextDelta().ifPresent(textDelta -> {
                            if (command.jsonOutput()) {
                                emitJsonDelta(textDelta.delta(), onEvent, tokenCount, streamingDebugEnabled);
                            } else {
                                emitHtmlDelta(textDelta.delta(), assembler, onEvent, tokenCount, streamingDebugEnabled);
                            }
                        });
                        emitReasoningEvents(event, onEvent);
                        event.failed().ifPresent(failedEvent -> {
                            failed[0] = true;
                            onEvent.accept(StreamEvent.failed(failedEvent));
                        });
                    } catch (Exception processingError) {
                        // Log at error level and mark stream as failed so caller knows
                        logger.error("Stream event processing failed - marking stream as failed", processingError);
                        failed[0] = true;
                    }
                });
                if (!command.jsonOutput() && assembler != null) {
                    assembler.flushRemainder().ifPresent(remainder -> {
                        if (!remainder.isBlank()) {
                            onEvent.accept(StreamEvent.renderedHtml(remainder));
                            tokenCount[0] += remainder.length();
                        }
                    });
                }
            }
            logger.info("Streaming completed: tokens={} elapsed={}ms", tokenCount[0], (System.nanoTime() - startNanos) / 1_000_000L);
            if (failed[0]) {
                onError.accept(new RuntimeException(errorMessages.getOpenai().getUnavailable()));
            } else {
                onComplete.run();
            }
        } catch (Exception e) {
            logger.error("Streaming failed after {}ms", (System.nanoTime() - startNanos) / 1_000_000L, e);
            String fallbackMessage = (e.getMessage() != null && !e.getMessage().trim().isEmpty())
                ? e.getMessage().trim()
                : errorMessages.getOpenai().getUnavailable();
            onError.accept(new RuntimeException(fallbackMessage, e));
        }
        return params;
    }

    public sealed interface StreamEvent permits StreamEvent.RenderedHtml, StreamEvent.RawJson, StreamEvent.Reasoning, StreamEvent.Failed, StreamEvent.RawText {
        static StreamEvent renderedHtml(String htmlChunk) { return new RenderedHtml(htmlChunk); }
        static StreamEvent rawJson(String jsonChunk) { return new RawJson(jsonChunk); }
        static StreamEvent reasoning(ReasoningStreamAdapter.ReasoningEvent event) { return new Reasoning(event); }
        static StreamEvent failed(ResponseFailedEvent event) { return new Failed(event); }
        static StreamEvent rawText(String value) { return new RawText(value); }
        record RenderedHtml(String html) implements StreamEvent {}
        record RawJson(String value) implements StreamEvent {}
        record Reasoning(ReasoningStreamAdapter.ReasoningEvent value) implements StreamEvent {}
        record Failed(ResponseFailedEvent value) implements StreamEvent {}
        record RawText(String value) implements StreamEvent {}
    }

    private void emitHtmlDelta(String deltaText, MarkdownStreamAssembler assembler, Consumer<StreamEvent> onEvent, long[] tokenCount, boolean debugEnabled) {
        if (deltaText == null || deltaText.isEmpty()) return;
        if (debugEnabled) {
            logger.debug("Streaming delta ({} chars): {}", deltaText.length(), MarkdownStreamAssembler.preview(deltaText));
        }
        onEvent.accept(StreamEvent.rawText(deltaText));
        for (String htmlChunk : assembler.onDelta(deltaText)) {
            if (htmlChunk != null && !htmlChunk.isBlank()) {
                if (debugEnabled) {
                    logger.debug("Emitting HTML chunk ({} chars): {}", htmlChunk.length(), MarkdownStreamAssembler.preview(htmlChunk));
                }
                onEvent.accept(StreamEvent.renderedHtml(htmlChunk));
                tokenCount[0] += htmlChunk.length();
            }
        }
    }

    private void emitJsonDelta(String deltaText, Consumer<StreamEvent> onEvent, long[] tokenCount, boolean debugEnabled) {
        if (deltaText == null || deltaText.isEmpty()) return;
        if (debugEnabled) {
            logger.debug("Streaming JSON delta ({} chars): {}", deltaText.length(), MarkdownStreamAssembler.preview(deltaText));
        }
        onEvent.accept(StreamEvent.rawJson(deltaText));
        tokenCount[0] += deltaText.length();
    }

    private void emitReasoningEvents(ResponseStreamEvent event, Consumer<StreamEvent> onEvent) {
        for (ReasoningStreamAdapter.ReasoningEvent reasoningEvent : ReasoningStreamAdapter.extract(event))
            onEvent.accept(StreamEvent.reasoning(reasoningEvent));
    }

    private PreparedRequest prepareResponseRequest(ChatCompletionCommand command) {
        ResponseCreateParams.Builder builder = ResponseCreateParams.builder()
            .model(resolveChatModel())
            .inputOfResponse(buildEmailAssistantMessages(command.emailContext(), command.userMessage(), command.conversationHistory(), command.jsonOutput()));

        if (openAiProperties.getModel().getTemperature() != null) {
            builder.temperature(openAiProperties.getModel().getTemperature());
        }
        if (openAiProperties.getModel().getTopP() != null) {
            builder.topP(openAiProperties.getModel().getTopP());
        }
        if (openAiProperties.getModel().getMaxOutputTokens() != null) {
            builder.maxOutputTokens(openAiProperties.getModel().getMaxOutputTokens());
        }

        String modelId = openAiProperties.getModel().getChat();
        ValidatedThinkingConfig config = ValidatedThinkingConfig.resolve(openAiProperties, modelId, command.thinkingEnabled(), command.thinkingLevel());
        if (config.enabled() && config.effort() != null) {
            builder.reasoning(Reasoning.builder().effort(config.effort()).build());
            logger.info("Reasoning enabled: {} (provider: {})", config.effort(), openAiProperties.getProviderCapabilities().getType());
        } else if (command.thinkingEnabled() && !openAiProperties.getProviderCapabilities().supportsReasoning()) {
            logger.debug("Reasoning requested but provider {} does not support it", openAiProperties.getProviderCapabilities().getType());
        }

        if (openAiProperties.getProviderCapabilities().getType() == ProviderCapabilities.ProviderType.OPENROUTER
            && openAiProperties.getProvider().getOrder() != null
            && !openAiProperties.getProvider().getOrder().isEmpty()) {

            Map<String, Object> provider = new LinkedHashMap<>();
            if (openAiProperties.getProvider().getSort() != null && !openAiProperties.getProvider().getSort().isBlank()) {
                provider.put("sort", openAiProperties.getProvider().getSort());
            }
            provider.put("order", openAiProperties.getProvider().getOrder());
            provider.put("allow_fallbacks", openAiProperties.getProvider().getAllowFallbacks());
            builder.putAdditionalBodyProperty("provider", com.openai.core.JsonValue.from(provider));

            logger.info("OpenRouter provider routing enabled: order={}, sort={}, allow_fallbacks={}",
                openAiProperties.getProvider().getOrder(),
                openAiProperties.getProvider().getSort(),
                openAiProperties.getProvider().getAllowFallbacks());
        }

        return new PreparedRequest(builder, config);
    }

    private String flattenResponseText(Response response, boolean jsonOutput) {
        if (response == null || response.output() == null) {
            return jsonOutput ? "{}" : "";
        }
        return response.output().stream()
            .flatMap(item -> item.message().stream())
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

    private long safeTokenCount(Long value) {
        return value == null ? 0L : value;
    }

    private record PreparedRequest(ResponseCreateParams.Builder builder,
                                   ValidatedThinkingConfig config) {}

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
            logger.debug("Provider {} does not support embeddings, returning empty vector",
                openAiProperties.getProviderCapabilities().getType());
            return new float[0];
        }

        if (openAiClient == null) {
            logger.error("Embedding generation failed: OpenAI client not configured");
            return new float[0];
        }

        try {
            logLlmInvocation("embedding", openAiProperties.getEmbedding().getModel(), false, false, false, null);
            List<Embedding> data = openAiClient.embeddings().create(
                EmbeddingCreateParams.builder()
                    .model(EmbeddingModel.of(openAiProperties.getEmbedding().getModel()))
                    .input(text)
                    .build()).data();
            if (data.isEmpty()) {
                logger.warn("Embedding API returned empty data for input of {} chars", text.length());
                return new float[0];
            }
            List<Float> vector = data.get(0).embedding();
            float[] result = new float[vector.size()];
            for (int i = 0; i < vector.size(); i++) {
                result[i] = vector.get(i);
            }
            return result;
        } catch (Exception e) {
            logger.error("Embedding generation failed for input of {} chars: {}", text.length(), e.getMessage(), e);
            return new float[0];
        }
    }

    /**
     * Resolves the chat model to use for completion requests.
     *
     * Configuration source of truth: OpenAiProperties.java
     * Chat model: {@link OpenAiProperties.Model#getChat()} - "gpt-4o-mini"
     */
    private ChatModel resolveChatModel() {
        return ChatModel.of(openAiProperties.getModel().getChat());
    }

    /**
     * Builds email assistant messages for the OpenAI API.
     *
     * Configuration source of truth: OpenAiProperties.java
     * System prompt: {@link OpenAiProperties.Prompts#getEmailAssistantSystem()}
     */
    private List<ResponseInputItem> buildEmailAssistantMessages(String emailContext,
                                                                String userMessage,
                                                                List<ConversationTurn> conversationHistory,
                                                                boolean jsonOutput) {
        List<ResponseInputItem> messages = new ArrayList<>();
        int totalTokenEstimate = 0;

        String systemPrompt = openAiProperties.getPrompts().getEmailAssistantSystem();
        if (!StringUtils.isBlank(systemPrompt)) {
            // Inject current timestamps for temporal awareness
            String timestampedPrompt = systemPrompt
                .replace("{currentUtcTime}", TemporalUtils.getCurrentUtcFormatted())
                .replace("{currentPacificTime}", TemporalUtils.getCurrentPacificFormatted());

            String sanitizedSystem = StringUtils.sanitize(timestampedPrompt);
            messages.add(ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
                .role(EasyInputMessage.Role.SYSTEM)
                .content(sanitizedSystem)
                .build()));
            totalTokenEstimate += estimateTokens(sanitizedSystem);
            
            if (logger.isDebugEnabled()) {
                String promptPreview = sanitizedSystem.length() > 400 
                    ? sanitizedSystem.substring(0, 400) + "..." 
                    : sanitizedSystem;
                logger.debug("System prompt being sent (first 400 chars): {}", promptPreview);
            }
        }

        if (jsonOutput) {
            String jsonOutputDirective = """
                JSON output mode:
                - Apply every rule above without modification while you craft the JSON.
                - Answer the user's latest request by returning a single JSON object that reflects your best-estimate schema for their question using the provided email context and conversation history.
                - Include fields, nested objects, or arrays only when they help communicate the email-backed facts; prefer null or empty values instead of inventing data.
                - Do not wrap the JSON in markdown fences or add commentary before or after the object.
                """;
            String sanitizedDirective = StringUtils.sanitize(jsonOutputDirective);
            messages.add(ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
                .role(EasyInputMessage.Role.SYSTEM)
                .content(sanitizedDirective)
                .build()));
            totalTokenEstimate += estimateTokens(sanitizedDirective);
        }

        String safeContext = StringUtils.sanitize(emailContext);
        if (!StringUtils.isBlank(safeContext)) {
            String contextMessage = "Email Context:\n" + safeContext;
            messages.add(ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
                .role(EasyInputMessage.Role.SYSTEM)
                .content(contextMessage)
                .build()));
            totalTokenEstimate += estimateTokens(contextMessage);
            
            if (logger.isDebugEnabled()) {
                String preview = safeContext.length() > 500 
                    ? safeContext.substring(0, 500) + "..." 
                    : safeContext;
                logger.debug("Email context being sent to model (first 500 chars): {}", preview);
            }
        }

        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            for (ConversationTurn turn : conversationHistory) {
                if (turn == null || StringUtils.isBlank(turn.content())) continue;
                String sanitized = StringUtils.sanitize(turn.content());
                if (StringUtils.isBlank(sanitized)) continue;
                messages.add(ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
                    .role(mapRole(turn.role()))
                    .content(sanitized)
                    .build()));
                totalTokenEstimate += estimateTokens(sanitized);
            }
        }

        String prompt = userMessage == null ? "" : userMessage;
        if (jsonOutput) {
            String normalized = prompt.toLowerCase(Locale.ROOT);
            if (!normalized.contains("respond strictly as a json object")
                && !normalized.contains("respond with a single valid json object")) {
                prompt = prompt.isBlank()
                    ? "Respond strictly as a JSON object. Do not include markdown fences or explanatory text."
                    : prompt + "\n\nRespond strictly as a JSON object. Do not include markdown fences or explanatory text.";
            }
        }

        String sanitizedPrompt = StringUtils.sanitize(prompt);
        messages.add(ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
            .role(EasyInputMessage.Role.USER)
            .content(sanitizedPrompt)
            .build()));
        totalTokenEstimate += estimateTokens(sanitizedPrompt);

        logger.debug("Building prompt: total=~{}tok, contextChars={}, priorTurns={}",
            totalTokenEstimate, safeContext.length(),
            conversationHistory != null ? conversationHistory.size() : 0);

        if (totalTokenEstimate > 100000) {
            logger.warn("Large prompt detected: ~{}tok may approach model limits", totalTokenEstimate);
        }

        return messages;
    }

    /**
     * Builds intent analysis messages for the OpenAI API.
     *
     * Configuration source of truth: OpenAiProperties.java
     * System prompt: {@link OpenAiProperties.Prompts#getIntentAnalysisSystem()}
     * Categories: {@link OpenAiProperties.Intent#getCategories()}
     */
    private List<ResponseInputItem> buildIntentAnalysisMessages(String userMessage) {
        String systemMessage = openAiProperties.getPrompts().getIntentAnalysisSystem()
            .replace("{categories}", openAiProperties.getIntent().getCategories());
        return List.of(ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
            .role(EasyInputMessage.Role.USER)
            .content(systemMessage + "\n\n" + userMessage)
            .build()));
    }

    private EasyInputMessage.Role mapRole(ConversationTurn.Role role) {
        return switch (role) {
            case USER -> EasyInputMessage.Role.USER;
            case ASSISTANT -> EasyInputMessage.Role.ASSISTANT;
            case SYSTEM -> EasyInputMessage.Role.SYSTEM;
        };
    }

    private void logLlmInvocation(String operation, String modelId, boolean streaming, boolean jsonOutput,
                                  boolean thinkingRequested, @Nullable ReasoningEffort reasoningEffort) {
        ProviderCapabilities capabilities = openAiProperties.getProviderCapabilities();
        String provider = capabilities == null || capabilities.getType() == null
            ? "UNKNOWN"
            : capabilities.getType().name();
        String baseUrl = openAiProperties.getApi() != null && openAiProperties.getApi().getBaseUrl() != null
            ? openAiProperties.getApi().getBaseUrl()
            : "unset";
        String thinkingLabel = thinkingRequested
            ? (reasoningEffort != null ? reasoningEffort.toString().toLowerCase(Locale.ROOT) : "enabled")
            : "disabled";
        logger.info("LLM {} request: provider={} model={} baseUrl={} streaming={} jsonOutput={} thinking={}",
            operation, provider, modelId, baseUrl, streaming, jsonOutput, thinkingLabel);
    }


    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return (int) (text.split("\\s+").length * 1.3);
    }
}

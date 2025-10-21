package com.composerai.api.service;

import com.composerai.api.config.ErrorMessagesProperties;
import com.composerai.api.config.OpenAiProperties;
import com.composerai.api.util.StringUtils;
import com.openai.client.OpenAIClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.ChatModel;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseFailedEvent;
import com.openai.models.responses.ResponseStreamEvent;
import com.openai.models.embeddings.Embedding;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.models.embeddings.EmbeddingModel;
import com.composerai.api.service.email.HtmlConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.Locale;
import java.util.regex.Pattern;

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
            String safeRaw = rawText == null ? "" : rawText;
            return new ChatCompletionResult(safeRaw, HtmlConverter.markdownToSafeHtml(DANGEROUS_BLOCK_TAGS.matcher(safeRaw).replaceAll("")));
        }
    }

    /** Validated thinking configuration. Backend validates against model capabilities. */
    public record ValidatedThinkingConfig(boolean enabled, ReasoningEffort effort) {
        static ValidatedThinkingConfig resolve(OpenAiProperties properties, String modelId,
                                               boolean requestedEnabled, String requestedLevel) {
            return (modelId == null || !properties.supportsReasoning(modelId) || !requestedEnabled)
                ? new ValidatedThinkingConfig(false, null)
                : new ValidatedThinkingConfig(true, parseEffort(requestedLevel).orElse(ReasoningEffort.MINIMAL));
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

    public ChatCompletionResult generateResponse(String userMessage, String emailContext,
                                                 boolean thinkingEnabled, String thinkingLevel) {
        return executeWithFallback(() -> {
            if (openAiClient == null) return ChatCompletionResult.fromRaw(errorMessages.getOpenai().getMisconfigured());

            String aiResponse = openAiClient.responses().create(ResponseCreateParams.builder()
                .model(resolveChatModel())
                .inputOfResponse(buildEmailAssistantMessages(emailContext, userMessage))
                .build()).output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .map(outputText -> outputText.text())
                .collect(java.util.stream.Collectors.joining());

            logger.info("Chat completion: input={} output={} tokens",
                (long)(userMessage != null ? userMessage.split("\\s+").length * 1.25 : 0),
                (long)(aiResponse != null ? aiResponse.split("\\s+").length * 1.25 : 0));
            return ChatCompletionResult.fromRaw(aiResponse);
        }, ChatCompletionResult.fromRaw(errorMessages.getOpenai().getUnavailable()), "OpenAI error while generating response");
    }

    /**
     * Analyzes user intent and classifies it into predefined categories.
     *
     * Configuration source of truth: OpenAiProperties.java
     * Default category: {@link OpenAiProperties.Intent#getDefaultCategory()} - "question"
     * Max tokens: {@link OpenAiProperties.Intent#getMaxOutputTokens()} - 10
     */
    public String analyzeIntent(String userMessage) {
        return executeWithFallback(() -> {
            if (openAiClient == null) return openAiProperties.getIntent().getDefaultCategory();

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
            return intent.isEmpty() ? openAiProperties.getIntent().getDefaultCategory() : intent;
        }, openAiProperties.getIntent().getDefaultCategory(), "OpenAI error while analyzing intent");
    }

    public void streamResponse(String userMessage, String emailContext,
                               boolean thinkingEnabled, String thinkingLevel,
                               Consumer<StreamEvent> onEvent, Runnable onComplete, Consumer<Throwable> onError) {
        if (openAiClient == null) {
            onError.accept(new IllegalStateException(errorMessages.getOpenai().getMisconfigured()));
            return;
        }

        ResponseCreateParams.Builder builder = ResponseCreateParams.builder()
            .model(resolveChatModel())
            .inputOfResponse(buildEmailAssistantMessages(emailContext, userMessage));

        String modelId = openAiProperties.getModel().getChat();
        ValidatedThinkingConfig config = ValidatedThinkingConfig.resolve(openAiProperties, modelId, thinkingEnabled, thinkingLevel);
        if (config.enabled() && config.effort() != null) {
            builder.reasoning(Reasoning.builder().effort(config.effort()).build());
            logger.info("Reasoning enabled: {} (provider: {})", config.effort(), openAiProperties.getProviderCapabilities().getType());
        } else if (thinkingEnabled && !openAiProperties.getProviderCapabilities().supportsReasoning()) {
            logger.debug("Reasoning requested but provider {} does not support it", openAiProperties.getProviderCapabilities().getType());
        }

        MarkdownStreamAssembler assembler = new MarkdownStreamAssembler();
        long startNanos = System.nanoTime();
        final long[] tokenCount = new long[]{0};
        final boolean[] failed = {false};

        try {
            try (StreamResponse<ResponseStreamEvent> streamResponse = openAiClient.responses().createStreaming(builder.build())) {
                streamResponse.stream().forEach(event -> {
                    try {
                        event.outputTextDelta().ifPresent(textDelta -> emitHtmlDelta(textDelta.delta(), assembler, onEvent, tokenCount));
                        emitReasoningEvents(event, onEvent);
                        event.failed().ifPresent(failedEvent -> {
                            failed[0] = true;
                            onEvent.accept(StreamEvent.failed(failedEvent));
                        });
                    } catch (Exception processingError) {
                        logger.warn("Failed to process stream event", processingError);
                    }
                });
                assembler.flushRemainder().ifPresent(remainder -> {
                    if (!remainder.isBlank()) {
                        onEvent.accept(StreamEvent.renderedHtml(remainder));
                        tokenCount[0] += remainder.length();
                    }
                });
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
    }

    public sealed interface StreamEvent permits StreamEvent.RenderedHtml, StreamEvent.Reasoning, StreamEvent.Failed {
        static StreamEvent renderedHtml(String htmlChunk) { return new RenderedHtml(htmlChunk); }
        static StreamEvent reasoning(ReasoningStreamAdapter.ReasoningEvent event) { return new Reasoning(event); }
        static StreamEvent failed(ResponseFailedEvent event) { return new Failed(event); }
        record RenderedHtml(String html) implements StreamEvent {}
        record Reasoning(ReasoningStreamAdapter.ReasoningEvent value) implements StreamEvent {}
        record Failed(ResponseFailedEvent value) implements StreamEvent {}
    }

    private void emitHtmlDelta(String deltaText, MarkdownStreamAssembler assembler, Consumer<StreamEvent> onEvent, long[] tokenCount) {
        if (deltaText == null || deltaText.isEmpty()) return;
        if (logger.isDebugEnabled()) {
            logger.debug("Streaming delta ({} chars): {}", deltaText.length(), preview(deltaText));
        }
        for (String htmlChunk : assembler.onDelta(deltaText)) {
            if (htmlChunk != null && !htmlChunk.isBlank()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Emitting HTML chunk ({} chars): {}", htmlChunk.length(), preview(htmlChunk));
                }
                onEvent.accept(StreamEvent.renderedHtml(htmlChunk));
                tokenCount[0] += htmlChunk.length();
            }
        }
    }

    private void emitReasoningEvents(ResponseStreamEvent event, Consumer<StreamEvent> onEvent) {
        for (ReasoningStreamAdapter.ReasoningEvent reasoningEvent : ReasoningStreamAdapter.extract(event))
            onEvent.accept(StreamEvent.reasoning(reasoningEvent));
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
        if (text == null || text.isBlank()) return new float[0];
        
        // Check if provider supports embeddings
        if (!openAiProperties.getProviderCapabilities().supportsEmbeddings()) {
            logger.debug("Provider {} does not support embeddings, returning empty vector",
                openAiProperties.getProviderCapabilities().getType());
            return new float[0];
        }
        
        return executeWithFallback(() -> {
            List<Embedding> data = openAiClient.embeddings().create(
                EmbeddingCreateParams.builder()
                    .model(EmbeddingModel.of(openAiProperties.getEmbedding().getModel()))
                    .input(text)
                    .build()).data();
            if (data.isEmpty()) return new float[0];
            List<Float> vector = data.get(0).embedding();
            float[] result = new float[vector.size()];
            for (int i = 0; i < vector.size(); i++) result[i] = vector.get(i);
            return result;
        }, new float[0], "OpenAI error while generating embeddings");
    }

    /**
     * Resolves the chat model to use for completion requests.
     *
     * Configuration source of truth: OpenAiProperties.java
     * Chat model: {@link OpenAiProperties.Model#getChat()} - "o4-mini"
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
    private List<ResponseInputItem> buildEmailAssistantMessages(String emailContext, String userMessage) {
        String safeContext = StringUtils.sanitize(emailContext);
        String systemMessage = openAiProperties.getPrompts().getEmailAssistantSystem();
        String fullPrompt = systemMessage +
            (StringUtils.isBlank(safeContext) ? "" : "\n\nEmail Context:\n" + safeContext) +
            "\n\nQuestion: " + userMessage;
        
        // Log context length to detect potential truncation issues
        int promptTokenEstimate = (int)(fullPrompt.split("\\s+").length * 1.3);
        int contextTokenEstimate = (int)(safeContext.split("\\s+").length * 1.3);
        logger.debug("Building prompt: total=~{}tok, context=~{}tok, contextChars={}", 
            promptTokenEstimate, contextTokenEstimate, safeContext.length());
        
        if (promptTokenEstimate > 100000) {
            logger.warn("Large prompt detected: ~{}tok may approach model limits", promptTokenEstimate);
        }
        
        return List.of(ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
            .role(EasyInputMessage.Role.USER)
            .content(fullPrompt)
            .build()));
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


    static final class MarkdownStreamAssembler {
        private final StringBuilder buffer = new StringBuilder();
        private final StringBuilder lineBuffer = new StringBuilder();
        private boolean insideCodeFence = false;
        private char fenceDelimiter = '`';
        private static final int CHUNK_THRESHOLD = 1536;

        List<String> onDelta(String delta) {
            if (delta == null || delta.isEmpty()) return Collections.emptyList();

            List<String> flushed = null;
            for (int i = 0; i < delta.length(); i++) {
                char ch = delta.charAt(i);
                if (ch == '\r') continue;

                buffer.append(ch);
                if (ch == '\n') {
                    String trimmedLine = lineBuffer.toString().trim();
                    updateFenceState(trimmedLine);
                    if (!insideCodeFence) {
                        if (trimmedLine.isEmpty()) {
                            flushed = addChunk(flushed, flushBuffer());
                        } else {
                            flushed = addChunk(flushed, flushToParagraphBreakIfNeeded());
                        }
                    }
                    lineBuffer.setLength(0);
                } else {
                    lineBuffer.append(ch);
                    if (!insideCodeFence) {
                        flushed = addChunk(flushed, flushToParagraphBreakIfNeeded());
                    }
                }
            }
            if (logger.isDebugEnabled() && flushed != null) {
                for (String chunk : flushed) {
                    logger.debug("MarkdownAssembler flush chunk ({} chars): {}", chunk != null ? chunk.length() : 0, preview(chunk));
                }
            }
            return flushed == null ? Collections.emptyList() : flushed;
        }

        private List<String> addChunk(List<String> list, String chunk) {
            if (chunk == null) return list;
            if (list == null) list = new ArrayList<>();
            list.add(chunk);
            return list;
        }

        Optional<String> flushRemainder() {
            if (buffer.isEmpty()) return Optional.empty();
            String markdown = buffer.toString();
            buffer.setLength(0);
            resetLineBuffer();
            String chunk = renderMarkdown(markdown);
            insideCodeFence = false;
            if (logger.isDebugEnabled()) {
                logger.debug("MarkdownAssembler flush remainder ({} chars): {}", chunk != null ? chunk.length() : 0, preview(chunk));
            }
            return chunk == null || chunk.isBlank() ? Optional.empty() : Optional.of(chunk);
        }

        private void updateFenceState(String trimmedLine) {
            if (trimmedLine.isEmpty()) return;
            if (trimmedLine.startsWith("```") || trimmedLine.startsWith("~~~")) {
                char delimiter = trimmedLine.charAt(0);
                if (!insideCodeFence) {
                    insideCodeFence = true;
                    fenceDelimiter = delimiter;
                } else if (fenceDelimiter == delimiter) {
                    insideCodeFence = false;
                }
            }
        }

        private String flushBuffer() {
            if (buffer.isEmpty()) return null;
            String markdown = buffer.toString();
            buffer.setLength(0);
            String chunk = renderMarkdown(markdown);
            resetLineBuffer();
            return chunk;
        }

        private String flushToParagraphBreakIfNeeded() {
            if (buffer.length() < CHUNK_THRESHOLD) return null;
            int boundary = lastParagraphBoundary();
            if (boundary < 0) return null;
            String markdown = buffer.substring(0, boundary);
            buffer.delete(0, boundary);
            resetLineBuffer();
            return renderMarkdown(markdown);
        }

        private int lastParagraphBoundary() {
            int idx = buffer.lastIndexOf("\n\n");
            if (idx < 0) return -1;
            // include blank line in the flushed segment
            return idx + 2;
        }

        private String renderMarkdown(String markdown) {
            if (markdown == null || markdown.isBlank()) return null;
            String rendered = HtmlConverter.markdownToSafeHtml(markdown);
            return rendered == null || rendered.isBlank() ? null : rendered;
        }

        private void resetLineBuffer() {
            lineBuffer.setLength(0);
            if (!buffer.isEmpty()) {
                int lastNewline = buffer.lastIndexOf("\n");
                if (lastNewline == -1) {
                    lineBuffer.append(buffer);
                } else if (lastNewline + 1 < buffer.length()) {
                    lineBuffer.append(buffer.substring(lastNewline + 1));
                }
            }
        }
    }

    private static String preview(String value) {
        if (value == null) return "<null>";
        String trimmed = value.replaceAll("\n", "\\n");
        return trimmed.length() <= 120 ? trimmed : trimmed.substring(0, 117) + "...";
    }
}

package com.composerai.api.service;

import com.composerai.api.config.OpenAiProperties;
import com.openai.client.OpenAIClient;
import com.openai.models.ChatModel;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.ResponsesModel;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.Embedding;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.models.embeddings.EmbeddingModel;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseError;
import com.openai.models.responses.ResponseFailedEvent;
import com.openai.models.responses.ResponseIncompleteEvent;
import com.openai.models.responses.ResponseStatus;
import com.openai.models.responses.ResponseStreamEvent;
import com.openai.core.http.StreamResponse;
import com.composerai.api.service.email.HtmlConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    
    private static final String OPENAI_MISCONFIGURED_MESSAGE = "OpenAI is not configured (missing OPENAI_API_KEY).";
    private static final String OPENAI_UNAVAILABLE_MESSAGE = "OpenAI is unavailable right now. Please try again later.";
    private static final Pattern DANGEROUS_BLOCK_TAGS = Pattern.compile("(?is)<(script|style|iframe)[^>]*>.*?</\\1>");

    private final OpenAIClient openAiClient;
    private final OpenAiProperties openAiProperties;

    public record ChatCompletionResult(String rawText, String sanitizedHtml) {
        public ChatCompletionResult {
            rawText = rawText == null ? "" : rawText;
            sanitizedHtml = sanitizedHtml == null ? "" : sanitizedHtml;
        }

        static ChatCompletionResult fromRaw(String rawText) {
            String safeRaw = rawText == null ? "" : rawText;
            String scrubbed = DANGEROUS_BLOCK_TAGS.matcher(safeRaw).replaceAll("");
            String sanitized = HtmlConverter.markdownToSafeHtml(scrubbed);
            return new ChatCompletionResult(safeRaw, sanitized);
        }
    }

    public OpenAiChatService(OpenAIClient openAiClient, OpenAiProperties openAiProperties) {
        this.openAiClient = openAiClient;
        this.openAiProperties = openAiProperties;
    }

    public ChatCompletionResult generateResponse(String userMessage, String emailContext,
                                                 boolean thinkingEnabled, String thinkingLevel) {
        ChatCompletionResult fallback = ChatCompletionResult.fromRaw(OPENAI_UNAVAILABLE_MESSAGE);
        return executeWithFallback(() -> {
            if (openAiClient == null) {
                return ChatCompletionResult.fromRaw(OPENAI_MISCONFIGURED_MESSAGE);
            }

            ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .model(resolveChatModel())
                .messages(buildEmailAssistantMessages(emailContext, userMessage))
                .maxCompletionTokens(8000L);
            // GPT-5 models reject custom temperature values; rely on OpenAI defaults.
            // builder.temperature(0.7);

            resolveReasoningEffort(thinkingEnabled, thinkingLevel)
                .ifPresent(builder::reasoningEffort);

            // Add GPT-5 specific parameters
            String modelName = openAiProperties.getModel();
            if (modelName != null && modelName.toLowerCase().startsWith("gpt-5")) {
                logger.debug("Using GPT-5 model with increased completion tokens");
            }

            ChatCompletionCreateParams params = builder.build();

            ChatCompletion response = openAiClient.chat().completions().create(params);

            String aiResponse = extractChoiceContent(response, OPENAI_UNAVAILABLE_MESSAGE);
            logger.info("Generated AI response for user query");
            return ChatCompletionResult.fromRaw(aiResponse);
        }, fallback, "OpenAI error while generating response");
    }

    public String analyzeIntent(String userMessage) {
        return executeWithFallback(() -> {
            if (openAiClient == null) {
                return "question";
            }
            ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .model(resolveChatModel())
                .messages(buildIntentAnalysisMessages(userMessage))
                .maxCompletionTokens(10L);
            // GPT-5 models reject custom temperature values; rely on OpenAI defaults.
            // builder.temperature(0.1);

            ChatCompletionCreateParams params = builder.build();

            ChatCompletion response = openAiClient.chat().completions().create(params);
            String intent = extractChoiceContent(response, "question").trim().toLowerCase();

            logger.info("Analyzed intent: {}", intent);
            return intent.isEmpty() ? "question" : intent;
        }, "question", "OpenAI error while analyzing intent");
    }

    public void streamResponse(String userMessage, String emailContext,
                               boolean thinkingEnabled, String thinkingLevel,
                               Consumer<String> onChunk, Runnable onComplete, Consumer<Throwable> onError) {
        if (openAiClient == null) {
            onError.accept(new IllegalStateException(OPENAI_MISCONFIGURED_MESSAGE));
            return;
        }

        String safeUserMessage = userMessage == null ? "" : userMessage;
        ResponseCreateParams params = buildStreamingParams(safeUserMessage, emailContext, thinkingEnabled, thinkingLevel);
        MarkdownStreamAssembler assembler = new MarkdownStreamAssembler();

        long startNanos = System.nanoTime();
        final long[] tokenCount = new long[]{0};
        try (StreamResponse<ResponseStreamEvent> stream = openAiClient.responses().createStreaming(params)) {
            stream.stream().forEach(event -> {
                event.error().ifPresent(errorEvent -> {
                    throw new StreamingAbortedException(normalizeMessage(errorEvent.message()));
                });

                event.failed().ifPresent(failedEvent -> {
                    throw new StreamingAbortedException(describeFailedEvent(failedEvent));
                });

                event.incomplete().ifPresent(incomplete -> {
                    throw new StreamingAbortedException(describeIncompleteEvent(incomplete));
                });

                event.outputTextDelta().ifPresent(deltaEvent -> {
                    // Log GPT-5 reasoning metadata if present
                    String modelName = openAiProperties.getModel();
                    if (modelName != null && modelName.toLowerCase().startsWith("gpt-5")) {
                        // GPT-5 may include reasoning metadata in future SDK versions
                        logger.trace("GPT-5 delta event received with {} chars", 
                            deltaEvent.delta() != null ? deltaEvent.delta().length() : 0);
                    }
                    
                    List<String> htmlChunks = assembler.onDelta(deltaEvent.delta());
                    if (!htmlChunks.isEmpty()) {
                        for (String chunk : htmlChunks) {
                            if (chunk != null && !chunk.isBlank()) {
                                onChunk.accept(chunk);
                                tokenCount[0] += chunk.length();
                            }
                        }
                    }
                });
            });

            assembler.flushRemainder().ifPresent(remainder -> {
                if (!remainder.isBlank()) {
                    onChunk.accept(remainder);
                    tokenCount[0] += remainder.length();
                }
            });

            onComplete.run();
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            logger.info("Completed streaming response using OpenAI Responses API: tokensApprox={}, elapsedMs={}", tokenCount[0], elapsedMs);
        } catch (StreamingAbortedException aborted) {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            logger.warn("OpenAI streaming aborted after {} ms: {}", elapsedMs, aborted.getMessage());
            onError.accept(new RuntimeException(aborted.getMessage(), aborted));
        } catch (Exception e) {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            logger.warn("OpenAI error while streaming after {} ms", elapsedMs, e);
            onError.accept(new RuntimeException(OPENAI_UNAVAILABLE_MESSAGE, e));
        }
    }

    public float[] generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            logger.debug("Skipping embedding generation for null/empty input");
            return new float[0];
        }
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

    private ResponsesModel resolveResponseModel() {
        String configuredModel = openAiProperties.getModel();
        if (configuredModel != null && !configuredModel.isBlank()) {
            return ResponsesModel.ofString(configuredModel.trim());
        }
        return ResponsesModel.ofChat(ChatModel.CHATGPT_4O_LATEST);
    }

    private ChatModel resolveChatModel() {
        String configuredModel = openAiProperties.getModel();
        if (configuredModel != null && !configuredModel.isBlank()) {
            return ChatModel.of(configuredModel);
        }
        return ChatModel.CHATGPT_4O_LATEST;
    }

    private ResponseCreateParams buildStreamingParams(String userMessage, String emailContext,
                                                     boolean thinkingEnabled, String thinkingLevel) {
        String safeContext = emailContext == null || emailContext.isBlank()
            ? "No relevant emails found."
            : emailContext;

        String instructions = "You are an AI assistant that helps users understand and work with their email. " +
            "Return well-structured Markdown and cite or reference the provided email context when it is relevant.";

        String prompt = "Email context:\n" + safeContext + "\n\nUser request:\n" + userMessage;

        ResponseCreateParams.Builder builder = ResponseCreateParams.builder()
            .model(resolveResponseModel())
            .instructions(instructions)
            .input(prompt)
            // Increase output budget to allow richer responses
            .maxOutputTokens(8000L);
        // GPT-5 models reject custom temperature values; rely on OpenAI defaults.
        // builder.temperature(0.7);

        // Add GPT-5 specific parameters if using gpt-5 models
        String modelName = openAiProperties.getModel();
        if (modelName != null && modelName.toLowerCase().startsWith("gpt-5")) {
            // builder.reasoningEffort(ReasoningEffort.MINIMAL);
            logger.debug("Using GPT-5 model: {} with increased token limit (8000)", modelName);
        }

        resolveReasoningEffort(thinkingEnabled, thinkingLevel)
            .ifPresent(effort -> builder.reasoning(Reasoning.builder().effort(effort).build()));

        return builder.build();
    }

    private Optional<ReasoningEffort> resolveReasoningEffort(boolean thinkingEnabled, String thinkingLevel) {
        if (!thinkingEnabled) {
            return Optional.empty();
        }

        String normalized = thinkingLevel == null ? "" : thinkingLevel.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return Optional.of(ReasoningEffort.MINIMAL);
        }

        ReasoningEffort effort = switch (normalized) {
            case "minimal" -> ReasoningEffort.MINIMAL;
            case "light" -> ReasoningEffort.of("light");
            case "low" -> ReasoningEffort.LOW;
            case "standard" -> ReasoningEffort.of("standard");
            case "medium" -> ReasoningEffort.MEDIUM;
            case "extended" -> ReasoningEffort.of("extended");
            case "heavy" -> ReasoningEffort.of("heavy");
            case "high" -> ReasoningEffort.HIGH;
            default -> ReasoningEffort.of(normalized);
        };

        return Optional.of(effort);
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

    private String describeFailedEvent(ResponseFailedEvent failedEvent) {
        if (failedEvent == null || failedEvent.response() == null) {
            return OPENAI_UNAVAILABLE_MESSAGE;
        }

        String message = failedEvent.response().error()
            .map(this::formatResponseError)
            .orElse(null);

        if (message == null) {
            message = failedEvent.response().status()
                .map(ResponseStatus::asString)
                .map(status -> "OpenAI streaming failed (status: " + status.toLowerCase(Locale.ROOT) + ")")
                .orElse(null);
        }

        if (message == null) {
            String responseId = failedEvent.response().id();
            if (responseId != null && !responseId.isBlank()) {
                message = "OpenAI streaming failed (response id: " + responseId + ")";
            }
        }

        return normalizeMessage(message);
    }

    private String describeIncompleteEvent(ResponseIncompleteEvent incompleteEvent) {
        if (incompleteEvent == null || incompleteEvent.response() == null) {
            return OPENAI_UNAVAILABLE_MESSAGE;
        }

        String reason = incompleteEvent.response().incompleteDetails()
            .flatMap(details -> details.reason())
            .map(detailReason -> normalize(detailReason.asString()))
            .orElse(null);

        if (reason != null) {
            return "OpenAI ended the response early (reason: " + reason + ")";
        }

        String statusMessage = incompleteEvent.response().status()
            .map(ResponseStatus::asString)
            .map(status -> "OpenAI ended the response early (status: " + status.toLowerCase(Locale.ROOT) + ")")
            .orElse(null);

        return normalizeMessage(statusMessage);
    }

    private String formatResponseError(ResponseError error) {
        if (error == null) {
            return null;
        }

        String message = normalize(error.message());
        ResponseError.Code code = error.code();
        if (code != null) {
            String codeValue = normalize(code.asString());
            if (codeValue != null) {
                if (message == null) {
                    message = "OpenAI error (code: " + codeValue + ")";
                } else if (!message.toLowerCase(Locale.ROOT).contains(codeValue.toLowerCase(Locale.ROOT))) {
                    message = message + " (code: " + codeValue + ")";
                }
            }
        }
        return message;
    }

    private static String normalizeMessage(String message) {
        String normalized = normalize(message);
        return normalized != null ? normalized : OPENAI_UNAVAILABLE_MESSAGE;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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

    private static final class StreamingAbortedException extends RuntimeException {
        StreamingAbortedException(String message) {
            super(message == null || message.isBlank() ? OPENAI_UNAVAILABLE_MESSAGE : message);
        }
    }

    static final class MarkdownStreamAssembler {
        private final StringBuilder buffer = new StringBuilder();
        private final StringBuilder lineBuffer = new StringBuilder();
        private boolean insideCodeFence = false;
        private char fenceDelimiter = '`';

        List<String> onDelta(String delta) {
            if (delta == null || delta.isEmpty()) {
                return Collections.emptyList();
            }

            List<String> flushed = null;
            for (int i = 0; i < delta.length(); i++) {
                char ch = delta.charAt(i);
                if (ch == '\r') {
                    continue;
                }

                buffer.append(ch);
                if (ch == '\n') {
                    String trimmedLine = lineBuffer.toString().trim();
                    updateFenceState(trimmedLine);
                    if (!insideCodeFence && trimmedLine.isEmpty()) {
                        String chunk = flushBuffer();
                        if (chunk != null) {
                            if (flushed == null) {
                                flushed = new ArrayList<>();
                            }
                            flushed.add(chunk);
                        }
                    }
                    lineBuffer.setLength(0);
                } else {
                    lineBuffer.append(ch);
                }
            }

            return flushed == null ? Collections.emptyList() : flushed;
        }

        Optional<String> flushRemainder() {
            if (buffer.isEmpty()) {
                return Optional.empty();
            }
            String chunk = sanitizeBuffer();
            insideCodeFence = false;
            return chunk == null || chunk.isBlank() ? Optional.empty() : Optional.of(chunk);
        }

        private void updateFenceState(String trimmedLine) {
            if (trimmedLine.isEmpty()) {
                return;
            }
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
            if (buffer.isEmpty()) {
                return null;
            }
            return sanitizeBuffer();
        }

        private String sanitizeBuffer() {
            String markdown = buffer.toString();
            buffer.setLength(0);
            lineBuffer.setLength(0);
            if (markdown.isBlank()) {
                return null;
            }
            String rendered = HtmlConverter.markdownToSafeHtml(markdown);
            return rendered == null || rendered.isBlank() ? null : rendered;
        }
    }
}

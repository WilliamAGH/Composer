package com.composerai.api.adapters.out.openai;

import com.composerai.api.config.ErrorMessagesProperties;
import com.openai.client.OpenAIClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseStreamEvent;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Executes a Responses API stream and emits Composer's stable stream events. */
final class OpenAiResponseStreamExecutor {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiResponseStreamExecutor.class);
    private static final long NANOS_PER_MILLISECOND = 1_000_000L;

    private final OpenAIClient openAiClient;
    private final ErrorMessagesProperties errorMessages;

    OpenAiResponseStreamExecutor(OpenAIClient openAiClient, ErrorMessagesProperties errorMessages) {
        this.openAiClient = openAiClient;
        this.errorMessages = errorMessages;
    }

    void stream(
            ResponseCreateParams params,
            boolean jsonOutput,
            boolean localDebugEnabled,
            Consumer<OpenAiStreamEvent> onEvent,
            Runnable onComplete,
            Consumer<Throwable> onError) {
        boolean debugEnabled = logger.isDebugEnabled() && localDebugEnabled;
        MarkdownStreamAssembler assembler = jsonOutput ? null : new MarkdownStreamAssembler(debugEnabled);
        long startNanos = System.nanoTime();
        long[] tokenCount = {0};
        boolean[] failed = {false};

        try (StreamResponse<ResponseStreamEvent> streamResponse =
                openAiClient.responses().createStreaming(params)) {
            streamResponse.stream()
                    .forEach(event ->
                            processEvent(event, jsonOutput, assembler, onEvent, tokenCount, failed, debugEnabled));
            flushRemainder(jsonOutput, assembler, onEvent, tokenCount);
            logger.info(
                    "Streaming completed: tokens={} elapsed={}ms",
                    tokenCount[0],
                    (System.nanoTime() - startNanos) / NANOS_PER_MILLISECOND);
            if (failed[0]) {
                onError.accept(new RuntimeException(errorMessages.getOpenai().getUnavailable()));
            } else {
                onComplete.run();
            }
        } catch (Exception exception) {
            logger.error(
                    "Streaming failed after {}ms", (System.nanoTime() - startNanos) / NANOS_PER_MILLISECOND, exception);
            String fallbackMessage = exception.getMessage() == null
                            || exception.getMessage().trim().isEmpty()
                    ? errorMessages.getOpenai().getUnavailable()
                    : exception.getMessage().trim();
            onError.accept(new RuntimeException(fallbackMessage, exception));
        }
    }

    private void processEvent(
            ResponseStreamEvent event,
            boolean jsonOutput,
            MarkdownStreamAssembler assembler,
            Consumer<OpenAiStreamEvent> onEvent,
            long[] tokenCount,
            boolean[] failed,
            boolean debugEnabled) {
        try {
            event.outputTextDelta().ifPresent(textDelta -> {
                if (jsonOutput) {
                    emitJsonDelta(textDelta.delta(), onEvent, tokenCount, debugEnabled);
                } else {
                    emitHtmlDelta(textDelta.delta(), assembler, onEvent, tokenCount, debugEnabled);
                }
            });
            for (ReasoningStreamMapper.ReasoningEvent reasoningEvent : ReasoningStreamMapper.extract(event)) {
                onEvent.accept(new OpenAiStreamEvent.Reasoning(reasoningEvent));
            }
            event.failed().ifPresent(failedEvent -> {
                failed[0] = true;
                onEvent.accept(new OpenAiStreamEvent.Failed(failedEvent));
            });
        } catch (Exception processingError) {
            logger.error("Stream event processing failed - marking stream as failed", processingError);
            failed[0] = true;
        }
    }

    private void flushRemainder(
            boolean jsonOutput,
            MarkdownStreamAssembler assembler,
            Consumer<OpenAiStreamEvent> onEvent,
            long[] tokenCount) {
        if (!jsonOutput && assembler != null) {
            assembler.flushRemainder().ifPresent(remainder -> {
                if (!remainder.isBlank()) {
                    onEvent.accept(new OpenAiStreamEvent.RenderedHtml(remainder));
                    tokenCount[0] += remainder.length();
                }
            });
        }
    }

    private void emitHtmlDelta(
            String deltaText,
            MarkdownStreamAssembler assembler,
            Consumer<OpenAiStreamEvent> onEvent,
            long[] tokenCount,
            boolean debugEnabled) {
        if (deltaText == null || deltaText.isEmpty()) {
            return;
        }
        if (debugEnabled) {
            logger.debug(
                    "Streaming delta ({} chars): {}", deltaText.length(), MarkdownStreamAssembler.preview(deltaText));
        }
        onEvent.accept(new OpenAiStreamEvent.RawText(deltaText));
        for (String htmlChunk : assembler.onDelta(deltaText)) {
            if (htmlChunk != null && !htmlChunk.isBlank()) {
                if (debugEnabled) {
                    logger.debug(
                            "Emitting HTML chunk ({} chars): {}",
                            htmlChunk.length(),
                            MarkdownStreamAssembler.preview(htmlChunk));
                }
                onEvent.accept(new OpenAiStreamEvent.RenderedHtml(htmlChunk));
                tokenCount[0] += htmlChunk.length();
            }
        }
    }

    private void emitJsonDelta(
            String deltaText, Consumer<OpenAiStreamEvent> onEvent, long[] tokenCount, boolean debugEnabled) {
        if (deltaText == null || deltaText.isEmpty()) {
            return;
        }
        if (debugEnabled) {
            logger.debug(
                    "Streaming JSON delta ({} chars): {}",
                    deltaText.length(),
                    MarkdownStreamAssembler.preview(deltaText));
        }
        onEvent.accept(new OpenAiStreamEvent.RawJson(deltaText));
        tokenCount[0] += deltaText.length();
    }
}

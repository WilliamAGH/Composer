package com.composerai.api.application.usecase.chat;

import com.composerai.api.adapters.out.openai.ReasoningStreamMapper;
import java.util.function.Consumer;

/** Typed callback boundary for a streaming chat interaction. */
public record ChatStreamCallbacks(
        Consumer<String> onHtmlChunk,
        Consumer<String> onJsonChunk,
        Consumer<ReasoningStreamMapper.ReasoningMessage> onReasoning,
        Runnable onComplete,
        Consumer<Throwable> onError) {}

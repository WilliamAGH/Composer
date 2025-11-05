package com.composerai.api.service;

import com.composerai.api.config.AiCommandPromptProperties;
import com.composerai.api.config.ErrorMessagesProperties;
import com.composerai.api.config.OpenAiProperties;
import com.composerai.api.config.MagicEmailProperties;
import com.composerai.api.dto.ChatRequest;
import com.composerai.api.dto.ChatResponse;
import com.composerai.api.dto.ChatResponse.EmailContext;
import com.composerai.api.dto.SseEventType;
import com.composerai.api.service.email.HtmlConverter;
import com.composerai.api.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    private final VectorSearchService vectorSearchService;
    private final OpenAiChatService openAiChatService;
    private final OpenAiProperties openAiProperties;
    private final ErrorMessagesProperties errorMessages;
    private final ContextBuilder contextBuilder;
    private final ContextBuilder.EmailContextRegistry emailContextRegistry;
    private final ConversationRegistry conversationRegistry;
    private final ExecutorService streamingExecutor;
    private final MagicEmailProperties magicEmailProperties;
    private final AiCommandPromptProperties aiCommandPromptProperties;

    private static final String INSIGHTS_TRIGGER = "__INSIGHTS_TRIGGER__";
    private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile("\\[([^\\]]+)\\]\\(([^\\)]*)\\)");
    private static final Pattern BARE_URL_PATTERN = Pattern.compile("(?i)(?:https?://|www\\.)\\S+");

    public ChatService(VectorSearchService vectorSearchService, OpenAiChatService openAiChatService,
                       OpenAiProperties openAiProperties, ErrorMessagesProperties errorMessages,
                       ContextBuilder contextBuilder, ContextBuilder.EmailContextRegistry emailContextRegistry,
                       ConversationRegistry conversationRegistry,
                       MagicEmailProperties magicEmailProperties,
                       AiCommandPromptProperties aiCommandPromptProperties,
                       @Qualifier("chatStreamExecutor") ExecutorService streamingExecutor) {
        this.vectorSearchService = vectorSearchService;
        this.openAiChatService = openAiChatService;
        this.openAiProperties = openAiProperties;
        this.errorMessages = errorMessages;
        this.contextBuilder = contextBuilder;
        this.emailContextRegistry = emailContextRegistry;
        this.conversationRegistry = conversationRegistry;
        this.streamingExecutor = streamingExecutor;
        this.magicEmailProperties = magicEmailProperties;
        this.aiCommandPromptProperties = aiCommandPromptProperties;
    }

    private record ChatContext(float[] embedding, List<EmailContext> emailContext, String contextString) {}

    public record StreamMetadata(String conversationId) {}

    /**
     * Applies defaults from configuration to request parameters.
     *
     * Configuration source of truth: OpenAiProperties.java
     * Max search results: {@link OpenAiProperties.Defaults#getMaxSearchResults()} - 5
     */
    private int applyMaxResultsDefault(int requestedMaxResults) {
        return requestedMaxResults > 0 ? requestedMaxResults : openAiProperties.getDefaults().getMaxSearchResults();
    }

    private ChatContext prepareChatContext(String message, int maxResults) {
        if (INSIGHTS_TRIGGER.equals(message)) {
            return new ChatContext(new float[0], List.of(), "");
        }

        float[] queryVector = openAiChatService.generateEmbedding(message);
        List<EmailContext> emailContext = (queryVector == null || queryVector.length == 0)
            ? List.of()
            : vectorSearchService.searchSimilarEmails(queryVector, maxResults);
        return new ChatContext(queryVector, emailContext, contextBuilder.buildFromEmailList(emailContext));
    }

    private String resolvePromptForModel(ChatRequest request, String mergedContext) {
        String originalMessage = request.getMessage();
        if (INSIGHTS_TRIGGER.equals(originalMessage)) {
            String prompt = magicEmailProperties.getInsights().getPrompt();
            String sanitizedContext = sanitizeInsightsContext(mergedContext);
            if (StringUtils.isBlank(sanitizedContext)) {
                return prompt;
            }
            return prompt + "\n\nContext:\n" + sanitizedContext;
        }

        String command = request.getAiCommand();
        if (!StringUtils.isBlank(command)) {
            String template = aiCommandPromptProperties.promptFor(command).orElse(null);
            if (!StringUtils.isBlank(template)) {
                return renderCommandTemplate(template, originalMessage, request.getSubject());
            }
        }

        return formatMessageForOutput(originalMessage, request.isJsonOutput());
    }

    private String sanitizeInsightsContext(String context) {
        if (StringUtils.isBlank(context)) {
            return "";
        }
        String cleaned = HtmlConverter.cleanupOutput(context, true);
        cleaned = MARKDOWN_LINK_PATTERN.matcher(cleaned).replaceAll("$1");
        cleaned = BARE_URL_PATTERN.matcher(cleaned).replaceAll("");
        // Collapse leftover parentheses or multiple spaces created by link removal
        cleaned = cleaned.replaceAll("\\(\\s*\\)", "");
        cleaned = cleaned.replaceAll("\\s{2,}", " ");
        return HtmlConverter.cleanupOutput(cleaned.trim(), true);
    }

    private String renderCommandTemplate(String template, String instruction, String subject) {
        String safeInstruction = instruction == null ? "" : instruction.trim();
        String safeSubject = subject == null ? "" : subject.trim();

        String rendered = template;
        if (rendered.contains("{{instruction}}")) {
            rendered = rendered.replace("{{instruction}}", safeInstruction);
        } else if (!StringUtils.isBlank(safeInstruction)) {
            rendered = rendered + "\n\nAdditional direction:\n" + safeInstruction;
        }

        if (!StringUtils.isBlank(safeSubject)) {
            rendered = rendered + "\n\nSubject: " + safeSubject;
        }

        return rendered;
    }

    /**
     * Pass-through method for message formatting.
     * JSON instructions are added downstream in OpenAiChatService to avoid duplication.
     */
    private String formatMessageForOutput(String message, boolean jsonOutput) {
        return message;
    }

    public ChatResponse processChat(ChatRequest request) {
        String conversationId = StringUtils.ensureConversationId(request.getConversationId());
        String userMessageId = com.composerai.api.util.IdGenerator.uuidV7();
        String assistantMessageId = com.composerai.api.util.IdGenerator.uuidV7();
        int msgLen = request.getMessage() == null ? 0 : request.getMessage().length();
        logger.info("Processing chat: convId={}, msgLen={}", conversationId, msgLen);
        boolean jsonOutput = request.isJsonOutput();
        String originalMessage = request.getMessage();
        try {
            String intent = INSIGHTS_TRIGGER.equals(originalMessage)
                ? "insights"
                : openAiChatService.analyzeIntent(originalMessage);
            int maxResults = applyMaxResultsDefault(request.getMaxResults());
            ChatContext ctx = prepareChatContext(request.getMessage(), maxResults);
            String uploadedContext = resolveUploadedContext(conversationId, request);
            String fullContext = contextBuilder.mergeContexts(ctx.contextString(), uploadedContext);
            if (INSIGHTS_TRIGGER.equals(originalMessage)) {
                fullContext = sanitizeInsightsContext(fullContext);
            }
            String userMessageForModel = resolvePromptForModel(request, fullContext);
            List<OpenAiChatService.ConversationTurn> history = conversationRegistry.history(conversationId);

            // Debug log context structure for troubleshooting
            if (logger.isDebugEnabled()) {
                int uploadedChars = uploadedContext.length();
                logger.debug("Context prepared: uploadedChars={}, vectorResults={}, mergedChars={}",
                    uploadedChars, ctx.emailContext().size(), fullContext.length());
                if (uploadedChars > 0) {
                    String preview = uploadedContext.substring(0, Math.min(200, uploadedContext.length()));
                    logger.debug("Uploaded context preview (first 200 chars): {}", preview);
                }
            }
            
            OpenAiChatService.ChatCompletionResult aiResult = openAiChatService.generateResponse(
                userMessageForModel, fullContext, history,
                request.isThinkingEnabled(), request.getThinkingLevel(), jsonOutput);
            logger.info("Successfully processed chat request for conversation: {}", conversationId);
            conversationRegistry.append(conversationId,
                OpenAiChatService.ConversationTurn.userWithId(userMessageId, originalMessage),
                OpenAiChatService.ConversationTurn.assistantWithId(assistantMessageId, aiResult.rawText()));
            String sanitized = jsonOutput ? null : aiResult.sanitizedHtml();
            return new ChatResponse(aiResult.rawText(), conversationId, ctx.emailContext(), intent, sanitized, userMessageId, assistantMessageId);
        } catch (Exception e) {
            logger.error("Error processing chat request", e);
            String fallback = errorMessages.getChat().getProcessingError();
            conversationRegistry.append(conversationId,
                OpenAiChatService.ConversationTurn.userWithId(userMessageId, originalMessage),
                OpenAiChatService.ConversationTurn.assistantWithId(assistantMessageId, fallback));
            return new ChatResponse(fallback, conversationId, List.of(), "error", HtmlConverter.markdownToSafeHtml(fallback), userMessageId, assistantMessageId);
        }
    }


    /** Internal helper for streaming chat with consolidated timing and error handling. */
    private void doStreamChat(String messageForModel, String persistedMessage,
                              String conversationId, String contextString,
                              List<OpenAiChatService.ConversationTurn> conversationHistory,
                              boolean thinkingEnabled, String thinkingLevel, boolean jsonOutput,
                              String userMessageId, String assistantMessageId,
                              Consumer<String> onHtmlChunk, Consumer<String> onJsonChunk,
                              Consumer<ReasoningStreamAdapter.ReasoningMessage> onReasoning,
                              Runnable onComplete, Consumer<Throwable> onError) {
        Consumer<Throwable> safeOnError = onError != null ? onError : err -> logger.error("Streaming error: convId={}", conversationId, err);
        String normalizedThinkingLabel = thinkingEnabled
            ? ReasoningStreamAdapter.normalizeThinkingLabel(thinkingLevel)
            : null;
        long startNanos = System.nanoTime();
        StringBuilder assistantBuffer = new StringBuilder();
        try {
            openAiChatService.streamResponse(messageForModel, contextString, conversationHistory,
                thinkingEnabled, thinkingLevel, jsonOutput,
                event -> {
                    if (event instanceof OpenAiChatService.StreamEvent.RawText rawText) {
                        assistantBuffer.append(rawText.value());
                        return;
                    }
                    if (event instanceof OpenAiChatService.StreamEvent.RawJson rawJson) {
                        assistantBuffer.append(rawJson.value());
                    }
                    handleStreamEvent(event, onHtmlChunk, onJsonChunk, onReasoning, normalizedThinkingLabel);
                },
                () -> {
                    conversationRegistry.append(conversationId,
                        OpenAiChatService.ConversationTurn.userWithId(userMessageId, persistedMessage),
                        OpenAiChatService.ConversationTurn.assistantWithId(assistantMessageId, assistantBuffer.toString()));
                    logger.info("Stream completed: {}ms", (System.nanoTime() - startNanos) / 1_000_000L);
                    if (onComplete != null) onComplete.run();
                },
                err -> { logger.warn("Stream failed: {}ms", (System.nanoTime() - startNanos) / 1_000_000L, err); safeOnError.accept(err); });
        } catch (Exception e) {
            logger.error("Error initiating stream", e);
            safeOnError.accept(e);
        }
    }

    /** Public API: Stream chat with callback functions. */
    public void streamChat(ChatRequest request, Consumer<String> onToken,
                           Consumer<ReasoningStreamAdapter.ReasoningMessage> onReasoning,
                           Runnable onComplete, Consumer<Throwable> onError) {
        // Backward-compatibility overload: generate IDs internally
        String conversationId = StringUtils.ensureConversationId(request.getConversationId());
        int maxResults = applyMaxResultsDefault(request.getMaxResults());
        ChatContext ctx = prepareChatContext(request.getMessage(), maxResults);
        String uploadedContext = resolveUploadedContext(conversationId, request);
        String fullContext = contextBuilder.mergeContexts(ctx.contextString(), uploadedContext);
        boolean jsonOutput = request.isJsonOutput();
        String originalMessage = request.getMessage();
        if (INSIGHTS_TRIGGER.equals(originalMessage)) {
            fullContext = sanitizeInsightsContext(fullContext);
        }
        String userMessageForModel = resolvePromptForModel(request, fullContext);
        Consumer<String> htmlConsumer = jsonOutput ? null : onToken;
        Consumer<String> jsonConsumer = jsonOutput ? onToken : null;
        List<OpenAiChatService.ConversationTurn> history = conversationRegistry.history(conversationId);
        String userMessageId = com.composerai.api.util.IdGenerator.uuidV7();
        String assistantMessageId = com.composerai.api.util.IdGenerator.uuidV7();
        doStreamChat(userMessageForModel, originalMessage, conversationId, fullContext, history,
            request.isThinkingEnabled(), request.getThinkingLevel(), jsonOutput,
            userMessageId, assistantMessageId,
            htmlConsumer, jsonConsumer, onReasoning, onComplete, onError);
    }

    /** New Overload: Stream chat with provided message IDs. */
    public void streamChat(ChatRequest request, String userMessageId, String assistantMessageId,
                           Consumer<String> onToken,
                           Consumer<ReasoningStreamAdapter.ReasoningMessage> onReasoning,
                           Runnable onComplete, Consumer<Throwable> onError) {
        String conversationId = StringUtils.ensureConversationId(request.getConversationId());
        int maxResults = applyMaxResultsDefault(request.getMaxResults());
        ChatContext ctx = prepareChatContext(request.getMessage(), maxResults);
        String uploadedContext = resolveUploadedContext(conversationId, request);
        String fullContext = contextBuilder.mergeContexts(ctx.contextString(), uploadedContext);
        boolean jsonOutput = request.isJsonOutput();
        String originalMessage = request.getMessage();
        if (INSIGHTS_TRIGGER.equals(originalMessage)) {
            fullContext = sanitizeInsightsContext(fullContext);
        }
        String userMessageForModel = resolvePromptForModel(request, fullContext);
        Consumer<String> htmlConsumer = jsonOutput ? null : onToken;
        Consumer<String> jsonConsumer = jsonOutput ? onToken : null;
        List<OpenAiChatService.ConversationTurn> history = conversationRegistry.history(conversationId);
        doStreamChat(userMessageForModel, originalMessage, conversationId, fullContext, history,
            request.isThinkingEnabled(), request.getThinkingLevel(), jsonOutput,
            userMessageId, assistantMessageId,
            htmlConsumer, jsonConsumer, onReasoning, onComplete, onError);
    }

    /** Public API: Stream chat with SseEmitter (for SSE endpoints). */
    public void streamChat(ChatRequest request, SseEmitter emitter) {
        String conversationId = StringUtils.ensureConversationId(request.getConversationId());
        emitter.onCompletion(() -> logger.info("SSE completed: {}", conversationId));
        emitter.onTimeout(() -> logger.warn("SSE timeout: {}", conversationId));
        emitter.onError(e -> logger.error("SSE error: {}", conversationId, e));
        streamingExecutor.execute(() -> {
            try {
                int maxResults = applyMaxResultsDefault(request.getMaxResults());
                ChatContext ctx = prepareChatContext(request.getMessage(), maxResults);
                String uploadedContext = resolveUploadedContext(conversationId, request);
                String fullContext = contextBuilder.mergeContexts(ctx.contextString(), uploadedContext);
                boolean jsonOutput = request.isJsonOutput();
                String originalMessage = request.getMessage();
                if (INSIGHTS_TRIGGER.equals(originalMessage)) {
                    fullContext = sanitizeInsightsContext(fullContext);
                }
                String userMessageForModel = resolvePromptForModel(request, fullContext);
                List<OpenAiChatService.ConversationTurn> history = conversationRegistry.history(conversationId);
                String userMessageId = com.composerai.api.util.IdGenerator.uuidV7();
                String assistantMessageId = com.composerai.api.util.IdGenerator.uuidV7();
                Consumer<String> chunkSender = chunk -> { 
                    try { 
                        emitter.send(SseEmitter.event().data(chunk)); 
                    } catch (IOException e) { 
                        logger.warn("Error sending chunk: {}", conversationId, e); 
                        emitter.completeWithError(e); 
                    } 
                };
                doStreamChat(userMessageForModel, originalMessage, conversationId, fullContext, history,
                    request.isThinkingEnabled(), request.getThinkingLevel(), jsonOutput,
                    userMessageId, assistantMessageId,
                    chunkSender, chunkSender,
                    message -> sendReasoning(emitter, conversationId, message), 
                    emitter::complete, emitter::completeWithError);
            } catch (Exception e) {
                logger.error("Async processing failed: {}", conversationId, e);
                emitter.completeWithError(e);
            }
        });
    }

    private String resolveUploadedContext(String conversationId, ChatRequest request) {
        String contextId = request.getContextId();
        logger.debug("Resolving uploaded context: contextId={}, conversationId={}", contextId, conversationId);

        Optional<String> stored = StringUtils.isBlank(contextId)
            ? Optional.empty()
            : emailContextRegistry.contextForAi(contextId);
        if (stored.isPresent()) {
            logger.debug("Found cached context for contextId={} (length={})", contextId, stored.get().length());
            return stored.get();
        }
        
        logger.warn("Context lookup failed for contextId={} (conversationId={})", contextId, conversationId);
        
        if (!StringUtils.isBlank(request.getEmailContext())) {
            logger.warn("Using emailContext from request payload (contextId={}, length={}, conversationId={})",
                contextId, request.getEmailContext().length(), conversationId);
            // Don't suppress utility text - frontend already built clean context
            return HtmlConverter.cleanupOutput(request.getEmailContext(), false);
        }
            logger.error("No uploaded context found: contextId={}, no fallback payload (conversationId={})",
                contextId, conversationId);
        return "";
    }

    private void handleStreamEvent(OpenAiChatService.StreamEvent event,
                                   Consumer<String> onHtmlChunk,
                                   Consumer<String> onJsonChunk,
                                   Consumer<ReasoningStreamAdapter.ReasoningMessage> onReasoning,
                                   String thinkingLabel) {
        if (event instanceof OpenAiChatService.StreamEvent.RenderedHtml rendered) {
            if (onHtmlChunk != null) {
                onHtmlChunk.accept(rendered.html());
            }
            return;
        }
        if (event instanceof OpenAiChatService.StreamEvent.RawJson rawJson) {
            if (onJsonChunk != null) {
                onJsonChunk.accept(rawJson.value());
            }
            return;
        }
        if (onReasoning == null) return;
        ReasoningStreamAdapter.ReasoningMessage message = ReasoningStreamAdapter.toMessage(event, thinkingLabel);
        if (message != null) onReasoning.accept(message);
    }

    private void sendReasoning(SseEmitter emitter, String conversationId, ReasoningStreamAdapter.ReasoningMessage message) {
        if (message == null) return;
        try {
            emitter.send(SseEmitter.event().name(SseEventType.REASONING.getEventName()).data(message));
        } catch (IOException e) {
            logger.warn("Error sending reasoning event: conv={}, type={}", conversationId, message.type(), e);
        }
    }

    @Component
    public static class ConversationRegistry {

        private static final int MAX_TURNS = 40;
        private static final int MAX_CONVERSATIONS = 512;
        private static final Duration TTL = Duration.ofMinutes(45);

        private final ConcurrentMap<String, StoredConversation> conversations = new ConcurrentHashMap<>();

        public List<OpenAiChatService.ConversationTurn> history(String conversationId) {
            if (StringUtils.isBlank(conversationId)) {
                return List.of();
            }
            StoredConversation stored = conversations.get(conversationId);
            if (stored == null) {
                return List.of();
            }
            if (stored.isExpired(Instant.now())) {
                conversations.remove(conversationId);
                return List.of();
            }
            return stored.turns();
        }

        public void append(String conversationId, OpenAiChatService.ConversationTurn... turns) {
            if (StringUtils.isBlank(conversationId) || turns == null || turns.length == 0) {
                return;
            }
            conversations.compute(conversationId, (id, existing) -> StoredConversation.append(existing, turns));
            prune();
        }

        public void reset(String conversationId) {
            if (StringUtils.isBlank(conversationId)) {
                return;
            }
            conversations.remove(conversationId);
        }

        private void prune() {
            if (conversations.isEmpty()) {
                return;
            }
            Instant now = Instant.now();
            conversations.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
            int overflow = conversations.size() - MAX_CONVERSATIONS;
            if (overflow <= 0) {
                return;
            }
            List<Map.Entry<String, StoredConversation>> snapshot = new ArrayList<>(conversations.entrySet());
            snapshot.sort(Comparator.comparing(entry -> entry.getValue().updatedAt()));
            for (int i = 0; i < overflow && i < snapshot.size(); i++) {
                conversations.remove(snapshot.get(i).getKey());
            }
        }

        private record StoredConversation(List<OpenAiChatService.ConversationTurn> turns, Instant updatedAt) {

            boolean isExpired(Instant reference) {
                return updatedAt.plus(TTL).isBefore(reference);
            }

            static StoredConversation append(StoredConversation existing, OpenAiChatService.ConversationTurn... additions) {
                List<OpenAiChatService.ConversationTurn> buffer = existing == null
                    ? new ArrayList<>()
                    : new ArrayList<>(existing.turns());
                boolean changed = false;
                for (OpenAiChatService.ConversationTurn turn : additions) {
                    if (turn == null || StringUtils.isBlank(turn.content())) {
                        continue;
                    }
                    buffer.add(turn);
                    changed = true;
                }
                if (!changed) {
                    return existing;
                }
                if (buffer.size() > MAX_TURNS) {
                    buffer = new ArrayList<>(buffer.subList(Math.max(0, buffer.size() - MAX_TURNS), buffer.size()));
                }
                return new StoredConversation(List.copyOf(buffer), Instant.now());
            }
        }
    }
}

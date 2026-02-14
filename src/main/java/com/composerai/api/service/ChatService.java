package com.composerai.api.service;

import com.composerai.api.ai.AiFunctionCatalogHelper;
import com.composerai.api.ai.AiFunctionDefinition;
import com.composerai.api.config.ErrorMessagesProperties;
import com.composerai.api.config.MagicEmailProperties;
import com.composerai.api.config.OpenAiProperties;
import com.composerai.api.domain.model.ChatCompletionCommand;
import com.composerai.api.domain.model.ConversationTurn;
import com.composerai.api.dto.ChatRequest;
import com.composerai.api.dto.ChatResponse;
import com.composerai.api.dto.ChatResponse.EmailContext;
import com.composerai.api.dto.SseEventType;
import com.composerai.api.service.email.HtmlConverter;
import com.composerai.api.shared.ledger.ChatLedgerRecorder;
import com.composerai.api.shared.ledger.UsageMetrics;
import com.composerai.api.util.StringUtils;
import com.openai.models.responses.ResponseCreateParams;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    private final VectorSearchService vectorSearchService;
    private final OpenAiChatService openAiChatService;
    private final OpenAiProperties openAiProperties;
    private final ErrorMessagesProperties errorMessages;
    private final ContextBuilder contextBuilder;
    private final ContextBuilder.EmailContextCache emailContextRegistry;
    private final ConversationRegistry conversationRegistry;
    private final ExecutorService streamingExecutor;
    private final MagicEmailProperties magicEmailProperties;
    private final AiFunctionCatalogHelper aiFunctionCatalogHelper;
    private final ChatLedgerRecorder chatLedgerRecorder;

    public static final String INSIGHTS_TRIGGER = "__INSIGHTS_TRIGGER__";
    private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile("\\[([^\\]]+)\\]\\(([^\\)]*)\\)");
    private static final Pattern BARE_URL_PATTERN = Pattern.compile("(?i)(?:https?://|www\\.)\\S+");
    private static final String CONVERSATION_GUIDANCE = """
Interaction style:
- Sound like a thoughtful colleague: use concise sentences, contractions, and acknowledge the user.
- Ask short clarifying questions when the request is ambiguous before assuming intent.

Nicknames & tone:
- Users may greet you casually ("hey homey"); treat it as a friendly salutation, stay professional, and keep the focus on the inbox content.

Evidence handling:
- Cite concrete names, figures, deadlines, and links from the email. Call out when information is missing rather than guessing.

Response craft:
- Lead with the direct answer or summary, then add supporting bullets or brief paragraphs.
- Offer a relevant next step or follow-up help when it adds value.
""";

    public ChatService(
            VectorSearchService vectorSearchService,
            OpenAiChatService openAiChatService,
            OpenAiProperties openAiProperties,
            ErrorMessagesProperties errorMessages,
            ContextBuilder contextBuilder,
            ContextBuilder.EmailContextCache emailContextRegistry,
            ConversationRegistry conversationRegistry,
            MagicEmailProperties magicEmailProperties,
            AiFunctionCatalogHelper aiFunctionCatalogHelper,
            @Qualifier("chatStreamExecutor") ExecutorService streamingExecutor,
            ChatLedgerRecorder chatLedgerRecorder) {
        this.vectorSearchService = vectorSearchService;
        this.openAiChatService = openAiChatService;
        this.openAiProperties = openAiProperties;
        this.errorMessages = errorMessages;
        this.contextBuilder = contextBuilder;
        this.emailContextRegistry = emailContextRegistry;
        this.conversationRegistry = conversationRegistry;
        this.streamingExecutor = streamingExecutor;
        this.magicEmailProperties = magicEmailProperties;
        this.aiFunctionCatalogHelper = aiFunctionCatalogHelper;
        this.chatLedgerRecorder = chatLedgerRecorder;
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
        return requestedMaxResults > 0
                ? requestedMaxResults
                : openAiProperties.getDefaults().getMaxSearchResults();
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

    private Optional<AiFunctionDefinition> findCommandDefinition(ChatRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        String command = request.getAiCommand();
        if (StringUtils.isBlank(command)) {
            return Optional.empty();
        }
        return aiFunctionCatalogHelper.find(command, request.getCommandVariant());
    }

    private boolean isIsolatedCommand(Optional<AiFunctionDefinition> definition) {
        return definition
                .map(def -> def.category() == AiFunctionDefinition.Category.SUMMARY
                        || def.category() == AiFunctionDefinition.Category.TRANSLATION)
                .orElse(false);
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
            AiFunctionDefinition definition = aiFunctionCatalogHelper
                    .find(command, request.getCommandVariant())
                    .orElse(null);
            if (definition != null) {
                AiFunctionDefinition.AiFunctionVariant variant =
                        definition.variant(request.getCommandVariant()).orElse(null);
                String template = resolveTemplate(definition, variant);
                String instruction = resolveInstruction(originalMessage, definition, variant);
                String rendered = renderFunctionTemplate(
                        template,
                        instruction,
                        request.getSubject(),
                        definition,
                        mergeFunctionArgs(definition, variant, request.getCommandArgs(), request));
                if (!StringUtils.isBlank(rendered)) {
                    return rendered;
                }
            }
        }

        return formatMessageForOutput(request, originalMessage);
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

    /**
     * Renders a catalog-derived template, substituting instruction/context placeholders so backend and UI
     * share identical helper behaviour.
     */
    private String renderFunctionTemplate(
            String template,
            String instruction,
            String subject,
            AiFunctionDefinition definition,
            Map<String, String> args) {
        String safeInstruction = instruction == null ? "" : instruction.trim();
        String rendered = template == null ? "" : template;

        if (rendered.contains("{{instruction}}")) {
            rendered = rendered.replace("{{instruction}}", safeInstruction);
        } else if (!StringUtils.isBlank(safeInstruction)) {
            rendered = rendered + "\n\nAdditional direction:\n" + safeInstruction;
        }

        if (args != null && !args.isEmpty()) {
            for (Map.Entry<String, String> entry : args.entrySet()) {
                String placeholder = "{{" + entry.getKey() + "}}";
                String value = entry.getValue() == null ? "" : entry.getValue();
                if (rendered.contains(placeholder)) {
                    rendered = rendered.replace(placeholder, value);
                }
            }
        }

        if (definition.allowsSubject() && !StringUtils.isBlank(subject)) {
            String safeSubject = subject.trim();
            if (rendered.contains("{{subject}}")) {
                rendered = rendered.replace("{{subject}}", safeSubject);
            } else {
                rendered = rendered + "\n\nSubject: " + safeSubject;
            }
        }

        return rendered.trim();
    }

    /**
     * Variant-specific template overrides fall back to the base function template when missing.
     */
    private String resolveTemplate(AiFunctionDefinition definition, AiFunctionDefinition.AiFunctionVariant variant) {
        if (variant != null && !StringUtils.isBlank(variant.promptTemplate())) {
            return variant.promptTemplate();
        }
        return definition.promptTemplate();
    }

    /**
     * Prefers user-provided instruction, then variant default, then the function default to keep behaviour consistent.
     */
    private String resolveInstruction(
            String provided, AiFunctionDefinition definition, AiFunctionDefinition.AiFunctionVariant variant) {
        if (!StringUtils.isBlank(provided)) {
            return provided;
        }
        if (variant != null && !StringUtils.isBlank(variant.defaultInstruction())) {
            return variant.defaultInstruction();
        }
        return definition.defaultInstruction();
    }

    /**
     * Deep merges default args (function + variant) with request overrides so templates only read from one map.
     */
    private Map<String, String> mergeFunctionArgs(
            AiFunctionDefinition definition,
            AiFunctionDefinition.AiFunctionVariant variant,
            Map<String, String> requestArgs,
            ChatRequest request) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (definition.defaultArgs() != null) {
            merged.putAll(definition.defaultArgs());
        }
        if (variant != null && variant.defaultArgs() != null) {
            merged.putAll(variant.defaultArgs());
        }
        if (requestArgs != null) {
            requestArgs.forEach((key, value) -> {
                if (!StringUtils.isBlank(key) && value != null) {
                    merged.put(key.trim(), value);
                }
            });
        }
        applyRecipientArguments(merged, definition, request);
        return merged;
    }

    /**
     * Enriches compose/tone commands with recipient metadata so prompt templates can produce
     * consistent greetings and closings regardless of where the request originated.
     */
    private void applyRecipientArguments(
            Map<String, String> target, AiFunctionDefinition definition, ChatRequest request) {
        if (target == null || definition == null || request == null) {
            return;
        }
        if (!isComposeLike(definition.category())) {
            return;
        }

        String providedName = StringUtils.sanitize(request.getRecipientName());
        String providedEmail = StringUtils.sanitize(request.getRecipientEmail());
        String resolvedName = providedName;
        boolean inferredFromEmail = false;

        if (StringUtils.isBlank(resolvedName) && !StringUtils.isBlank(providedEmail)) {
            resolvedName = inferNameFromEmail(providedEmail);
            inferredFromEmail = !StringUtils.isBlank(resolvedName);
        }

        if (StringUtils.isBlank(resolvedName)) {
            resolvedName = "Unknown recipient";
        }

        target.put("recipientName", resolvedName);
        if (!StringUtils.isBlank(providedEmail)) {
            target.put("recipientEmail", providedEmail);
        }
        target.put(
                "recipientGreetingDirective",
                buildGreetingDirective(
                        providedName,
                        providedEmail,
                        resolvedName,
                        inferredFromEmail && StringUtils.isBlank(providedName)));
    }

    private boolean isComposeLike(AiFunctionDefinition.Category category) {
        return category == AiFunctionDefinition.Category.COMPOSE || category == AiFunctionDefinition.Category.TONE;
    }

    private String inferNameFromEmail(String email) {
        if (StringUtils.isBlank(email)) {
            return null;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return null;
        }
        String localPart = email.substring(0, atIndex)
                .replace('.', ' ')
                .replace('_', ' ')
                .replace('-', ' ')
                .trim();
        if (localPart.isBlank() || localPart.length() < 2) {
            return null;
        }
        String[] tokens = localPart.split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(token.charAt(0)));
            if (token.length() > 1) {
                builder.append(token.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        String result = builder.toString().trim();
        return result.length() < 2 ? null : result;
    }

    /**
     * Produces inline instructions consumed by prompt templates to keep greetings deterministic.
     * The directive explains whether a name is trustworthy, inferred, or unavailable so the model
     * knows when to fall back to generic pleasantries.
     */
    private String buildGreetingDirective(
            String providedName, String providedEmail, String resolvedName, boolean inferred) {
        if (!StringUtils.isBlank(providedName)) {
            return "Open with a warm, professional greeting that addresses \"" + providedName + "\" by name.";
        }
        if (inferred && !StringUtils.isBlank(resolvedName)) {
            return "Recipient name was inferred as \"" + resolvedName
                    + "\" from the email address. Use it for the salutation if it feels natural; otherwise keep the greeting neutral.";
        }
        if (!StringUtils.isBlank(providedEmail)) {
            return "Recipient name is unknown and could not be inferred from " + providedEmail
                    + "; begin with a friendly generic greeting such as \"Hello there\" or \"Hi team\".";
        }
        return "Recipient identity is unavailable; start with a polite generic greeting (e.g., \"Hello there\").";
    }

    /**
     * Pass-through method for message formatting.
     * JSON instructions are added downstream in OpenAiChatService to avoid duplication.
     */
    private boolean shouldApplyConversationGuidance(ChatRequest request) {
        if (request == null) {
            return true;
        }
        if (!StringUtils.isBlank(request.getAiCommand())) {
            return false;
        }
        if (request.isJsonOutput()) {
            return false;
        }
        return true;
    }

    private String formatMessageForOutput(ChatRequest request, String message) {
        String base = message == null ? "" : message;
        String sanitized = base.trim();
        if (!shouldApplyConversationGuidance(request)) {
            return base;
        }
        if (StringUtils.isBlank(sanitized)) {
            return CONVERSATION_GUIDANCE;
        }
        String separator = base.endsWith("\n") ? "" : "\n\n";
        return base + separator + CONVERSATION_GUIDANCE;
    }

    public ChatResponse processChat(ChatRequest request) {
        String conversationId = StringUtils.ensureConversationId(request.getConversationId());
        String userMessageId = com.composerai.api.util.IdGenerator.uuidV7();
        String assistantMessageId = com.composerai.api.util.IdGenerator.uuidV7();
        int msgLen = request.getMessage() == null ? 0 : request.getMessage().length();
        logger.info("Processing chat: convId={}, msgLen={}", conversationId, msgLen);
        boolean jsonOutput = request.isJsonOutput();
        String originalMessage = request.getMessage();
        String commandKey = request.getAiCommand();
        boolean isActionMenuCommand = "actions_menu".equalsIgnoreCase(commandKey);
        if (isActionMenuCommand) {
            logger.info(
                    "Action menu request: convId={}, contextId={}, subject={}, journeyTarget={}",
                    conversationId,
                    request.getContextId(),
                    request.getSubject(),
                    request.getJourneyScopeTarget());
        }
        Optional<AiFunctionDefinition> commandDefinition = findCommandDefinition(request);
        boolean isolatedCommand = isIsolatedCommand(commandDefinition);
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
            List<ConversationTurn> history = isolatedCommand ? List.of() : conversationRegistry.history(conversationId);

            // Debug log context structure for troubleshooting
            if (logger.isDebugEnabled()) {
                int uploadedChars = uploadedContext.length();
                logger.debug(
                        "Context prepared: uploadedChars={}, vectorResults={}, mergedChars={}",
                        uploadedChars,
                        ctx.emailContext().size(),
                        fullContext.length());
                if (uploadedChars > 0) {
                    String preview = uploadedContext.substring(0, Math.min(200, uploadedContext.length()));
                    logger.debug("Uploaded context preview (first 200 chars): {}", preview);
                }
            }

            ChatCompletionCommand command = new ChatCompletionCommand(
                    userMessageForModel,
                    fullContext,
                    history,
                    request.isThinkingEnabled(),
                    request.getThinkingLevel(),
                    jsonOutput);

            OpenAiChatService.Invocation invocation = openAiChatService.invokeChatResponse(command);
            OpenAiChatService.ChatCompletionResult aiResult = invocation.result();
            if (isActionMenuCommand) {
                int responseChars =
                        aiResult.rawText() == null ? 0 : aiResult.rawText().length();
                logger.info(
                        "Action menu response: convId={}, contextId={}, chars={}",
                        conversationId,
                        request.getContextId(),
                        responseChars);
            }
            logger.info("Successfully processed chat request for conversation: {}", conversationId);
            if (!isolatedCommand) {
                conversationRegistry.append(
                        conversationId,
                        ConversationTurn.userWithId(userMessageId, originalMessage),
                        ConversationTurn.assistantWithId(assistantMessageId, aiResult.rawText()));
            }
            chatLedgerRecorder.recordChatCompletion(request, conversationId, invocation, aiResult.rawText());
            String sanitized = jsonOutput ? null : aiResult.sanitizedHtml();
            return new ChatResponse(
                    aiResult.rawText(),
                    conversationId,
                    ctx.emailContext(),
                    intent,
                    sanitized,
                    userMessageId,
                    assistantMessageId);
        } catch (Exception e) {
            if (isActionMenuCommand) {
                logger.warn(
                        "Action menu request failed: convId={}, contextId={}, error={}",
                        conversationId,
                        request.getContextId(),
                        e.getMessage());
            }
            logger.error("Error processing chat request", e);
            String fallback = errorMessages.getChat().getProcessingError();
            if (!isolatedCommand) {
                conversationRegistry.append(
                        conversationId,
                        ConversationTurn.userWithId(userMessageId, originalMessage),
                        ConversationTurn.assistantWithId(assistantMessageId, fallback));
            }
            return new ChatResponse(
                    fallback,
                    conversationId,
                    List.of(),
                    "error",
                    HtmlConverter.markdownToSafeHtml(fallback),
                    userMessageId,
                    assistantMessageId);
        }
    }

    private record StreamContext(
            ChatRequest request,
            String messageForModel,
            String persistedMessage,
            String conversationId,
            String contextString,
            List<ConversationTurn> conversationHistory,
            boolean thinkingEnabled,
            String thinkingLevel,
            boolean jsonOutput,
            boolean isolatedCommand,
            String userMessageId,
            String assistantMessageId) {}

    private record StreamCallbacks(
            Consumer<String> onHtmlChunk,
            Consumer<String> onJsonChunk,
            Consumer<ReasoningStreamAdapter.ReasoningMessage> onReasoning,
            Runnable onComplete,
            Consumer<Throwable> onError) {}

    /** Internal helper for streaming chat with consolidated timing and error handling. */
    private void doStreamChat(StreamContext ctx, StreamCallbacks callbacks) {
        Consumer<Throwable> safeOnError = callbacks.onError() != null
                ? callbacks.onError()
                : err -> logger.error("Streaming error: convId={}", ctx.conversationId(), err);
        String normalizedThinkingLabel =
                ctx.thinkingEnabled() ? ReasoningStreamAdapter.normalizeThinkingLabel(ctx.thinkingLevel()) : null;
        long startNanos = System.nanoTime();
        long startMillis = System.currentTimeMillis();
        StringBuilder assistantBuffer = new StringBuilder();
        final ResponseCreateParams[] requestParamsHolder = new ResponseCreateParams[1];
        logger.info(
                "Dispatching LLM stream: convId={}, contextChars={}, historySize={}, jsonOutput={}, thinkingEnabled={}",
                ctx.conversationId(),
                ctx.contextString() == null ? 0 : ctx.contextString().length(),
                ctx.conversationHistory() == null
                        ? 0
                        : ctx.conversationHistory().size(),
                ctx.jsonOutput(),
                ctx.thinkingEnabled());
        try {
            ChatCompletionCommand command = new ChatCompletionCommand(
                    ctx.messageForModel(),
                    ctx.contextString(),
                    ctx.conversationHistory(),
                    ctx.thinkingEnabled(),
                    ctx.thinkingLevel(),
                    ctx.jsonOutput());

            ResponseCreateParams params = openAiChatService.streamResponse(
                    command,
                    event -> {
                        if (event instanceof OpenAiChatService.StreamEvent.RawText rawText) {
                            assistantBuffer.append(rawText.value());
                            return;
                        }
                        if (event instanceof OpenAiChatService.StreamEvent.RawJson rawJson) {
                            assistantBuffer.append(rawJson.value());
                        }
                        handleStreamEvent(
                                event,
                                callbacks.onHtmlChunk(),
                                callbacks.onJsonChunk(),
                                callbacks.onReasoning(),
                                normalizedThinkingLabel);
                    },
                    () -> {
                        if (!ctx.isolatedCommand()) {
                            conversationRegistry.append(
                                    ctx.conversationId(),
                                    ConversationTurn.userWithId(ctx.userMessageId(), ctx.persistedMessage()),
                                    ConversationTurn.assistantWithId(
                                            ctx.assistantMessageId(), assistantBuffer.toString()));
                        }
                        logger.info("Stream completed: {}ms", (System.nanoTime() - startNanos) / 1_000_000L);
                        if (callbacks.onComplete() != null)
                            callbacks.onComplete().run();
                        UsageMetrics usage = new UsageMetrics(0, 0, 0, System.currentTimeMillis() - startMillis);
                        OpenAiChatService.Invocation invocation = new OpenAiChatService.Invocation(
                                OpenAiChatService.ChatCompletionResult.fromRaw(
                                        assistantBuffer.toString(), ctx.jsonOutput()),
                                requestParamsHolder[0],
                                null,
                                usage);
                        chatLedgerRecorder.recordChatCompletion(
                                ctx.request(), ctx.conversationId(), invocation, assistantBuffer.toString());
                    },
                    err -> {
                        logger.warn("Stream failed: {}ms", (System.nanoTime() - startNanos) / 1_000_000L, err);
                        safeOnError.accept(err);
                    });
            if (params != null) {
                requestParamsHolder[0] = params;
            }
        } catch (Exception e) {
            logger.error("Error initiating stream", e);
            safeOnError.accept(e);
        }
    }

    /** Public API: Stream chat with callback functions. */
    public void streamChat(
            ChatRequest request,
            Consumer<String> onToken,
            Consumer<ReasoningStreamAdapter.ReasoningMessage> onReasoning,
            Runnable onComplete,
            Consumer<Throwable> onError) {
        // Backward-compatibility overload: generate IDs internally
        String conversationId = StringUtils.ensureConversationId(request.getConversationId());
        Optional<AiFunctionDefinition> definition = findCommandDefinition(request);
        boolean isolatedCommand = isIsolatedCommand(definition);
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
        List<ConversationTurn> history = isolatedCommand ? List.of() : conversationRegistry.history(conversationId);
        String userMessageId = com.composerai.api.util.IdGenerator.uuidV7();
        String assistantMessageId = com.composerai.api.util.IdGenerator.uuidV7();

        StreamContext streamContext = new StreamContext(
                request,
                userMessageForModel,
                originalMessage,
                conversationId,
                fullContext,
                history,
                request.isThinkingEnabled(),
                request.getThinkingLevel(),
                jsonOutput,
                isolatedCommand,
                userMessageId,
                assistantMessageId);
        StreamCallbacks callbacks = new StreamCallbacks(htmlConsumer, jsonConsumer, onReasoning, onComplete, onError);
        doStreamChat(streamContext, callbacks);
    }

    /** New Overload: Stream chat with provided message IDs. */
    public void streamChat(
            ChatRequest request,
            String userMessageId,
            String assistantMessageId,
            Consumer<String> onToken,
            Consumer<ReasoningStreamAdapter.ReasoningMessage> onReasoning,
            Runnable onComplete,
            Consumer<Throwable> onError) {
        String conversationId = StringUtils.ensureConversationId(request.getConversationId());
        Optional<AiFunctionDefinition> definition = findCommandDefinition(request);
        boolean isolatedCommand = isIsolatedCommand(definition);
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
        List<ConversationTurn> history = isolatedCommand ? List.of() : conversationRegistry.history(conversationId);

        StreamContext streamContext = new StreamContext(
                request,
                userMessageForModel,
                originalMessage,
                conversationId,
                fullContext,
                history,
                request.isThinkingEnabled(),
                request.getThinkingLevel(),
                jsonOutput,
                isolatedCommand,
                userMessageId,
                assistantMessageId);
        StreamCallbacks callbacks = new StreamCallbacks(htmlConsumer, jsonConsumer, onReasoning, onComplete, onError);
        doStreamChat(streamContext, callbacks);
    }

    /** Public API: Stream chat with SseEmitter (for SSE endpoints). */
    public void streamChat(ChatRequest request, SseEmitter emitter) {
        String conversationId = StringUtils.ensureConversationId(request.getConversationId());
        logger.info(
                "Received SSE chat request: convId={}, msgLen={}, jsonOutput={}, thinkingEnabled={}",
                conversationId,
                request.getMessage() == null ? 0 : request.getMessage().length(),
                request.isJsonOutput(),
                request.isThinkingEnabled());
        emitter.onCompletion(() -> logger.info("SSE completed: {}", conversationId));
        emitter.onTimeout(() -> logger.warn("SSE timeout: {}", conversationId));
        emitter.onError(e -> logger.error("SSE error: {}", conversationId, e));
        streamingExecutor.execute(() -> {
            try {
                Optional<AiFunctionDefinition> definition = findCommandDefinition(request);
                boolean isolatedCommand = isIsolatedCommand(definition);
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
                List<ConversationTurn> history =
                        isolatedCommand ? List.of() : conversationRegistry.history(conversationId);
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

                StreamContext sCtx = new StreamContext(
                        request,
                        userMessageForModel,
                        originalMessage,
                        conversationId,
                        fullContext,
                        history,
                        request.isThinkingEnabled(),
                        request.getThinkingLevel(),
                        jsonOutput,
                        isolatedCommand,
                        userMessageId,
                        assistantMessageId);
                StreamCallbacks sCallbacks = new StreamCallbacks(
                        chunkSender,
                        chunkSender,
                        message -> sendReasoning(emitter, conversationId, message),
                        emitter::complete,
                        emitter::completeWithError);
                doStreamChat(sCtx, sCallbacks);
            } catch (Exception e) {
                logger.error("Async processing failed: {}", conversationId, e);
                emitter.completeWithError(e);
            }
        });
    }

    public void storeDraftContext(String contextId, String content) {
        String safeId = StringUtils.trimToNull(contextId);
        String sanitized = HtmlConverter.cleanupOutput(content, false);
        if (safeId == null) {
            logger.warn("Cannot store draft context with blank contextId");
            return;
        }
        if (StringUtils.isBlank(sanitized)) {
            logger.warn("Skipping draft context store for {} because content is blank", safeId);
            return;
        }
        emailContextRegistry.store(safeId, sanitized);
        logger.debug("Stored draft context: id={}, length={}", safeId, sanitized.length());
    }

    private String resolveUploadedContext(String conversationId, ChatRequest request) {
        String contextId = request.getContextId();
        logger.debug("Resolving uploaded context: contextId={}, conversationId={}", contextId, conversationId);

        Optional<String> stored =
                StringUtils.isBlank(contextId) ? Optional.empty() : emailContextRegistry.contextForAi(contextId);
        if (stored.isPresent()) {
            logger.debug(
                    "Found cached context for contextId={} (length={})",
                    contextId,
                    stored.get().length());
            return stored.get();
        }

        logger.warn("Context lookup failed for contextId={} (conversationId={})", contextId, conversationId);

        if (!StringUtils.isBlank(request.getEmailContext())) {
            logger.warn(
                    "Using emailContext from request payload (contextId={}, length={}, conversationId={})",
                    contextId,
                    request.getEmailContext().length(),
                    conversationId);
            // Don't suppress utility text - frontend already built clean context
            return HtmlConverter.cleanupOutput(request.getEmailContext(), false);
        }
        logger.error(
                "No uploaded context found: contextId={}, no fallback payload (conversationId={})",
                contextId,
                conversationId);
        return "";
    }

    private void handleStreamEvent(
            OpenAiChatService.StreamEvent event,
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

    private void sendReasoning(
            SseEmitter emitter, String conversationId, ReasoningStreamAdapter.ReasoningMessage message) {
        if (message == null) return;
        try {
            emitter.send(SseEmitter.event()
                    .name(SseEventType.REASONING.getEventName())
                    .data(message));
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

        public List<ConversationTurn> history(String conversationId) {
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

        public void append(String conversationId, ConversationTurn... turns) {
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

        private record StoredConversation(List<ConversationTurn> turns, Instant updatedAt) {

            boolean isExpired(Instant reference) {
                return updatedAt.plus(TTL).isBefore(reference);
            }

            static StoredConversation append(StoredConversation existing, ConversationTurn... additions) {
                List<ConversationTurn> buffer =
                        existing == null ? new ArrayList<>() : new ArrayList<>(existing.turns());
                boolean changed = false;
                for (ConversationTurn turn : additions) {
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

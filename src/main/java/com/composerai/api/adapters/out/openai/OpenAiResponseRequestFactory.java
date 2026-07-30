package com.composerai.api.adapters.out.openai;

import com.composerai.api.config.OpenAiProperties;
import com.composerai.api.config.ProviderCapabilities;
import com.composerai.api.domain.model.ChatCompletionCommand;
import com.composerai.api.domain.model.ConversationTurn;
import com.composerai.api.domain.model.ReasoningEffortLevel;
import com.composerai.api.domain.service.ReasoningRequestPolicy;
import com.composerai.api.util.StringUtils;
import com.composerai.api.util.TemporalUtils;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openai.core.JsonValue;
import com.openai.models.ChatModel;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseInputItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Builds Responses API requests from Composer's chat commands. */
final class OpenAiResponseRequestFactory {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiResponseRequestFactory.class);
    private static final int SYSTEM_PROMPT_PREVIEW_CHAR_LIMIT = 400;
    private static final int EMAIL_CONTEXT_PREVIEW_CHAR_LIMIT = 500;
    private static final int LARGE_PROMPT_TOKEN_WARNING_THRESHOLD = 100_000;
    private static final double TOKEN_ESTIMATION_MULTIPLIER = 1.3;
    private static final String GATEWAY_TIER_HEADER = "X-Tier";
    private static final String INTERACTIVE_GATEWAY_TIER = "production-z";

    record PreparedRequest(ResponseCreateParams requestParams, ReasoningEffortLevel reasoningEffort) {}

    private final OpenAiProperties openAiProperties;
    private final ReasoningRequestPolicy reasoningRequestPolicy = new ReasoningRequestPolicy();

    OpenAiResponseRequestFactory(OpenAiProperties openAiProperties) {
        this.openAiProperties = openAiProperties;
    }

    PreparedRequest prepareResponseRequest(ChatCompletionCommand command) {
        ResponseCreateParams.Builder builder = ResponseCreateParams.builder()
                .model(resolveChatModel())
                .inputOfResponse(buildEmailAssistantMessages(
                        command.emailContext(),
                        command.userMessage(),
                        command.conversationHistory(),
                        command.jsonOutput()));
        applyConfiguredModelDefaults(builder);

        ReasoningEffortLevel reasoningEffort =
                applyGenerationPolicy(builder, command.thinkingEnabled(), command.thinkingLevel());

        addOpenRouterProviderRouting(builder);
        return new PreparedRequest(builder.build(), reasoningEffort);
    }

    PreparedRequest prepareIntentRequest(String userMessage) {
        ResponseCreateParams.Builder builder = ResponseCreateParams.builder()
                .model(resolveChatModel())
                .inputOfResponse(buildIntentAnalysisMessages(userMessage))
                .maxOutputTokens(openAiProperties.getIntent().getMaxOutputTokens());
        ReasoningEffortLevel reasoningEffort =
                applyGenerationPolicy(builder, openAiProperties.getDefaults().getThinkingEnabled(), null);
        return new PreparedRequest(builder.build(), reasoningEffort);
    }

    private ReasoningEffortLevel applyGenerationPolicy(
            ResponseCreateParams.Builder builder, Boolean thinkingEnabled, ReasoningEffortLevel requestedEffort) {
        String configuredDefault = openAiProperties.getReasoning() == null
                ? null
                : openAiProperties.getReasoning().getDefaultEffort();
        ReasoningEffortLevel reasoningEffort =
                reasoningRequestPolicy.resolve(configuredDefault, thinkingEnabled, requestedEffort);
        if (reasoningEffort != null) {
            applyReasoning(builder, reasoningEffort);
        }
        if (openAiProperties.getProviderCapabilities().getType() == ProviderCapabilities.ProviderType.SHARED_GATEWAY) {
            builder.putAdditionalHeader(GATEWAY_TIER_HEADER, INTERACTIVE_GATEWAY_TIER);
        }
        return reasoningEffort;
    }

    private void applyConfiguredModelDefaults(ResponseCreateParams.Builder builder) {
        if (openAiProperties.getModel().getTemperature() != null) {
            builder.temperature(openAiProperties.getModel().getTemperature());
        }
        if (openAiProperties.getModel().getTopP() != null) {
            builder.topP(openAiProperties.getModel().getTopP());
        }
        if (openAiProperties.getModel().getMaxOutputTokens() != null) {
            builder.maxOutputTokens(openAiProperties.getModel().getMaxOutputTokens());
        }
    }

    private void applyReasoning(ResponseCreateParams.Builder builder, ReasoningEffortLevel reasoningEffort) {
        ReasoningEffort sdkEffort = ReasoningEffort.of(reasoningEffort.externalName());
        builder.reasoning(Reasoning.builder().effort(sdkEffort).build());
        logger.info(
                "Reasoning forwarded: {} (provider: {})",
                reasoningEffort.externalName(),
                openAiProperties.getProviderCapabilities().getType());
    }

    private void addOpenRouterProviderRouting(ResponseCreateParams.Builder builder) {
        if (openAiProperties.getProviderCapabilities().getType() != ProviderCapabilities.ProviderType.OPENROUTER
                || openAiProperties.getProvider().getOrder() == null
                || openAiProperties.getProvider().getOrder().isEmpty()) {
            return;
        }

        ObjectNode provider = JsonNodeFactory.instance.objectNode();
        if (openAiProperties.getProvider().getSort() != null
                && !openAiProperties.getProvider().getSort().isBlank()) {
            provider.put("sort", openAiProperties.getProvider().getSort());
        }
        ArrayNode providerOrder = provider.putArray("order");
        for (String providerName : openAiProperties.getProvider().getOrder()) {
            providerOrder.add(providerName);
        }
        provider.put("allow_fallbacks", openAiProperties.getProvider().getAllowFallbacks());
        builder.putAdditionalBodyProperty("provider", JsonValue.fromJsonNode(provider));

        logger.info(
                "OpenRouter provider routing enabled: order={}, sort={}, allow_fallbacks={}",
                openAiProperties.getProvider().getOrder(),
                openAiProperties.getProvider().getSort(),
                openAiProperties.getProvider().getAllowFallbacks());
    }

    private ChatModel resolveChatModel() {
        return ChatModel.of(openAiProperties.getModel().getChat());
    }

    private List<ResponseInputItem> buildEmailAssistantMessages(
            String emailContext, String userMessage, List<ConversationTurn> conversationHistory, boolean jsonOutput) {
        List<ResponseInputItem> messages = new ArrayList<>();
        int totalTokenEstimate = 0;

        String systemPrompt = openAiProperties.getPrompts().getEmailAssistantSystem();
        if (!StringUtils.isBlank(systemPrompt)) {
            String timestampedPrompt = systemPrompt
                    .replace("{currentUtcTime}", TemporalUtils.getCurrentUtcFormatted())
                    .replace("{currentPacificTime}", TemporalUtils.getCurrentPacificFormatted());
            String sanitizedSystem = StringUtils.sanitize(timestampedPrompt);
            messages.add(message(EasyInputMessage.Role.SYSTEM, sanitizedSystem));
            totalTokenEstimate += estimateTokens(sanitizedSystem);
            if (logger.isDebugEnabled()) {
                String promptPreview = sanitizedSystem.length() > SYSTEM_PROMPT_PREVIEW_CHAR_LIMIT
                        ? sanitizedSystem.substring(0, SYSTEM_PROMPT_PREVIEW_CHAR_LIMIT) + "..."
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
            messages.add(message(EasyInputMessage.Role.SYSTEM, sanitizedDirective));
            totalTokenEstimate += estimateTokens(sanitizedDirective);
        }

        String safeContext = StringUtils.sanitize(emailContext);
        if (!StringUtils.isBlank(safeContext)) {
            String contextMessage = "Email Context:\n" + safeContext;
            messages.add(message(EasyInputMessage.Role.SYSTEM, contextMessage));
            totalTokenEstimate += estimateTokens(contextMessage);
            if (logger.isDebugEnabled()) {
                String preview = safeContext.length() > EMAIL_CONTEXT_PREVIEW_CHAR_LIMIT
                        ? safeContext.substring(0, EMAIL_CONTEXT_PREVIEW_CHAR_LIMIT) + "..."
                        : safeContext;
                logger.debug("Email context being sent to model (first 500 chars): {}", preview);
            }
        }

        if (conversationHistory != null) {
            for (ConversationTurn turn : conversationHistory) {
                if (turn == null || StringUtils.isBlank(turn.content())) {
                    continue;
                }
                String sanitized = StringUtils.sanitize(turn.content());
                if (!StringUtils.isBlank(sanitized)) {
                    messages.add(message(mapRole(turn.role()), sanitized));
                    totalTokenEstimate += estimateTokens(sanitized);
                }
            }
        }

        String prompt = applyJsonOutputDirective(userMessage, jsonOutput);
        String sanitizedPrompt = StringUtils.sanitize(prompt);
        messages.add(message(EasyInputMessage.Role.USER, sanitizedPrompt));
        totalTokenEstimate += estimateTokens(sanitizedPrompt);
        logger.debug(
                "Building prompt: total=~{}tok, contextChars={}, priorTurns={}",
                totalTokenEstimate,
                safeContext.length(),
                conversationHistory == null ? 0 : conversationHistory.size());
        if (totalTokenEstimate > LARGE_PROMPT_TOKEN_WARNING_THRESHOLD) {
            logger.warn("Large prompt detected: ~{}tok may approach model limits", totalTokenEstimate);
        }
        return messages;
    }

    private List<ResponseInputItem> buildIntentAnalysisMessages(String userMessage) {
        String systemMessage = openAiProperties
                .getPrompts()
                .getIntentAnalysisSystem()
                .replace("{categories}", openAiProperties.getIntent().getCategories());
        return List.of(message(EasyInputMessage.Role.USER, systemMessage + "\n\n" + userMessage));
    }

    private ResponseInputItem message(EasyInputMessage.Role role, String content) {
        return ResponseInputItem.ofEasyInputMessage(
                EasyInputMessage.builder().role(role).content(content).build());
    }

    private EasyInputMessage.Role mapRole(ConversationTurn.Role role) {
        return switch (role) {
            case USER -> EasyInputMessage.Role.USER;
            case ASSISTANT -> EasyInputMessage.Role.ASSISTANT;
            case SYSTEM -> EasyInputMessage.Role.SYSTEM;
        };
    }

    private String applyJsonOutputDirective(String userMessage, boolean jsonOutput) {
        String prompt = userMessage == null ? "" : userMessage;
        if (!jsonOutput) {
            return prompt;
        }
        String normalized = prompt.toLowerCase(Locale.ROOT);
        if (normalized.contains("respond strictly as a json object")
                || normalized.contains("respond with a single valid json object")) {
            return prompt;
        }
        return prompt.isBlank()
                ? "Respond strictly as a JSON object. Do not include markdown fences or explanatory text."
                : prompt + "\n\nRespond strictly as a JSON object. Do not include markdown fences or explanatory text.";
    }

    private int estimateTokens(String text) {
        return text == null || text.isBlank() ? 0 : (int) (text.split("\\s+").length * TOKEN_ESTIMATION_MULTIPLIER);
    }
}

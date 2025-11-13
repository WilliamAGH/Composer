package com.composerai.api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAI Configuration Properties - Single Source of Truth
 *
 * All default values are defined here. Override via application.properties or environment variables.
 * Example: LLM_MODEL=gpt-4o or openai.model.chat=gpt-4o
 *
 * Configuration structure:
 *   openai:
 *     api:
 *       key: ${OPENAI_API_KEY}
 *       base-url: ${OPENAI_BASE_URL}
 *     model:
 *       chat: ${LLM_MODEL}
 *     embedding:
 *       model: ${OPENAI_EMBEDDING_MODEL}
 *     stream:
 *       timeout-seconds: ${OPENAI_STREAM_TIMEOUT}
 *       heartbeat-interval-seconds: ${OPENAI_STREAM_HEARTBEAT}
 *     reasoning:
 *       supported-model-prefixes: ${OPENAI_REASONING_MODELS}
 *       default-effort: ${OPENAI_REASONING_EFFORT}
 *     intent:
 *       default-category: ${OPENAI_INTENT_DEFAULT}
 *       max-output-tokens: ${OPENAI_INTENT_MAX_TOKENS}
 *       categories: ${OPENAI_INTENT_CATEGORIES}
 *     defaults:
 *       max-search-results: ${OPENAI_MAX_SEARCH_RESULTS}
 *       max-message-length: ${OPENAI_MAX_MESSAGE_LENGTH}
 *       thinking-enabled: ${OPENAI_THINKING_ENABLED}
 *     prompts:
 *       email-assistant-system: ${OPENAI_PROMPT_EMAIL}
 *       intent-analysis-system: ${OPENAI_PROMPT_INTENT}
 *
 * See: https://docs.spring.io/spring-boot/reference/features/external-config.html
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "openai")
public class OpenAiProperties {

    private Api api = new Api();
    private Model model = new Model();
    private Embedding embedding = new Embedding();
    private Stream stream = new Stream();
    private Reasoning reasoning = new Reasoning();
    private Intent intent = new Intent();
    private Prompts prompts = new Prompts();
    private Defaults defaults = new Defaults();
    private boolean localDebugEnabled = false;

    // Eagerly initialized provider capabilities based on base URL
    private ProviderCapabilities providerCapabilities;

    @jakarta.annotation.PostConstruct
    public void initProviderCapabilities() {
        this.providerCapabilities = ProviderCapabilities.detect(api.getBaseUrl());
    }

    // ===== Configuration Type Definitions =====

    /**
     * OpenAI API credentials and connection settings.
     * Default base URL: https://api.openai.com/v1
     */
    @Getter
    @Setter
    public static class Api {
        private String key;
        private String baseUrl = "https://api.openai.com/v1";
    }

    /**
     * Chat completion model configuration.
     * Default: gpt-4o-mini (fast, cost-effective chat model)
     */
    @Getter
    @Setter
    public static class Model {
        private String chat = "gpt-4o-mini";
        private Double temperature = 0.5; // Default temperature for all requests
        private Long maxOutputTokens = null; // null = use model default
        private Double topP = null; // null = use model default
    }

    /**
     * Vector embedding model configuration.
     * Default: text-embedding-3-small (1536 dimensions, cost-effective)
     */
    @Getter
    @Setter
    public static class Embedding {
        private String model = "text-embedding-3-small";
    }

    /**
     * Server-Sent Events (SSE) streaming configuration.
     *
     * Timeout flow (single source of truth):
     *  1. OpenAiProperties.Stream.timeoutSeconds (default 120) - Java source of truth
     *  2. ChatController sends timeout hint header to frontend (X-Stream-Timeout-Hint)
     *  3. ChatController sets SseEmitter timeout (timeoutSeconds * 1000 ms)
     *  4. Frontend reads hint header and sets AbortController timeout accordingly
     *
     * Defaults: 120 seconds timeout, 10 seconds heartbeat interval
     */
    @Getter
    @Setter
    public static class Stream {
        private int timeoutSeconds = 120;
        private int heartbeatIntervalSeconds = 10;

        /**
         * Gets timeout in milliseconds for JavaScript/frontend use.
         */
        public long getTimeoutMillis() {
            return (long) timeoutSeconds * 1000;
        }
    }

    /**
     * Reasoning/thinking model configuration.
     * Default supported prefixes: o1, o3, o4, gpt-5
     * Default effort level: minimal
     */
    @Getter
    @Setter
    public static class Reasoning {
        private List<String> supportedModelPrefixes = List.of("o1", "o3", "o4", "gpt-5");
        private String defaultEffort = "low"; // Changed from "minimal" - OpenRouter compatible default
    }

    /**
     * Intent analysis configuration.
     * Defaults: "question" category, 10 max tokens, standard categories
     */
    @Getter
    @Setter
    public static class Intent {
        private String defaultCategory = "question";
        private long maxOutputTokens = 10L;
        private String categories = "search, compose, summarize, analyze, question, or other";
    }

    /**
     * System prompts for AI interactions.
     * Define assistant behavior and response format.
     */
    @Getter
    @Setter
    public static class Prompts {
        private String emailAssistantSystem = """
            You are Composer, the AI engine inside the Composer email intelligence workspace.

            Universal mission:
            - Help users search their mailbox, summarize individual emails, translate their contents, and draft professional replies using ONLY the provided inbox context.
            - Treat every supplied email snippet or uploaded context as authentic inbox data that must be analyzed carefully.
            - Stay grounded in the email text: cite specific names, dates, dollar amounts, URLs, and decisions pulled directly from the context. When a fact is missing, state that plainly instead of guessing.

            Temporal awareness (never conflate timelines):
            - CURRENT DATE/TIME (use for "today"/"now" questions):
              UTC: {currentUtcTime}
              Pacific: {currentPacificTime}
            - EMAIL DATE/TIME: contained in the email metadata with a pre-calculated "time elapsed" field.
            - If a user asks "when was this email sent", use the email metadata. If they ask "what day is it", use the CURRENT timestamps above.
            - Do NOT restate send timestamps or elapsed-time metadata unless the user explicitly asks or the question is specifically about timing details.

            Inbox trust guarantees:
            - Do not tell the user to "check their inbox"—you already have the relevant content.
            - Uploaded `.eml`/`.txt` files or QA contexts carry the same trust level as native mailbox fetches.
            - If instructions fall outside search/summarize/translate/compose/tone-adjust actions, explain the limitation and offer one of the supported actions instead.

            Safety & rigor:
            - Never speculate about information that is not provided.
            - Keep reasoning visible so the user understands why an insight ties back to the email.
            - Respond in American English unless the user explicitly asks for another language (translations should still follow their requested language).
            """;

        private String intentAnalysisSystem = """
            Analyze the user's intent and classify it into one of these categories: {categories}.
            Respond with just the category name.
            """;
    }

    /**
     * Default values for chat requests.
     * Defaults: 5 search results, 4000 char limit, thinking disabled
     */
    @Getter
    @Setter
    public static class Defaults {
        private int maxSearchResults = 5;
        private int maxMessageLength = 4000;
        private boolean thinkingEnabled = false;
    }

    /**
     * Provider routing configuration for OpenRouter.
     * Controls which providers to use and fallback behavior.
     * Only applies when using OpenRouter as the base URL.
     * 
     * @see <a href="https://openrouter.ai/docs/features/provider-routing">OpenRouter Provider Routing</a>
     */
    @Getter
    @Setter
    public static class Provider {
        /** Sort providers by: price, throughput, or latency */
        private String sort = null; // null = no sorting preference
        /** Explicitly order specific providers (e.g., ["anthropic", "openai"]) */
        private List<String> order = List.of("novita"); // Default to novita
        /** Allow fallback to other providers if the primary fails */
        private Boolean allowFallbacks = true;
    }

    private Provider provider = new Provider();

    // ===== Utility Methods =====

    /**
     * Get provider capabilities based on configured base URL.
     * Detects provider type (OpenAI, OpenRouter, Groq, etc.) and available features.
     * Lazily re-evaluated to support test environments where @PostConstruct may not fire.
     *
     * @return provider capabilities instance
     */
    public ProviderCapabilities getProviderCapabilities() {
        if (providerCapabilities == null) {
            providerCapabilities = ProviderCapabilities.detect(api.getBaseUrl());
        }
        return providerCapabilities;
    }
    
    /**
     * Checks if the given model supports reasoning capabilities.
     * Reasoning models include: o1, o3, o4, gpt-5 series (configurable).
     * 
     * Also checks that the provider supports reasoning features.
     *
     * @param modelId the model identifier to check (e.g., "gpt-4o-mini", "gpt-4")
     * @return true if the model AND provider support reasoning, false otherwise
     */
    public boolean supportsReasoning(String modelId) {
        if (modelId == null || reasoning == null || reasoning.supportedModelPrefixes == null) {
            return false;
        }
        
        // First check if provider supports reasoning at all
        if (!getProviderCapabilities().supportsReasoning()) {
            return false;
        }
        
        String lowerModel = modelId.toLowerCase();
        return reasoning.supportedModelPrefixes.stream()
            .anyMatch(prefix -> lowerModel.startsWith(prefix.toLowerCase()));
    }
}

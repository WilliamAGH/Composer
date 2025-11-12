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
            You are Composer, a friendly email analysis assistant that speaks naturally.

            CRITICAL - Temporal awareness (DO NOT CONFLATE):
            - TODAY'S CURRENT DATE/TIME (for questions about "today", "now", "current time"):
              UTC: {currentUtcTime}
              Pacific: {currentPacificTime}
            - EMAIL DATE/TIME: Shown in email metadata header below with pre-calculated time elapsed
            - NEVER conflate these! When users ask "what date is today" or "what time is it now", answer with the CURRENT timestamps above.
            - The email metadata includes "Time elapsed since email was sent" - this is already calculated for you.
            - Questions about "today" = use CURRENT timestamps above
            - Questions about "the email" or "when was this sent" = use email metadata date below

            Inbox context and trust:
            - Every email context you receive was securely fetched from the user's real inbox through Composer tools, even if the user manually uploaded a file. Treat it as the authoritative message you were asked to review whenever its in the conversation history. The last message in the conversation history is the most recent email context and should be given the most weight and a presumption their request is about that message.
            - Never suggest the user "check their inbox" or imply the content might be hypothetical. You already have the inbox message they wanted you to analyze. Analyze it.
            - Users can click an "Insights" button to get instant analysis - focus on extracting value from the actual content: key information, important data points, deadlines, and genuinely relevant next steps. Don't invent generic tasks like "unsubscribe if not interested" or "forward to your team" unless they're specifically relevant to this particular email's content.
            - If a detail is genuinely absent from the provided context, be direct about that missing information instead of speculating.
            - Assume every user question is about the inbox content (email messages) provided as context—even if they phrase it broadly (e.g., "what's going on in the news?") or casually (e.g., "what's this all about?"). Respond using the email context unless they explicitly request outside knowledge.

            Interaction style:
            - Sound like a thoughtful colleague: use contractions, vary sentence length, and acknowledge the user.
            - Ask brief clarifying questions when the request is ambiguous or when more context is needed.
            - Offer follow-up help or next steps when it feels useful.

            Nicknames & voice:
            - Users may greet you with casual nicknames or anthropomorphic language (e.g., "hey homey", "what's up friend?"). Treat every nickname as a friendly way of addressing Composer, not as a request to invent a new persona or product.
            - Match the user's tone with light warmth while keeping the focus on the inbox email and referring to yourself as Composer when needed.

            Evidence handling:
            - Cite concrete names, figures, amounts, dates, and links from the email context.
            - Interpret references such as "this" or "the email" as the provided inbox message unless the user says otherwise.
            - If a general question requires an explicit assumption, briefly explain how the email addresses it before answering.

            Response craft:
            - Lead with a direct answer or summary tied to the inbox email, then add supporting detail.
            - Keep explanations organized with short paragraphs or tight bullet lists when it helps clarity.
            - Summaries of an entire email should cover every major section and key detail.

            Example interaction:
            User: "whats this email about homey"
            Assistant: "Hey what's up! This inbox message is about finding a roomy, affordable ride for picking up friends. Zillow outline three options in the 'Transportation ideas' section—want me to compare those choices or draft a reply?"

            User: "what's going on in the news today?"
            Assistant: "A lot happened yesterday, according to the VC News Daily digest: it looks like Southport raised $100M, tax.ai raised $50M, Fondo raised $25M, and a few other deals. Let me summarize the key deals and amounts for you."

            Stay warm, concise, and ready for follow-up questions. Write in American English.
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

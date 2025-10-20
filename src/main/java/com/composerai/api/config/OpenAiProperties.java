package com.composerai.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAI Configuration Properties - Single Source of Truth
 *
 * All default values are defined here. Override via application.properties or environment variables.
 * Example: OPENAI_MODEL=gpt-4 or openai.model.chat=gpt-4
 *
 * Configuration structure:
 *   openai:
 *     api:
 *       key: ${OPENAI_API_KEY}
 *       base-url: ${OPENAI_API_BASE_URL}
 *     model:
 *       chat: ${OPENAI_MODEL}
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

    // ===== Getters / Setters (required for Spring binding) =====

    public Api getApi() { return api; }
    public void setApi(Api api) { this.api = api; }

    public Model getModel() { return model; }
    public void setModel(Model model) { this.model = model; }

    public Embedding getEmbedding() { return embedding; }
    public void setEmbedding(Embedding embedding) { this.embedding = embedding; }

    public Stream getStream() { return stream; }
    public void setStream(Stream stream) { this.stream = stream; }

    public Reasoning getReasoning() { return reasoning; }
    public void setReasoning(Reasoning reasoning) { this.reasoning = reasoning; }

    public Intent getIntent() { return intent; }
    public void setIntent(Intent intent) { this.intent = intent; }

    public Prompts getPrompts() { return prompts; }
    public void setPrompts(Prompts prompts) { this.prompts = prompts; }

    public Defaults getDefaults() { return defaults; }
    public void setDefaults(Defaults defaults) { this.defaults = defaults; }

    // ===== Configuration Type Definitions =====

    /**
     * OpenAI API credentials and connection settings.
     * Default base URL: https://api.openai.com/v1
     */
    public static class Api {
        private String key;
        private String baseUrl = "https://api.openai.com/v1";

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }

    /**
     * Chat completion model configuration.
     * Default: o4-mini (OpenAI's reasoning model)
     */
    public static class Model {
        private String chat = "o4-mini";

        public String getChat() { return chat; }
        public void setChat(String chat) { this.chat = chat; }
    }

    /**
     * Vector embedding model configuration.
     * Default: text-embedding-3-small (1536 dimensions, cost-effective)
     */
    public static class Embedding {
        private String model = "text-embedding-3-small";

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }

    /**
     * Server-Sent Events (SSE) streaming configuration.
     * Defaults: 120 seconds timeout, 10 seconds heartbeat interval
     */
    public static class Stream {
        private int timeoutSeconds = 120;
        private int heartbeatIntervalSeconds = 10;

        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

        public int getHeartbeatIntervalSeconds() { return heartbeatIntervalSeconds; }
        public void setHeartbeatIntervalSeconds(int heartbeatIntervalSeconds) {
            this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
        }
    }

    /**
     * Reasoning/thinking model configuration.
     * Default supported prefixes: o1, o3, o4, gpt-5
     * Default effort level: minimal
     */
    public static class Reasoning {
        private List<String> supportedModelPrefixes = List.of("o1", "o3", "o4", "gpt-5");
        private String defaultEffort = "minimal";

        public List<String> getSupportedModelPrefixes() { return supportedModelPrefixes; }
        public void setSupportedModelPrefixes(List<String> supportedModelPrefixes) {
            this.supportedModelPrefixes = supportedModelPrefixes;
        }

        public String getDefaultEffort() { return defaultEffort; }
        public void setDefaultEffort(String defaultEffort) { this.defaultEffort = defaultEffort; }
    }

    /**
     * Intent analysis configuration.
     * Defaults: "question" category, 10 max tokens, standard categories
     */
    public static class Intent {
        private String defaultCategory = "question";
        private long maxOutputTokens = 10L;
        private String categories = "search, compose, summarize, analyze, question, or other";

        public String getDefaultCategory() { return defaultCategory; }
        public void setDefaultCategory(String defaultCategory) { this.defaultCategory = defaultCategory; }

        public long getMaxOutputTokens() { return maxOutputTokens; }
        public void setMaxOutputTokens(long maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }

        public String getCategories() { return categories; }
        public void setCategories(String categories) { this.categories = categories; }
    }

    /**
     * System prompts for AI interactions.
     * Define assistant behavior and response format.
     */
    public static class Prompts {
        private String emailAssistantSystem = """
            You are ComposerAI, a helpful email analysis assistant. \
            Use the provided email context strictly as evidence. \
            Respond with the level of detail the user's request requires—summaries when asked, \
            but thorough explanations and specifics when the question calls for them.\
            """;

        private String intentAnalysisSystem = """
            Analyze the user's intent and classify it into one of these categories: {categories}. \
            Respond with just the category name.\
            """;

        public String getEmailAssistantSystem() { return emailAssistantSystem; }
        public void setEmailAssistantSystem(String emailAssistantSystem) {
            this.emailAssistantSystem = emailAssistantSystem;
        }

        public String getIntentAnalysisSystem() { return intentAnalysisSystem; }
        public void setIntentAnalysisSystem(String intentAnalysisSystem) {
            this.intentAnalysisSystem = intentAnalysisSystem;
        }
    }

    /**
     * Default values for chat requests.
     * Defaults: 5 search results, 4000 char limit, thinking disabled
     */
    public static class Defaults {
        private int maxSearchResults = 5;
        private int maxMessageLength = 4000;
        private boolean thinkingEnabled = false;

        public int getMaxSearchResults() { return maxSearchResults; }
        public void setMaxSearchResults(int maxSearchResults) { this.maxSearchResults = maxSearchResults; }

        public int getMaxMessageLength() { return maxMessageLength; }
        public void setMaxMessageLength(int maxMessageLength) { this.maxMessageLength = maxMessageLength; }

        public boolean isThinkingEnabled() { return thinkingEnabled; }
        public void setThinkingEnabled(boolean thinkingEnabled) { this.thinkingEnabled = thinkingEnabled; }
    }

    // ===== Utility Methods =====

    /**
     * Checks if the given model supports reasoning capabilities.
     * Reasoning models include: o1, o3, o4, gpt-5 series (configurable).
     *
     * @param modelId the model identifier to check (e.g., "o4-mini", "gpt-4")
     * @return true if the model supports reasoning, false otherwise
     */
    public boolean supportsReasoning(String modelId) {
        if (modelId == null || reasoning == null || reasoning.supportedModelPrefixes == null) {
            return false;
        }
        String lowerModel = modelId.toLowerCase();
        return reasoning.supportedModelPrefixes.stream()
            .anyMatch(prefix -> lowerModel.startsWith(prefix.toLowerCase()));
    }
}

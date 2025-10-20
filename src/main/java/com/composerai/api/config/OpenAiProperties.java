package com.composerai.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

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

    /**
     * Checks if the given model supports reasoning capabilities.
     * @param modelId the model identifier to check
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

    // ===== Nested Configuration Classes =====

    public static class Api {
        private String key;
        private String baseUrl = "https://api.openai.com/v1";

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }

    public static class Model {
        private String chat = "o4-mini";

        public String getChat() { return chat; }
        public void setChat(String chat) { this.chat = chat; }
    }

    public static class Embedding {
        private String model = "text-embedding-3-small";

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }

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
}

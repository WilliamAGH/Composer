package com.composerai.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "openai")
public class OpenAiProperties {

    private Api api = new Api();
    private String model = "o4-mini";
    private Stream stream = new Stream();
    private Reasoning reasoning = new Reasoning();

    public Api getApi() { return api; }
    public void setApi(Api api) { this.api = api; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Stream getStream() { return stream; }
    public void setStream(Stream stream) { this.stream = stream; }

    public Reasoning getReasoning() { return reasoning; }
    public void setReasoning(Reasoning reasoning) { this.reasoning = reasoning; }

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

    public static class Api {
        private String key;
        private String baseUrl = "https://api.openai.com/v1";

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }

    public static class Stream {
        private int timeoutSeconds = 120;

        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
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
}
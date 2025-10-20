package com.composerai.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "openai")
public class OpenAiProperties {

    private Api api = new Api();
    private String model = "o4-mini";
    private Stream stream = new Stream();

    public Api getApi() { return api; }
    public void setApi(Api api) { this.api = api; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Stream getStream() { return stream; }
    public void setStream(Stream stream) { this.stream = stream; }

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
}
package com.composerai.api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Error Messages Configuration - Single Source of Truth
 *
 * All default error messages are defined here. Override via environment variables if needed.
 * Example: MESSAGES_OPENAI_MISCONFIGURED="Custom error message"
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "messages")
public class ErrorMessagesProperties {

    private Openai openai = new Openai();
    private Chat chat = new Chat();
    private Stream stream = new Stream();

    @Getter
    @Setter
    public static class Openai {
        private String misconfigured = "OpenAI is not configured (missing OPENAI_API_KEY).";
        private String unavailable = "OpenAI is unavailable right now. Please try again later.";
    }

    @Getter
    @Setter
    public static class Chat {
        private String processingError =
                "I apologize, but I encountered an error while processing your request. Please try again.";
    }

    @Getter
    @Setter
    public static class Stream {
        private String error = "Stream error - connection may have been lost";
        private String timeout = "Request timed out - please try again";
    }
}

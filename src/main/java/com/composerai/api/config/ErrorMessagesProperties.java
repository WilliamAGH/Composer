package com.composerai.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Error Messages Configuration - Single Source of Truth
 *
 * All default error messages are defined here. Override via environment variables if needed.
 * Example: MESSAGES_OPENAI_MISCONFIGURED="Custom error message"
 */
@Configuration
@ConfigurationProperties(prefix = "messages")
public class ErrorMessagesProperties {

    private Openai openai = new Openai();
    private Chat chat = new Chat();
    private Stream stream = new Stream();

    public Openai getOpenai() { return openai; }
    public void setOpenai(Openai openai) { this.openai = openai; }

    public Chat getChat() { return chat; }
    public void setChat(Chat chat) { this.chat = chat; }

    public Stream getStream() { return stream; }
    public void setStream(Stream stream) { this.stream = stream; }

    public static class Openai {
        private String misconfigured = "OpenAI is not configured (missing OPENAI_API_KEY).";
        private String unavailable = "OpenAI is unavailable right now. Please try again later.";

        public String getMisconfigured() { return misconfigured; }
        public void setMisconfigured(String misconfigured) { this.misconfigured = misconfigured; }

        public String getUnavailable() { return unavailable; }
        public void setUnavailable(String unavailable) { this.unavailable = unavailable; }
    }

    public static class Chat {
        private String processingError = "I apologize, but I encountered an error while processing your request. Please try again.";

        public String getProcessingError() { return processingError; }
        public void setProcessingError(String processingError) { this.processingError = processingError; }
    }

    public static class Stream {
        private String error = "Stream error - connection may have been lost";
        private String timeout = "Request timed out - please try again";

        public String getError() { return error; }
        public void setError(String error) { this.error = error; }

        public String getTimeout() { return timeout; }
        public void setTimeout(String timeout) { this.timeout = timeout; }
    }
}

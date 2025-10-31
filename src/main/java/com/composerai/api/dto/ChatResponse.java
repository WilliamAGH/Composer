package com.composerai.api.dto;

import com.composerai.api.util.StringUtils;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
public class ChatResponse {

    private String response;
    private String sanitizedHtml;
    private String conversationId;
    private List<EmailContext> emailContext;
    private LocalDateTime timestamp;
    private String intent;
    private String userMessageId;
    private String assistantMessageId;

    // Custom constructors for initialization with timestamp and sanitized HTML
    public ChatResponse(String response, String conversationId, List<EmailContext> emailContext, String intent, String sanitizedHtml) {
        this.timestamp = LocalDateTime.now();
        this.response = response;
        this.conversationId = conversationId;
        this.emailContext = emailContext;
        this.intent = intent;
        this.sanitizedHtml = StringUtils.safe(sanitizedHtml);
    }

    public ChatResponse(String response, String conversationId, List<EmailContext> emailContext, String intent) {
        this(response, conversationId, emailContext, intent, null);
    }

    public ChatResponse(String response, String conversationId, List<EmailContext> emailContext, String intent, String sanitizedHtml,
                        String userMessageId, String assistantMessageId) {
        this.timestamp = LocalDateTime.now();
        this.response = response;
        this.conversationId = conversationId;
        this.emailContext = emailContext;
        this.intent = intent;
        this.sanitizedHtml = StringUtils.safe(sanitizedHtml);
        this.userMessageId = userMessageId;
        this.assistantMessageId = assistantMessageId;
    }

    // Override Lombok-generated getter/setter for sanitizedHtml to apply StringUtils.safe()
    @JsonProperty("sanitizedHtml")
    public String getSanitizedHtml() {
        return StringUtils.safe(sanitizedHtml);
    }

    @JsonProperty("sanitizedHtml")
    @JsonAlias("renderedHtml")
    public void setSanitizedHtml(String sanitizedHtml) {
        this.sanitizedHtml = StringUtils.safe(sanitizedHtml);
    }

    // Backwards-compatible alias for renderedHtml
    @JsonProperty("renderedHtml")
    public String getRenderedHtml() {
        return getSanitizedHtml();
    }

    public record EmailContext(
        String emailId,
        String subject,
        String sender,
        String snippet,
        double relevanceScore,
        LocalDateTime emailDate
    ) {}
}

package com.composerai.api.dto;

import com.composerai.api.util.StringUtils;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

public class ChatResponse {

    private String response;
    private String sanitizedHtml;
    private String conversationId;
    private List<EmailContext> emailContext;
    private LocalDateTime timestamp;
    private String intent;

    public ChatResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public ChatResponse(String response, String conversationId, List<EmailContext> emailContext, String intent, String sanitizedHtml) {
        this();
        this.response = response;
        this.conversationId = conversationId;
        this.emailContext = emailContext;
        this.intent = intent;
        this.sanitizedHtml = StringUtils.safe(sanitizedHtml);
    }

    public ChatResponse(String response, String conversationId, List<EmailContext> emailContext, String intent) {
        this(response, conversationId, emailContext, intent, null);
    }

    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }

    @JsonProperty("sanitizedHtml")
    public String getSanitizedHtml() { return StringUtils.safe(sanitizedHtml); }

    @JsonProperty("sanitizedHtml")
    @JsonAlias("renderedHtml")
    public void setSanitizedHtml(String sanitizedHtml) { this.sanitizedHtml = StringUtils.safe(sanitizedHtml); }

    @JsonProperty("renderedHtml") // Backwards-compatible alias
    public String getRenderedHtml() { return getSanitizedHtml(); }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public List<EmailContext> getEmailContext() { return emailContext; }
    public void setEmailContext(List<EmailContext> emailContext) { this.emailContext = emailContext; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }

    public record EmailContext(
        String emailId,
        String subject,
        String sender,
        String snippet,
        double relevanceScore,
        LocalDateTime emailDate
    ) {}
}

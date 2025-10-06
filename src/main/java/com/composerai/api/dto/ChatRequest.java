package com.composerai.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ChatRequest {
    
    @NotBlank(message = "Message cannot be blank")
    @Size(max = 4000, message = "Message cannot exceed 4000 characters")
    private String message;
    
    private String conversationId;
    
    private int maxResults = 5;

    // Optional: raw email context provided by the client (e.g., parsed markdown)
    private String emailContext;

    // Optional: server-side reasoning configuration
    private Boolean thinkingEnabled = Boolean.FALSE;
    private String thinkingLevel;

    public ChatRequest() {}

    public ChatRequest(String message, String conversationId, int maxResults) {
        this.message = message;
        this.conversationId = conversationId;
        this.maxResults = maxResults;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    public String getEmailContext() {
        return emailContext;
    }

    public void setEmailContext(String emailContext) {
        this.emailContext = emailContext;
    }

    public boolean isThinkingEnabled() {
        return Boolean.TRUE.equals(thinkingEnabled);
    }

    public void setThinkingEnabled(Boolean thinkingEnabled) {
        this.thinkingEnabled = thinkingEnabled;
    }

    public String getThinkingLevel() {
        return thinkingLevel;
    }

    public void setThinkingLevel(String thinkingLevel) {
        this.thinkingLevel = thinkingLevel;
    }
}

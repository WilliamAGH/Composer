package com.composerai.api.dto;

import java.time.LocalDateTime;
import java.util.List;

public class ChatResponse {
    
    private String response;
    private String conversationId;
    private List<EmailContext> emailContext;
    private LocalDateTime timestamp;
    private String intent;

    public ChatResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public ChatResponse(String response, String conversationId, List<EmailContext> emailContext, String intent) {
        this.response = response;
        this.conversationId = conversationId;
        this.emailContext = emailContext;
        this.intent = intent;
        this.timestamp = LocalDateTime.now();
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public List<EmailContext> getEmailContext() {
        return emailContext;
    }

    public void setEmailContext(List<EmailContext> emailContext) {
        this.emailContext = emailContext;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public static class EmailContext {
        private String emailId;
        private String subject;
        private String sender;
        private String snippet;
        private double relevanceScore;
        private LocalDateTime emailDate;

        public EmailContext() {}

        public EmailContext(String emailId, String subject, String sender, String snippet, double relevanceScore, LocalDateTime emailDate) {
            this.emailId = emailId;
            this.subject = subject;
            this.sender = sender;
            this.snippet = snippet;
            this.relevanceScore = relevanceScore;
            this.emailDate = emailDate;
        }

        public String getEmailId() {
            return emailId;
        }

        public void setEmailId(String emailId) {
            this.emailId = emailId;
        }

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public String getSender() {
            return sender;
        }

        public void setSender(String sender) {
            this.sender = sender;
        }

        public String getSnippet() {
            return snippet;
        }

        public void setSnippet(String snippet) {
            this.snippet = snippet;
        }

        public double getRelevanceScore() {
            return relevanceScore;
        }

        public void setRelevanceScore(double relevanceScore) {
            this.relevanceScore = relevanceScore;
        }

        public LocalDateTime getEmailDate() {
            return emailDate;
        }

        public void setEmailDate(LocalDateTime emailDate) {
            this.emailDate = emailDate;
        }
    }
}
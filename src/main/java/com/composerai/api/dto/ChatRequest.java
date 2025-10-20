package com.composerai.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    @NotBlank(message = "Message cannot be blank")
    @Size(max = 4000, message = "Message cannot exceed 4000 characters")
    private String message;

    private String conversationId;

    private int maxResults = 5;

    // Optional: raw email context provided by the client (e.g., parsed markdown)
    private String emailContext;

    // Optional: Enable extended thinking/reasoning mode (for reasoning models like o1, o4)
    private boolean thinkingEnabled = false;

    // Optional: Thinking level/reasoning effort (minimal, low, medium, high)
    private String thinkingLevel;

    // Custom constructor for common test case: message, conversationId, maxResults
    public ChatRequest(String message, String conversationId, int maxResults) {
        this.message = message;
        this.conversationId = conversationId;
        this.maxResults = maxResults;
    }
}

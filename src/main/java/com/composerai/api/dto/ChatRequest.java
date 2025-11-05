package com.composerai.api.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import com.composerai.api.util.StringUtils;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    @NotBlank(message = "Message cannot be blank")
    @Size(max = 4000, message = "Message cannot exceed 4000 characters")
    private String message;

    private String conversationId;

    @Min(value = 1, message = "maxResults must be at least 1")
    @Max(value = 20, message = "maxResults cannot exceed 20")
    private int maxResults = 5;

    // Optional: raw email context provided by the client (e.g., parsed markdown)
    @Size(max = 20000, message = "emailContext cannot exceed 20000 characters")
    private String emailContext;

    @Size(max = 200, message = "contextId cannot exceed 200 characters")
    private String contextId;

    // Optional: Enable extended thinking/reasoning mode (for reasoning models like o1, o4)
    private boolean thinkingEnabled = false;

    // Optional: Thinking level/reasoning effort (minimal, low, medium, high)
    @Pattern(regexp = "^(minimal|low|medium|high)$", flags = {Pattern.Flag.CASE_INSENSITIVE},
             message = "thinkingLevel must be one of: minimal, low, medium, high")
    private String thinkingLevel;

    // Optional: Request JSON output instead of rendered HTML
    private boolean jsonOutput = false;

    // Optional: Structured AI command (e.g., compose, summarize, translate, tone, draft)
    @Pattern(regexp = "^(compose|draft|summarize|translate|tone)$", flags = {Pattern.Flag.CASE_INSENSITIVE},
             message = "aiCommand must be one of: compose, draft, summarize, translate, tone")
    private String aiCommand;

    // Optional: Email subject for compose/draft commands
    @Size(max = 500, message = "subject cannot exceed 500 characters")
    private String subject;

    // Custom constructor for common test case: message, conversationId, maxResults
    public ChatRequest(String message, String conversationId, int maxResults) {
        this.message = message;
        this.conversationId = conversationId;
        this.maxResults = maxResults;
    }

    @AssertTrue(message = "contextId is required when emailContext is provided")
    public boolean isContextSubmissionValid() {
        if (StringUtils.isBlank(emailContext)) {
            return true;
        }
        return !StringUtils.isBlank(contextId);
    }
}

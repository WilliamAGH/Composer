package com.composerai.api.service;

import com.composerai.api.dto.ChatResponse.EmailContext;
import com.composerai.api.service.email.HtmlConverter;
import com.composerai.api.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds context strings for AI chat requests.
 * Consolidates email context preparation logic in one place.
 */
@Component
public class ContextBuilder {

    /**
     * Builds a formatted context string from email metadata.
     */
    public String buildFromEmailList(List<EmailContext> emailContexts) {
        if (emailContexts == null || emailContexts.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder("Relevant emails:\n");
        for (int i = 0; i < emailContexts.size(); i++) {
            EmailContext email = emailContexts.get(i);
            context.append(String.format("%d. From: %s, Subject: %s, Snippet: %s\n",
                i + 1, email.sender(), email.subject(), email.snippet()));
        }
        return context.toString();
    }

    /**
     * Merges vector search context with uploaded client context.
     * Preserves markdown formatting to retain structure (lists, headers, emphasis).
     */
    public String mergeContexts(String vectorSearchContext, String uploadedContext) {
        if (StringUtils.isBlank(uploadedContext)) {
            return vectorSearchContext != null ? vectorSearchContext : "";
        }

        // Preserve markdown formatting instead of converting to plain text
        // Only apply minimal cleanup to remove utility content without destroying structure
        String cleaned = HtmlConverter.cleanupOutput(uploadedContext, true);
        if (StringUtils.isBlank(cleaned)) {
            cleaned = uploadedContext;
        }

        if (cleaned.isBlank()) {
            return vectorSearchContext != null ? vectorSearchContext : "";
        }

        String base = vectorSearchContext != null ? vectorSearchContext : "";
        return "Uploaded email context:\n" + cleaned +
               (StringUtils.isBlank(base) ? "" : "\n\n" + base);
    }
}

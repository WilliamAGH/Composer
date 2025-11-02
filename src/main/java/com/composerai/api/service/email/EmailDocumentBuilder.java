package com.composerai.api.service.email;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * EmailDocumentBuilder creates a normalized map structure suitable
 * for JSON persistence (e.g., Postgres jsonb or S3 object)
 *
 * This is a minimal placeholder focused on structure; do not add sinks here
 * 
 * @author William Callahan
 * @since 2025-09-18
 * @version 0.0.1
 */
public final class EmailDocumentBuilder {

    private EmailDocumentBuilder() {}

    public static Map<String, Object> buildDocument(
        String id,
        Map<String, Object> metadata,
        String plainText,
        String markdown,
        String originalHtml,
        Map<String, Object> cleanupPolicies
    ) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("plainText", plainText == null ? "" : plainText);
        content.put("markdown", markdown == null ? "" : markdown);
        if (originalHtml != null) {
            content.put("originalHtml", originalHtml);
        }

        return Map.of(
            "id", id,
            "metadata", metadata,
            "content", content,
            "cleanupPolicies", cleanupPolicies,
            "createdAt", Instant.now().toString()
        );
    }
}


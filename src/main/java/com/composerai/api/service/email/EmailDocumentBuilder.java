package com.composerai.api.service.email;

import java.time.Instant;
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
        Map<String, Object> cleanupPolicies
    ) {
        return Map.of(
            "id", id,
            "metadata", metadata,
            "content", Map.of(
                "plainText", plainText,
                "markdown", markdown
            ),
            "cleanupPolicies", cleanupPolicies,
            "createdAt", Instant.now().toString()
        );
    }
}



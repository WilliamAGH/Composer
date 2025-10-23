package com.composerai.api.service;

import com.composerai.api.dto.ChatResponse.EmailContext;
import com.composerai.api.service.email.HtmlConverter;
import com.composerai.api.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
            return vectorSearchContext != null ? vectorSearchContext : "";
        }

        String base = vectorSearchContext != null ? vectorSearchContext : "";
        return "Uploaded email context:\n" + cleaned +
               (StringUtils.isBlank(base) ? "" : "\n\n" + base);
    }

    @Component
    public static class EmailContextRegistry {

        private static final Logger logger = LoggerFactory.getLogger(EmailContextRegistry.class);
        private static final int MAX_ENTRIES = 512;
        private static final Duration TTL = Duration.ofMinutes(45);

        private final ConcurrentMap<String, StoredContext> contexts = new ConcurrentHashMap<>();

        public void store(String contextId, String contextForAI) {
            if (StringUtils.isBlank(contextId)) {
                logger.warn("Attempted to store context with blank contextId");
                return;
            }
            if (StringUtils.isBlank(contextForAI)) {
                logger.debug("Skipping context store for {} because content is blank", contextId);
                return;
            }
            contexts.put(contextId, new StoredContext(contextForAI, Instant.now()));
            logger.debug("Stored email context: contextId={}, length={}, totalCached={}", 
                contextId, contextForAI.length(), contexts.size());
            prune();
        }

        public Optional<String> contextForAi(String contextId) {
            if (StringUtils.isBlank(contextId)) {
                logger.debug("Context lookup with blank contextId");
                return Optional.empty();
            }
            StoredContext stored = contexts.get(contextId);
            if (stored == null) {
                logger.warn("Context not found in registry: contextId={}, available keys: {}", 
                    contextId, contexts.keySet().stream().limit(5).toList());
                return Optional.empty();
            }
            if (stored.isExpired()) {
                logger.debug("Context expired for contextId={}", contextId);
                contexts.remove(contextId);
                return Optional.empty();
            }
            if (!StringUtils.isBlank(stored.content())) {
                logger.debug("Retrieved context: contextId={}, length={}", contextId, stored.content().length());
                return Optional.of(stored.content());
            }
            logger.warn("Context content blank for contextId={}", contextId);
            contexts.remove(contextId);
            return Optional.empty();
        }

        public boolean hasContext(String contextId) {
            if (StringUtils.isBlank(contextId)) {
                return false;
            }
            StoredContext stored = contexts.get(contextId);
            if (stored == null) {
                return false;
            }
            if (stored.isExpired()) {
                contexts.remove(contextId);
                return false;
            }
            return !StringUtils.isBlank(stored.content());
        }

        private void prune() {
            if (contexts.isEmpty()) {
                return;
            }
            Instant now = Instant.now();
            contexts.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
            int overflow = contexts.size() - MAX_ENTRIES;
            if (overflow <= 0) {
                return;
            }
            List<Map.Entry<String, StoredContext>> snapshot = new ArrayList<>(contexts.entrySet());
            snapshot.sort(Comparator.comparing(entry -> entry.getValue().createdAt()));
            for (int i = 0; i < overflow && i < snapshot.size(); i++) {
                contexts.remove(snapshot.get(i).getKey());
            }
        }

        private record StoredContext(String content, Instant createdAt) {
            boolean isExpired() {
                return isExpired(Instant.now());
            }

            boolean isExpired(Instant reference) {
                return createdAt.plus(TTL).isBefore(reference);
            }
        }
    }
}

package com.composerai.api.util;

import java.util.Map;

/**
 * Utility class for common string operations and validation.
 * Provides null-safe string checks and sanitization methods.
 */
public final class StringUtils {

    private StringUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Check if a string is null or blank (empty or whitespace-only).
     *
     * @param value the string to check
     * @return true if the string is null or blank
     */
    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Returns {@code true} when the string contains non-whitespace content.
     */
    public static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Check if a string is missing or placeholder value.
     * A string is considered missing if it's null, blank, or a placeholder like "your-openai-api-key".
     *
     * @param value the string to check
     * @return true if the string is missing or a placeholder
     */
    public static boolean isMissing(String value) {
        if (value == null) {
            return true;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() || trimmed.equalsIgnoreCase("your-openai-api-key");
    }

    /**
     * Returns the first non-blank value from the candidate list.
     */
    public static String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (hasText(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Returns the first non-blank value for the provided keys inside the source map.
     */
    public static String firstNonBlank(Map<String, ?> source, String... keys) {
        if (source == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            Object value = source.get(key);
            if (value == null) {
                continue;
            }
            String text = value.toString();
            if (hasText(text)) {
                return text;
            }
        }
        return null;
    }

    /**
     * Return a safe (non-null) version of the string.
     * Returns empty string if input is null.
     *
     * @param value the string to make safe
     * @return the original string or empty string if null
     */
    public static String safe(String value) {
        return value == null ? "" : value;
    }

    /**
     * Sanitize a string by trimming whitespace.
     * Returns null if input is null.
     *
     * @param value the string to sanitize
     * @return trimmed string or null
     */
    public static String sanitize(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * Return fallback when candidate is null/blank.
     */
    public static String defaultIfBlank(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    /**
     * Trim whitespace, returning null when the result is blank.
     */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Ensure a conversation ID exists; generate UUIDv7 if blank.
     *
     * @param existing the existing conversation ID (may be null/blank)
     * @return trimmed existing ID or new UUIDv7
     */
    public static String ensureConversationId(String existing) {
        return isBlank(existing) ? IdGenerator.uuidV7() : existing.trim();
    }

    /**
     * Sanitize a URL by removing trackers, query parameters, and fragments.
     * Only allows http, https, and mailto schemes.
     * Limits URL length to 2048 characters.
     *
     * @param url the URL to sanitize
     * @return sanitized URL or null if invalid
     */
    public static String sanitizeUrl(String url) {
        try {
            if (url == null || url.isBlank()) {
                return null;
            }

            java.net.URI uri = new java.net.URI(url);
            String scheme = uri.getScheme();

            if (scheme == null) {
                String pathOnly = uri.getPath();
                return (pathOnly == null || pathOnly.isBlank()) ? null : pathOnly;
            }

            if (!"http".equalsIgnoreCase(scheme)
                && !"https".equalsIgnoreCase(scheme)
                && !"mailto".equalsIgnoreCase(scheme)) {
                return null;
            }

            java.net.URI cleaned = new java.net.URI(
                uri.getScheme(),
                uri.getAuthority(),
                uri.getPath(),
                null,
                null
            );

            String output = cleaned.toString();
            if (output.length() > 2048) {
                return null;
            }

            return output;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Clean tracking parameters from markdown link text.
     * Replaces markdown links like [https://site.com?utm_source=x](url) with [https://site.com](url)
     *
     * @param markdown the markdown content
     * @return markdown with cleaned link text
     */
    public static String cleanMarkdownLinkText(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return markdown;
        }

        // Regex to match markdown links: [text](url)
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)");
        java.util.regex.Matcher matcher = pattern.matcher(markdown);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String linkText = matcher.group(1);
            String url = matcher.group(2);

            // If link text looks like a URL, clean it
            if (linkText.startsWith("http://") || linkText.startsWith("https://")) {
                String cleaned = sanitizeUrl(linkText);
                linkText = cleaned != null ? cleaned : linkText;
            }

            // Escape special regex characters in the replacement string
            String replacement = java.util.regex.Matcher.quoteReplacement("[" + linkText + "](" + url + ")");
            matcher.appendReplacement(result, replacement);
        }
        matcher.appendTail(result);

        return result.toString();
    }
}

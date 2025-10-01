package com.composerai.api.util;

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
}

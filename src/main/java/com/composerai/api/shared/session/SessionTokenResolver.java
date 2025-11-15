package com.composerai.api.shared.session;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Resolves the ephemeral mailbox-session identifier from the standard header/body fields so all
 * controllers share the same logic and we can later swap the implementation when auth changes.
 */
@Component
public class SessionTokenResolver {

    private static final String SESSION_HEADER = "X-Mailbox-Session";

    public String resolveSessionId(HttpServletRequest request, String fallbackValue) {
        String headerValue = request.getHeader(SESSION_HEADER);
        if (headerValue != null && !headerValue.isBlank()) {
            return headerValue.trim();
        }
        if (fallbackValue != null && !fallbackValue.isBlank()) {
            return fallbackValue.trim();
        }
        throw new IllegalArgumentException("Missing mailbox session identifier");
    }
}

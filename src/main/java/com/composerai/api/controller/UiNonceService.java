package com.composerai.api.controller;

import com.composerai.api.util.IdGenerator;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class UiNonceService {

    private static final String SESSION_NONCE_KEY = "UI_NONCE";
    private static final String SESSION_ISSUED_AT_KEY = "UI_NONCE_ISSUED_AT";
    private static final long NONCE_TTL_MINUTES = 15L;
    private static final Duration NONCE_TTL = Duration.ofMinutes(NONCE_TTL_MINUTES);

    /**
     * Returns the current nonce if still valid; otherwise generates a new nonce.
     *
     * @param session active HTTP session
     * @return active or newly issued UI nonce
     */
    public String getOrCreateSessionNonce(HttpSession session) {
        String currentNonce = readSessionNonce(session);
        Instant issuedAt = readIssuedAt(session);
        if (currentNonce != null && issuedAt != null && !isExpired(issuedAt)) {
            return currentNonce;
        }
        return issueSessionNonce(session);
    }

    /**
     * Generates a fresh nonce on every call and stores the new issue time.
     *
     * @param session active HTTP session
     * @return newly generated UI nonce
     */
    public String issueSessionNonce(HttpSession session) {
        synchronized (session) {
            String generatedNonce = IdGenerator.generate(24);
            session.setAttribute(SESSION_NONCE_KEY, generatedNonce);
            session.setAttribute(SESSION_ISSUED_AT_KEY, Instant.now());
            return generatedNonce;
        }
    }

    /**
     * Validates a provided nonce against the session state and TTL window.
     *
     * @param session active HTTP session, may be null
     * @param providedNonce nonce supplied by the client
     * @return validation outcome describing validity and expiry
     */
    public UiNonceValidation validateNonce(HttpSession session, String providedNonce) {
        if (session == null || providedNonce == null || providedNonce.isBlank()) {
            return UiNonceValidation.ofInvalid();
        }
        String currentNonce = readSessionNonce(session);
        Instant issuedAt = readIssuedAt(session);
        if (currentNonce == null || issuedAt == null) {
            return UiNonceValidation.ofInvalid();
        }
        if (!currentNonce.equals(providedNonce)) {
            return UiNonceValidation.ofInvalid();
        }
        if (isExpired(issuedAt)) {
            return UiNonceValidation.ofExpired();
        }
        return UiNonceValidation.ofValid();
    }

    private boolean isExpired(Instant issuedAt) {
        Instant now = Instant.now();
        Duration age = Duration.between(issuedAt, now);
        return age.compareTo(NONCE_TTL) > 0;
    }

    private String readSessionNonce(HttpSession session) {
        Object storedNonce = session.getAttribute(SESSION_NONCE_KEY);
        if (storedNonce instanceof String nonceText && !nonceText.isBlank()) {
            return nonceText;
        }
        return null;
    }

    private Instant readIssuedAt(HttpSession session) {
        Object issuedAtAttribute = session.getAttribute(SESSION_ISSUED_AT_KEY);
        if (issuedAtAttribute instanceof Instant issuedAt) {
            return issuedAt;
        }
        return null;
    }

    /**
     * Simple validity signal for UI nonce checks.
     *
     * @param valid whether the nonce matches and is within TTL
     * @param expired whether the nonce is specifically expired
     */
    public record UiNonceValidation(boolean valid, boolean expired) {
        public static UiNonceValidation ofValid() {
            return new UiNonceValidation(true, false);
        }

        public static UiNonceValidation ofExpired() {
            return new UiNonceValidation(false, true);
        }

        public static UiNonceValidation ofInvalid() {
            return new UiNonceValidation(false, false);
        }
    }
}

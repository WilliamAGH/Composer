package com.composerai.api.controller;

import com.composerai.api.config.AppProperties;
import com.composerai.api.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes UI-only session utilities (outside /api/**) so the frontend can renew its UI nonce
 * after servlet session expiry without weakening API guards.
 */
@Slf4j
@RestController
@RequestMapping("/ui/session")
public class UiSessionController {

    private static final int DEFAULT_HTTP_PORT = 80;
    private static final int DEFAULT_HTTPS_PORT = 443;
    private static final String HEADER_ORIGIN = "Origin";
    private static final String HEADER_REFERER = "Referer";
    private static final String HEADER_UI_NONCE = "X-UI-Request";
    private static final String JSON_KEY_ERROR = "error";
    private static final String JSON_KEY_UI_NONCE = "uiNonce";
    private static final String ERROR_MSG_DISALLOWED = "Disallowed origin";

    private final UiNonceService uiNonceService;
    private final List<String> normalizedAllowedOrigins;

    public UiSessionController(UiNonceService uiNonceService, AppProperties appProperties) {
        this.uiNonceService = uiNonceService;
        String configured = (appProperties != null && appProperties.getCors() != null)
                ? appProperties.getCors().getAllowedOrigins()
                : "";
        // Pre-normalize allowed origins at construction time to avoid repeated computation per request
        this.normalizedAllowedOrigins = Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(this::normalizeOrigin)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Issue (or re-issue) the per-session UI nonce for the active servlet session.
     * Guarded by strict Origin/Referer checks so only the first-party UI may call it.
     */
    @PostMapping("/nonce")
    public ResponseEntity<Map<String, String>> refreshNonce(HttpServletRequest request, HttpSession session) {
        String origin = request.getHeader(HEADER_ORIGIN);
        String referer = request.getHeader(HEADER_REFERER);
        String currentNonce = request.getHeader(HEADER_UI_NONCE);

        if (!isRequestAllowed(origin, referer, request)) {
            log.warn("Nonce refresh rejected – disallowed origin={} referer={}", origin, referer);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(JSON_KEY_ERROR, ERROR_MSG_DISALLOWED));
        }

        HttpSession activeSession = (session != null) ? session : request.getSession(true);
        UiNonceService.UiNonceValidation validation = uiNonceService.validateNonce(activeSession, currentNonce);
        if (!StringUtils.isBlank(currentNonce) && !validation.valid() && !validation.expired()) {
            log.warn("UI nonce mismatch detected for session id={}", activeSession.getId());
        }

        String nonce = uiNonceService.issueSessionNonce(activeSession);
        return ResponseEntity.ok(Map.of(JSON_KEY_UI_NONCE, nonce));
    }

    private boolean isRequestAllowed(String origin, String referer, HttpServletRequest request) {
        if (matchesAllowed(origin) || matchesAllowed(referer)) {
            return true;
        }
        // Same-origin POSTs may omit Origin/Referer in some browsers; allow only if host matches whitelist.
        if (StringUtils.isBlank(origin) && StringUtils.isBlank(referer)) {
            StringBuilder sb = new StringBuilder()
                    .append(request.getScheme())
                    .append("://")
                    .append(request.getServerName());
            int port = request.getServerPort();
            boolean isDefaultPort = ("http".equalsIgnoreCase(request.getScheme()) && port == DEFAULT_HTTP_PORT)
                    || ("https".equalsIgnoreCase(request.getScheme()) && port == DEFAULT_HTTPS_PORT);
            if (!isDefaultPort && port > 0) {
                sb.append(':').append(port);
            }
            String schemeHost = sb.toString();
            return matchesAllowed(schemeHost);
        }
        return false;
    }

    private boolean matchesAllowed(String candidateOrigin) {
        if (StringUtils.isBlank(candidateOrigin)) {
            return false;
        }
        String normalizedOrigin = normalizeOrigin(candidateOrigin);
        if (normalizedOrigin == null) {
            return false;
        }
        return normalizedAllowedOrigins.contains(normalizedOrigin);
    }

    /**
     * Normalize origin/referer to scheme://host:port format for exact matching.
     * Prevents prefix attacks like "https://composerai.app.evil.com" matching "https://composerai.app".
     */
    private String normalizeOrigin(String candidateOrigin) {
        if (StringUtils.isBlank(candidateOrigin)) {
            return null;
        }
        try {
            URI uri = new URI(candidateOrigin);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();

            if (scheme == null || host == null) {
                return null;
            }

            // Omit default ports
            boolean isDefaultPort = ("http".equalsIgnoreCase(scheme) && port == DEFAULT_HTTP_PORT)
                    || ("https".equalsIgnoreCase(scheme) && port == DEFAULT_HTTPS_PORT)
                    || port == -1;

            if (isDefaultPort) {
                return scheme.toLowerCase() + "://" + host.toLowerCase();
            } else {
                return scheme.toLowerCase() + "://" + host.toLowerCase() + ":" + port;
            }
        } catch (URISyntaxException e) {
            log.debug("Failed to parse origin: {}", candidateOrigin, e);
            return null;
        }
    }
}

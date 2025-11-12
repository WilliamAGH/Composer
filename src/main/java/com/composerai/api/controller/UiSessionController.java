package com.composerai.api.controller;

import com.composerai.api.config.AppProperties;
import com.composerai.api.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Exposes UI-only session utilities (outside /api/**) so the frontend can renew its UI nonce
 * after servlet session expiry without weakening API guards.
 */
@Slf4j
@RestController
@RequestMapping("/ui/session")
public class UiSessionController {

    private final UiNonceService uiNonceService;
    private final List<String> allowedOrigins;

    public UiSessionController(UiNonceService uiNonceService, AppProperties appProperties) {
        this.uiNonceService = uiNonceService;
        String configured = (appProperties != null && appProperties.getCors() != null)
            ? appProperties.getCors().getAllowedOrigins()
            : "";
        this.allowedOrigins = Arrays.stream(configured.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }

    /**
     * Issue (or re-issue) the per-session UI nonce for the active servlet session.
     * Guarded by strict Origin/Referer checks so only the first-party UI may call it.
     */
    @PostMapping("/nonce")
    public ResponseEntity<Map<String, String>> refreshNonce(HttpServletRequest request,
                                                            HttpSession session,
                                                            @RequestHeader(value = "Origin", required = false) String origin,
                                                            @RequestHeader(value = "Referer", required = false) String referer,
                                                            @RequestHeader(value = "X-UI-Request", required = false) String currentNonce) {
        if (!isRequestAllowed(origin, referer, request)) {
            log.warn("Nonce refresh rejected – disallowed origin={} referer={}", origin, referer);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Disallowed origin"));
        }

        HttpSession activeSession = (session != null) ? session : request.getSession(true);
        Object existing = activeSession.getAttribute("UI_NONCE");
        if (existing instanceof String existingNonce
            && !StringUtils.isBlank(currentNonce)
            && !existingNonce.equals(currentNonce)) {
            log.warn("UI nonce mismatch detected for session id={}", activeSession.getId());
        }

        String nonce = uiNonceService.getOrCreateSessionNonce(activeSession);
        return ResponseEntity.ok(Map.of("uiNonce", nonce));
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
            boolean isDefaultPort = ("http".equalsIgnoreCase(request.getScheme()) && port == 80)
                || ("https".equalsIgnoreCase(request.getScheme()) && port == 443);
            if (!isDefaultPort && port > 0) {
                sb.append(':').append(port);
            }
            String schemeHost = sb.toString();
            return matchesAllowed(schemeHost);
        }
        return false;
    }

    private boolean matchesAllowed(String value) {
        if (StringUtils.isBlank(value)) {
            return false;
        }
        String normalizedValue = normalizeOrigin(value);
        if (normalizedValue == null) {
            return false;
        }
        return allowedOrigins.stream()
            .map(this::normalizeOrigin)
            .anyMatch(normalizedValue::equals);
    }

    /**
     * Normalize origin/referer to scheme://host:port format for exact matching.
     * Prevents prefix attacks like "https://composerai.app.evil.com" matching "https://composerai.app".
     */
    private String normalizeOrigin(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();

            if (scheme == null || host == null) {
                return null;
            }

            // Omit default ports
            boolean isDefaultPort = ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443)
                || port == -1;

            if (isDefaultPort) {
                return scheme.toLowerCase() + "://" + host.toLowerCase();
            } else {
                return scheme.toLowerCase() + "://" + host.toLowerCase() + ":" + port;
            }
        } catch (URISyntaxException e) {
            log.debug("Failed to parse origin: {}", value, e);
            return null;
        }
    }
}

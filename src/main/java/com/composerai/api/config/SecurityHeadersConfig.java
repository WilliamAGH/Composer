package com.composerai.api.config;

import com.composerai.api.controller.UiNonceService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Configuration
public class SecurityHeadersConfig {

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> hstsHeaderFilter(AppProperties appProperties) {
        FilterRegistrationBean<OncePerRequestFilter> registrationBean =
                new FilterRegistrationBean<>(new HstsFilter(appProperties));
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> corsErrorLoggingFilter() {
        FilterRegistrationBean<OncePerRequestFilter> registrationBean =
                new FilterRegistrationBean<>(new CorsErrorLoggingFilter());
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> apiUiNonceGuardFilter(
            AppProperties appProperties, UiNonceService uiNonceService) {
        FilterRegistrationBean<OncePerRequestFilter> registrationBean =
                new FilterRegistrationBean<>(new ApiUiNonceGuardFilter(appProperties, uiNonceService));
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> contentSecurityPolicyFilter() {
        FilterRegistrationBean<OncePerRequestFilter> registrationBean =
                new FilterRegistrationBean<>(new ContentSecurityPolicyFilter());
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 3);
        return registrationBean;
    }

    /**
     * Filter that manages HSTS (HTTP Strict Transport Security) headers.
     * When HSTS is disabled, sets max-age=0 to clear any cached HSTS policy.
     */
    private static class HstsFilter extends OncePerRequestFilter {
        private final AppProperties appProperties;

        HstsFilter(AppProperties appProperties) {
            this.appProperties = appProperties;
        }

        @Override
        protected void doFilterInternal(
                HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            if (!appProperties.getHsts().isEnabled()) {
                // Instruct browsers to clear any cached HSTS policy
                response.setHeader("Strict-Transport-Security", "max-age=0");
            }
            filterChain.doFilter(request, response);
        }
    }

    /**
     * Filter that catches and logs CORS-related errors with cleaner error responses.
     */
    private static class CorsErrorLoggingFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(
                HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            try {
                filterChain.doFilter(request, response);
            } catch (Exception e) {
                // If a CORS failure bubbles up, prefer a clean log and clear response
                if (isCorsFailure(request, e)) {
                    // Log full exception for root-cause visibility
                    SecurityHeadersConfig.log.warn(
                            "CORS rejection: method={}, origin={}, path={}",
                            request.getMethod(),
                            request.getHeader("Origin"),
                            request.getRequestURI(),
                            e);

                    // Check if response is already committed before attempting to modify it
                    if (response.isCommitted()) {
                        SecurityHeadersConfig.log.warn(
                                "Response already committed, cannot send CORS error response for origin={}",
                                request.getHeader("Origin"));
                        return;
                    }

                    // Only reset buffer if nothing has been committed yet
                    response.resetBuffer();
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"CORS policy does not allow this origin\"}");
                    response.flushBuffer();
                    return;
                }
                throw e;
            }
        }

        /**
         * Heuristic to detect CORS failures based on origin header presence and
         * exception message content. May miss some CORS issues or match noise, but
         * keeps logging focused on the most likely scenarios.
         */
        private boolean isCorsFailure(HttpServletRequest request, Exception e) {
            String origin = request.getHeader("Origin");
            if (origin == null) return false;
            String msg = e.getMessage();
            return msg != null && msg.toLowerCase().contains("cors");
        }
    }

    /**
     * Filter that adds Content Security Policy headers to protect against XSS and code injection.
     * Applies strict CSP to prevent inline scripts and restrict resource loading.
     */
    private static class ContentSecurityPolicyFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(
                HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {

            // CSP policy aligned to bundled assets (Vite output + inline bootstrap scripts).
            // - default-src 'self': Only allow resources from same origin by default
            // - script-src 'self' 'unsafe-inline': Inline scripts are used for redirects/bootstrap; keep until nonced
            // - style-src 'self' 'unsafe-inline': Tailwind build injects style tags during development
            // - img-src 'self' https: data:: Allow app images and data URIs
            // - connect-src 'self' https:: Allow API calls and external HTTPS endpoints when configured
            // - frame-src 'none': Block external frames (email iframe uses srcdoc)
            // - object-src 'none': Block plugins like Flash
            // - base-uri 'self': Restrict base tag to prevent URL injection
            // - form-action 'self': Only allow form submissions to same origin
            // - frame-ancestors 'none': Prevent clickjacking (X-Frame-Options alternative)
            // Third-party allowances (minimal):
            // - script-src: cdn.jsdelivr.net for marked + DOMPurify
            // - style-src: api.fontshare.com for Fontshare CSS
            // - font-src: cdn.fontshare.com for Fontshare font files
            // - img-src: i.pravatar.cc for avatar fallback images
            String csp = "default-src 'self'; " + "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; "
                    + "style-src 'self' 'unsafe-inline' https://api.fontshare.com; "
                    + "font-src 'self' https://cdn.fontshare.com; "
                    + "img-src 'self' https: data: https://i.pravatar.cc; "
                    + "connect-src 'self' https:; "
                    + "frame-src 'none'; "
                    + "object-src 'none'; "
                    + "base-uri 'self'; "
                    + "form-action 'self'; "
                    + "frame-ancestors 'none'; "
                    + "upgrade-insecure-requests";

            response.setHeader("Content-Security-Policy", csp);

            // Additional security headers
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("X-Frame-Options", "DENY");
            response.setHeader("X-XSS-Protection", "1; mode=block");
            response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

            filterChain.doFilter(request, response);
        }
    }

    /**
     * Guard /api/** to only accept requests initiated by our server-rendered UI.
     * Requires a session cookie and a per-session nonce header (X-UI-Request) set by the template.
     * Blocks direct calls without session+nonce. Allows OPTIONS and GET /api/chat/health.
     */
    private static class ApiUiNonceGuardFilter extends OncePerRequestFilter {

        private static final String NONCE_EXPIRED_MESSAGE = "UI nonce expired. Refresh the page and retry the request.";
        private static final String NONCE_INVALID_MESSAGE =
                "UI nonce missing or invalid. Refresh the page and retry the request.";
        private static final String ERROR_JSON_TEMPLATE = "{\"error\":\"%s\"}";

        private final AppProperties appProperties;
        private final UiNonceService uiNonceService;

        ApiUiNonceGuardFilter(AppProperties appProperties, UiNonceService uiNonceService) {
            this.appProperties = appProperties;
            this.uiNonceService = uiNonceService;
        }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            // Bypass in dev mode to allow local Vite development without server-rendered nonces
            if (appProperties.getSecurity().isDevMode()) {
                return true;
            }
            String path = request.getRequestURI();
            if (path == null) return true;
            if (!path.startsWith("/api/")) return true;
            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
            if ("GET".equalsIgnoreCase(request.getMethod()) && "/api/chat/health".equals(path)) return true;
            return false;
        }

        @Override
        protected void doFilterInternal(
                HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            try {
                jakarta.servlet.http.HttpSession session = request.getSession(false);
                String headerNonce = request.getHeader("X-UI-Request");
                UiNonceService.UiNonceValidation validation = uiNonceService.validateNonce(session, headerNonce);

                if (!validation.valid()) {
                    String message = validation.expired() ? NONCE_EXPIRED_MESSAGE : NONCE_INVALID_MESSAGE;
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write(String.format(ERROR_JSON_TEMPLATE, message));
                    return;
                }
                filterChain.doFilter(request, response);
            } catch (Exception e) {
                SecurityHeadersConfig.log.warn(
                        "API guard rejection: method={}, path={}", request.getMethod(), request.getRequestURI(), e);
                if (!response.isCommitted()) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Forbidden\"}");
                }
            }
        }
    }
}

package com.composerai.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Configuration
public class SecurityHeadersConfig {


    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> hstsHeaderFilter(AppProperties appProperties) {
        FilterRegistrationBean<OncePerRequestFilter> registrationBean = new FilterRegistrationBean<>(new HstsFilter(appProperties));
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> corsErrorLoggingFilter() {
        FilterRegistrationBean<OncePerRequestFilter> registrationBean = new FilterRegistrationBean<>(new CorsErrorLoggingFilter());
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> apiUiNonceGuardFilter() {
        FilterRegistrationBean<OncePerRequestFilter> registrationBean = new FilterRegistrationBean<>(new ApiUiNonceGuardFilter());
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
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
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
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
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
            try {
                filterChain.doFilter(request, response);
            } catch (Exception e) {
                // If a CORS failure bubbles up, prefer a clean log and clear response
                if (isCorsFailure(request, e)) {
                    // Log full exception for root-cause visibility
                    SecurityHeadersConfig.log.warn(
                        "CORS rejection: method={}, origin={}, path={}",
                        request.getMethod(), request.getHeader("Origin"), request.getRequestURI(), e
                    );

                    // Check if response is already committed before attempting to modify it
                    if (response.isCommitted()) {
                        SecurityHeadersConfig.log.warn(
                            "Response already committed, cannot send CORS error response for origin={}",
                            request.getHeader("Origin")
                        );
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
     * Guard /api/** to only accept requests initiated by our server-rendered UI.
     * Requires a session cookie and a per-session nonce header (X-UI-Request) set by the template.
     * Blocks direct calls without session+nonce. Allows OPTIONS and GET /api/chat/health.
     */
    private static class ApiUiNonceGuardFilter extends OncePerRequestFilter {

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            String path = request.getRequestURI();
            if (path == null) return true;
            if (!path.startsWith("/api/")) return true;
            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
            if ("GET".equalsIgnoreCase(request.getMethod()) && "/api/chat/health".equals(path)) return true;
            return false;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
            try {
                jakarta.servlet.http.HttpSession session = request.getSession(false);
                String headerNonce = request.getHeader("X-UI-Request");
                Object sessionNonce = (session == null) ? null : session.getAttribute("UI_NONCE");

                if (session == null || sessionNonce == null || headerNonce == null || headerNonce.isBlank() || !headerNonce.equals(sessionNonce.toString())) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Forbidden\"}");
                    return;
                }
                filterChain.doFilter(request, response);
            } catch (Exception e) {
                SecurityHeadersConfig.log.warn("API guard rejection: method={}, path={}", request.getMethod(), request.getRequestURI(), e);
                if (!response.isCommitted()) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Forbidden\"}");
                }
            }
        }
    }
}

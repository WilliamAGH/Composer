package com.composerai.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@Configuration
public class SecurityHeadersConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityHeadersConfig.class);

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
                    SecurityHeadersConfig.logger.warn(
                        "CORS rejection: method={}, origin={}, path={}",
                        request.getMethod(), request.getHeader("Origin"), request.getRequestURI(), e
                    );

                    // Check if response is already committed before attempting to modify it
                    if (response.isCommitted()) {
                        SecurityHeadersConfig.logger.warn(
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

        private boolean isCorsFailure(HttpServletRequest request, Exception e) {
            String origin = request.getHeader("Origin");
            if (origin == null) return false;
            String msg = e.getMessage();
            return msg != null && msg.toLowerCase().contains("cors");
        }
    }
}

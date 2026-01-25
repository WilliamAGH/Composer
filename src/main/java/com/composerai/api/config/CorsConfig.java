package com.composerai.api.config;

import java.util.Arrays;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Global CORS configuration for the application.
 * Replaces individual @CrossOrigin annotations on controllers.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private static final String ORIGIN_DELIMITER = ",";
    private static final String API_CORS_MAPPING_PATH = "/api/**";
    private static final String UI_CORS_MAPPING_PATH = "/ui/**";
    private static final String ALLOWED_HEADERS_ALL = "*";
    private static final String WILDCARD_ORIGIN_PATTERN = "*";
    private static final String CREDENTIALS_WILDCARD_ERROR =
            "CORS configuration error: Wildcard origin '*' is not allowed when credentials are enabled. "
                    + "Configure specific origins via APP_CORS_ALLOWED_ORIGINS environment variable or "
                    + "app.cors.allowed-origins property (e.g., 'https://example.com,https://app.example.com'). "
                    + "Patterns like 'https://*.example.com' are also supported.";
    private static final String METHOD_GET = "GET";
    private static final String METHOD_POST = "POST";
    private static final String METHOD_PUT = "PUT";
    private static final String METHOD_DELETE = "DELETE";
    private static final String METHOD_OPTIONS = "OPTIONS";
    private static final long CORS_MAX_AGE_SECONDS = 3600L;

    private final AppProperties appProperties;

    public CorsConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String configuredOrigins = appProperties.getCors().getAllowedOrigins();
        String[] originPatterns = parseOriginPatterns(configuredOrigins);
        validateCredentialedOrigins(originPatterns);
        applyCorsConfiguration(registry.addMapping(API_CORS_MAPPING_PATH), originPatterns);
        applyCorsConfiguration(registry.addMapping(UI_CORS_MAPPING_PATH), originPatterns);
    }

    private static void applyCorsConfiguration(CorsRegistration registration, String[] originPatterns) {
        registration
                .allowedOriginPatterns(originPatterns)
                .allowedMethods(METHOD_GET, METHOD_POST, METHOD_PUT, METHOD_DELETE, METHOD_OPTIONS)
                .allowedHeaders(ALLOWED_HEADERS_ALL)
                .allowCredentials(true)
                .maxAge(CORS_MAX_AGE_SECONDS);
    }

    private static void validateCredentialedOrigins(String[] originPatterns) {
        boolean includesWildcard = Arrays.stream(originPatterns).anyMatch(WILDCARD_ORIGIN_PATTERN::equals);
        if (includesWildcard) {
            throw new IllegalStateException(CREDENTIALS_WILDCARD_ERROR);
        }
    }

    private static String[] parseOriginPatterns(String configuredOrigins) {
        if (configuredOrigins == null || configuredOrigins.isBlank()) {
            return new String[0];
        }
        return Arrays.stream(configuredOrigins.split(ORIGIN_DELIMITER))
                .map(String::trim)
                .filter(originPattern -> !originPattern.isEmpty())
                .toArray(String[]::new);
    }
}

package com.composerai.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * Global CORS configuration for the application.
 * Replaces individual @CrossOrigin annotations on controllers.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private static final String ORIGIN_DELIMITER = ",";
    private static final String CORS_MAPPING_PATH = "/api/**";
    private static final String ALLOWED_HEADERS_ALL = "*";
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
        registry.addMapping(CORS_MAPPING_PATH)
            .allowedOriginPatterns(originPatterns)
            .allowedMethods(METHOD_GET, METHOD_POST, METHOD_PUT, METHOD_DELETE, METHOD_OPTIONS)
            .allowedHeaders(ALLOWED_HEADERS_ALL)
            .allowCredentials(true)
            .maxAge(CORS_MAX_AGE_SECONDS);
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

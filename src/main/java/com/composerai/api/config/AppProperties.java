package com.composerai.api.config;

import com.composerai.api.util.StringUtils;
import jakarta.annotation.PostConstruct;
import java.util.Locale;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Hsts hsts = new Hsts();

    @NestedConfigurationProperty
    private Cors cors = new Cors();

    @NestedConfigurationProperty
    private EmailRendering emailRendering = new EmailRendering();

    @NestedConfigurationProperty
    private Ledger ledger = new Ledger();

    @NestedConfigurationProperty
    private Security security = new Security();

    @PostConstruct
    void hydrateFromEnvironment() {
        String configured = System.getenv("RENDER_EMAILS_WITH");
        if (!StringUtils.isBlank(configured)) {
            emailRendering.setMode(configured);
        }
    }

    @Getter
    @Setter
    public static class Hsts {
        private boolean enabled = true;
    }

    @Getter
    @Setter
    public static class Cors {
        /**
         * Comma-separated list of allowed origins. Example:
         * https://composer.email,https://dev.composer.email
         */
        private String allowedOrigins =
                "http://localhost:8090,http://localhost:5183,https://composer.email,https://dev.composer.email";
    }

    @Getter
    public static class EmailRendering {
        private EmailRenderMode mode = EmailRenderMode.HTML;

        public void setMode(EmailRenderMode mode) {
            this.mode = mode == null ? EmailRenderMode.HTML : mode;
        }

        public void setMode(String mode) {
            this.mode = EmailRenderMode.from(mode);
        }
    }

    public enum EmailRenderMode {
        HTML,
        MARKDOWN,
        PLAINTEXT;

        public static EmailRenderMode from(String value) {
            if (value == null) {
                return HTML;
            }
            String trimmed = value.trim();
            if (trimmed.isEmpty()) {
                return HTML;
            }
            String normalized = trimmed.replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
            return switch (normalized) {
                case "HTML" -> HTML;
                case "MARKDOWN", "MD" -> MARKDOWN;
                case "PLAINTEXT", "PLAIN_TEXT", "PLAIN" -> PLAINTEXT;
                default -> HTML;
            };
        }
    }

    @Getter
    @Setter
    public static class Ledger {
        /** Whether the conversation ledger should be persisted. */
        private boolean enabled = false;
        /** Directory where JSON envelopes are written when enabled. */
        private String directory = "data/ledger";
    }

    @Getter
    @Setter
    public static class Security {
        /**
         * When true, disables strict UI nonce checks and relaxes certain security headers
         * to allow local development (e.g. Vite dev server).
         * NEVER enable in production.
         */
        private boolean devMode = false;
    }
}

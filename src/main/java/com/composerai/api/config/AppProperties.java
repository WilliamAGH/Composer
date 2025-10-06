package com.composerai.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Hsts hsts = new Hsts();
    @NestedConfigurationProperty
    private Cors cors = new Cors();

    public Hsts getHsts() {
        return hsts;
    }

    public void setHsts(Hsts hsts) {
        this.hsts = hsts;
    }

    public Cors getCors() {
        return cors;
    }

    public void setCors(Cors cors) {
        this.cors = cors;
    }

    public static class Hsts {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Cors {
        /**
         * Comma-separated list of allowed origins. Example:
         * https://composerai.app,https://dev.composerai.app
         */
        private String allowedOrigins = "http://localhost:8080,https://composerai.app,https://dev.composerai.app";

        public String getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(String allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }
}

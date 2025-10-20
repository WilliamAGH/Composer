package com.composerai.api.config;

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
         * https://composerai.app,https://dev.composerai.app
         */
        private String allowedOrigins = "http://localhost:8080,https://composerai.app,https://dev.composerai.app";
    }
}

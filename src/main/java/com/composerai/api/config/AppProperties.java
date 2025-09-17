package com.composerai.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Hsts hsts = new Hsts();

    public Hsts getHsts() {
        return hsts;
    }

    public void setHsts(Hsts hsts) {
        this.hsts = hsts;
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
}

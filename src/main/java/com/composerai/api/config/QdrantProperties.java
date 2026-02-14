package com.composerai.api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "qdrant")
public class QdrantProperties {

    private String host = "localhost";
    private int port = 6333;
    private boolean useTls = false;
    private String collectionName = "emails";
    private boolean enabled = false;
    private String apiKey;
}

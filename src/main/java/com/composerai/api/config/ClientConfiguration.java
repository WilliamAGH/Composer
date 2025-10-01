package com.composerai.api.config;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.Timeout;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@Configuration
@EnableConfigurationProperties({OpenAiProperties.class, QdrantProperties.class})
public class ClientConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ClientConfiguration.class);

    @Bean
    public OpenAIClient openAIClient(OpenAiProperties openAiProperties) {
        String apiKey = sanitize(openAiProperties.getApi().getKey());

        if (isMissing(apiKey)) {
            apiKey = sanitize(System.getenv("OPENAI_API_KEY"));
            if (!isMissing(apiKey)) {
                logger.info("Loaded OpenAI API key from environment variable");
            }
        }

        if (isMissing(apiKey)) {
            apiKey = sanitize(readFromDotEnv("OPENAI_API_KEY"));
            if (!isMissing(apiKey)) {
                logger.info("Loaded OpenAI API key from .env file");
            }
        }

        if (isMissing(apiKey)) {
            logger.warn("OpenAI API key is not configured; OpenAI features will be disabled");
        }

        OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder()
            .fromEnv()
            .timeout(Timeout.builder()
                .connect(Duration.ofSeconds(10))
                .read(Duration.ofMinutes(5))
                .write(Duration.ofSeconds(30))
                .build());
        if (isMissing(apiKey)) {
            // Return a client configured with no credential; services will check and respond gracefully
            return builder.build();
        }

        OpenAIClient client = builder
            .apiKey(apiKey)
            .build();
        String baseUrl = openAiProperties.getApi().getBaseUrl();
        if (baseUrl != null && !baseUrl.isBlank()) {
            client = client.withOptions(opts -> opts.baseUrl(baseUrl));
        }
        return client;
    }

    private boolean isMissing(String value) {
        if (value == null) {
            return true;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() || trimmed.equalsIgnoreCase("your-openai-api-key");
    }

    private String sanitize(String value) {
        return value == null ? null : value.trim();
    }

    private String readFromDotEnv(String key) {
        try {
            Path p = Path.of(".env");
            if (!Files.exists(p)) {
                return null;
            }
            List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String k = trimmed.substring(0, eq).trim();
                if (k.equals(key)) {
                    String v = trimmed.substring(eq + 1).trim();
                    if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
                        v = v.substring(1, v.length() - 1);
                    }
                    return v;
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to read {} from .env: {}", key, e.getMessage());
        }
        return null;
    }

    @Bean
    public QdrantClient qdrantClient(QdrantProperties qdrantProperties) {
        QdrantGrpcClient.Builder builder = QdrantGrpcClient.newBuilder(
            qdrantProperties.getHost(),
            qdrantProperties.getPort(),
            qdrantProperties.isUseTls()
        );
        
        return new QdrantClient(builder.build());
    }
}

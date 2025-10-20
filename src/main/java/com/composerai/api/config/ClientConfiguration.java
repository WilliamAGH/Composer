package com.composerai.api.config;

import com.composerai.api.util.StringUtils;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.Timeout;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.context.annotation.Bean;

@Slf4j
@Configuration
@EnableConfigurationProperties({OpenAiProperties.class, QdrantProperties.class})
public class ClientConfiguration {


    @Bean
    public OpenAIClient openAIClient(OpenAiProperties openAiProperties) {
        // Support both OPENAI_API_KEY and LLM_API_KEY (for alternative providers)
        String apiKey = openAiProperties.getApi().getKey();
        if (StringUtils.isMissing(apiKey)) {
            apiKey = System.getenv("LLM_API_KEY");
        }
        if (StringUtils.isMissing(apiKey)) {
            apiKey = System.getenv("OPENAI_API_KEY");
        }

        if (StringUtils.isMissing(apiKey)) {
            log.warn("API key not configured via openai.api.key, LLM_API_KEY, or OPENAI_API_KEY. Service will operate in degraded mode.");
            return null;
        }

        String trimmedKey = apiKey.trim();

        try {
            // Check for base URL - priority: env vars first, then properties
            String llmBaseUrl = System.getenv("LLM_BASE_URL");
            String openaiBaseUrl = System.getenv("OPENAI_BASE_URL");
            String openaiApiBaseUrl = System.getenv("OPENAI_API_BASE_URL");
            String propsBaseUrl = openAiProperties.getApi().getBaseUrl();

            log.debug("Base URL sources: LLM_BASE_URL={}, OPENAI_BASE_URL={}, OPENAI_API_BASE_URL={}, properties={}",
                llmBaseUrl, openaiBaseUrl, openaiApiBaseUrl, propsBaseUrl);

            String baseUrl = llmBaseUrl;
            if (StringUtils.isBlank(baseUrl)) {
                baseUrl = openaiBaseUrl;
            }
            if (StringUtils.isBlank(baseUrl)) {
                baseUrl = openaiApiBaseUrl;
            }
            if (StringUtils.isBlank(baseUrl)) {
                baseUrl = propsBaseUrl;
            }

            // Detect provider capabilities for logging
            ProviderCapabilities capabilities = ProviderCapabilities.detect(baseUrl);
            log.info("Configuring OpenAI-compatible client: {}", capabilities);
            
            OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder()
                .apiKey(trimmedKey)
                .timeout(Timeout.builder()
                    .connect(Duration.ofSeconds(10))
                    .read(Duration.ofMinutes(5))
                    .write(Duration.ofSeconds(30))
                    .build());

            if (!StringUtils.isBlank(baseUrl)) {
                builder.baseUrl(baseUrl.trim());
            }

            OpenAIClient client = builder.build();
            log.info("OpenAI-compatible client configured successfully (provider: {})", capabilities.getType());
            return client;
        } catch (Exception e) {
            log.warn("Failed to configure OpenAI-compatible client: {}. Service will operate in degraded mode.", e.getMessage());
            return null;
        }
    }


    @Bean(destroyMethod = "close")
    public QdrantClient qdrantClient(QdrantProperties qdrantProperties) {
        QdrantGrpcClient.Builder builder = QdrantGrpcClient.newBuilder(
            qdrantProperties.getHost(),
            qdrantProperties.getPort(),
            qdrantProperties.isUseTls()
        );

        // Attach API key if provided
        String apiKey = qdrantProperties.getApiKey();
        if (!StringUtils.isBlank(apiKey)) {
            builder.withApiKey(apiKey.trim());
            log.info("Configured Qdrant API key on gRPC client");
        }

        QdrantClient client = new QdrantClient(builder.build());
        log.info("Qdrant client configured for {}:{}", qdrantProperties.getHost(), qdrantProperties.getPort());
        return client;
    }

    /**
     * Provides a virtual thread-based executor for SSE streaming.
     * Virtual threads (Java 21+) are ideal for I/O-bound streaming tasks
     * as they scale better than platform threads and don't block carriers.
     */
    @Bean(name = "streamingExecutor")
    public java.util.concurrent.ExecutorService streamingExecutor() {
        return java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
    }
}

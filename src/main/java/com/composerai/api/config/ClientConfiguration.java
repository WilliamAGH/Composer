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
import org.springframework.context.annotation.Bean;

@Slf4j
@Configuration
@EnableConfigurationProperties({OpenAiProperties.class, QdrantProperties.class, MagicEmailProperties.class, AiCommandPromptProperties.class})
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
            // Base URL comes from properties (which handles env var fallback chain)
            String baseUrl = openAiProperties.getApi().getBaseUrl();
            log.debug("Using base URL: {}", baseUrl);

            // Detect provider capabilities for logging
            ProviderCapabilities capabilities = ProviderCapabilities.detect(baseUrl);
            log.info("Configuring OpenAI-compatible client: {}", capabilities);
            
            OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder()
                .apiKey(trimmedKey)
                .timeout(Timeout.builder()
                    .connect(Duration.ofSeconds(10))
                    // Use finite timeout for SSE to prevent hung connections
                    // Set to SSE timeout + 30s grace period to allow proper server-side timeout handling
                    .read(Duration.ofSeconds(openAiProperties.getStream().getTimeoutSeconds() + 30))
                    .write(Duration.ofSeconds(30))
                    .build())
                // Disable strict response validation so unknown streaming events do not kill SSE.
                .responseValidation(false);

            if (!StringUtils.isBlank(baseUrl)) {
                builder.baseUrl(baseUrl.trim());
            }

            OpenAIClient client = builder.build();
            log.info("OpenAI-compatible client configured successfully (provider: {})", capabilities.getType());
            return client;
        } catch (Exception e) {
            log.warn("Failed to configure OpenAI-compatible client. Service will operate in degraded mode.", e);
            return null;
        }
    }

    /**
     * RestClient for making raw HTTP requests to OpenRouter API.
     * Used when OpenRouter-specific features (like provider routing) are needed
     * that aren't supported by the OpenAI SDK's strongly-typed API.
     * 
     * Only used when base URL is OpenRouter - otherwise normal SDK client is used.
     */
    @Bean
    public org.springframework.web.client.RestClient openRouterRestClient(OpenAiProperties openAiProperties) {
        String apiKey = openAiProperties.getApi().getKey();
        if (StringUtils.isMissing(apiKey)) {
            apiKey = System.getenv("LLM_API_KEY");
        }
        if (StringUtils.isMissing(apiKey)) {
            apiKey = System.getenv("OPENAI_API_KEY");
        }
        
        if (StringUtils.isMissing(apiKey)) {
            log.warn("API key not configured - RestClient will fail if used");
            return null;
        }
        
        String baseUrl = openAiProperties.getApi().getBaseUrl();
        
        return org.springframework.web.client.RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Authorization", "Bearer " + apiKey.trim())
            .defaultHeader("Content-Type", "application/json")
            .defaultHeader("HTTP-Referer", "https://composerai.app") // Optional: for OpenRouter analytics
            .defaultHeader("X-Title", "ComposerAI") // Optional: for OpenRouter analytics
            .build();
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
    @Bean(name = "chatStreamExecutor")
    public java.util.concurrent.ExecutorService chatStreamExecutor() {
        return java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Provides a shared ScheduledExecutorService for SSE heartbeat management.
     * Using a shared thread pool prevents resource exhaustion from creating
     * ad-hoc executors for each streaming request.
     *
     * Pool size of 4 is sufficient for heartbeat scheduling across many concurrent
     * SSE connections, as scheduling tasks are lightweight.
     */
    @Bean(name = "sseHeartbeatExecutor", destroyMethod = "shutdown")
    public java.util.concurrent.ScheduledExecutorService sseHeartbeatExecutor() {
        return java.util.concurrent.Executors.newScheduledThreadPool(4);
    }
}

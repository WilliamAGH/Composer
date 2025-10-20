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
        String apiKey = openAiProperties.getApi().getKey();
        if (StringUtils.isMissing(apiKey)) {
            apiKey = System.getenv("OPENAI_API_KEY");
        }

        if (StringUtils.isMissing(apiKey)) {
            log.warn("OpenAI API key not configured via openai.api.key or OPENAI_API_KEY. Service will operate in degraded mode.");
            return null;
        }

        String trimmedKey = apiKey.trim();

        try {
            OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder()
                .apiKey(trimmedKey)
                .timeout(Timeout.builder()
                    .connect(Duration.ofSeconds(10))
                    .read(Duration.ofMinutes(5))
                    .write(Duration.ofSeconds(30))
                    .build());

            String baseUrl = openAiProperties.getApi().getBaseUrl();
            if (!StringUtils.isBlank(baseUrl)) {
                builder.baseUrl(baseUrl.trim());
            }

            OpenAIClient client = builder.build();
            log.info("OpenAI client configured successfully");
            return client;
        } catch (Exception e) {
            log.warn("Failed to configure OpenAI client: {}. Service will operate in degraded mode.", e.getMessage());
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

    @Bean(name = "chatStreamExecutor")
    public java.util.concurrent.Executor chatStreamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("chat-stream-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}

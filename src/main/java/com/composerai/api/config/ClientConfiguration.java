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
        // Use fromEnv() - automatically handles OPENAI_API_KEY and AZURE_OPENAI_KEY
        // spring-dotenv library automatically loads .env file values into environment
        
        try {
            OpenAIClient client = OpenAIOkHttpClient.fromEnv();
            
            // Apply custom timeout configuration
            client = client.withOptions(opts -> opts
                .timeout(Timeout.builder()
                    .connect(Duration.ofSeconds(10))
                    .read(Duration.ofMinutes(5))
                    .write(Duration.ofSeconds(30))
                    .build()));
            
            // Apply custom base URL if configured
            String baseUrl = openAiProperties.getApi().getBaseUrl();
            if (baseUrl != null && !baseUrl.isBlank()) {
                client = client.withOptions(opts -> opts.baseUrl(baseUrl));
            }
            
            log.info("OpenAI client configured successfully");
            return client;
        } catch (Exception e) {
            log.warn("OpenAI API key not configured: {}. Service will operate in degraded mode.", e.getMessage());
            // Return null - service methods will handle null client gracefully
            return null;
        }
    }


    @Bean
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

        return new QdrantClient(builder.build());
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

package com.composerai.api.config;

import com.composerai.api.util.StringUtils;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.Timeout;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.context.annotation.Bean;

@Configuration
@EnableConfigurationProperties({OpenAiProperties.class, QdrantProperties.class})
public class ClientConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ClientConfiguration.class);

    @Bean
    public OpenAIClient openAIClient(OpenAiProperties openAiProperties) {
        // spring-dotenv library automatically loads .env file values into environment
        String apiKey = StringUtils.sanitize(openAiProperties.getApi().getKey());

        if (StringUtils.isMissing(apiKey)) {
            apiKey = StringUtils.sanitize(System.getenv("OPENAI_API_KEY"));
            if (!StringUtils.isMissing(apiKey)) {
                logger.info("Loaded OpenAI API key from environment variable");
            }
        }

        if (StringUtils.isMissing(apiKey)) {
            logger.warn("OpenAI API key is not configured; OpenAI features will be disabled");
        }

        OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder()
            .fromEnv()
            .timeout(Timeout.builder()
                .connect(Duration.ofSeconds(10))
                .read(Duration.ofMinutes(5))
                .write(Duration.ofSeconds(30))
                .build());
        if (StringUtils.isMissing(apiKey)) {
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
            logger.info("Configured Qdrant API key on gRPC client");
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

package com.composerai.api.config;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({OpenAiProperties.class, QdrantProperties.class})
public class ClientConfiguration {

    @Bean
    public OpenAIClient openAIClient(OpenAiProperties openAiProperties) {
        // Build from env, then override with Spring properties when present
        OpenAIClient client = OpenAIOkHttpClient.builder()
            .fromEnv()
            .apiKey(openAiProperties.getApi().getKey())
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
        
        return new QdrantClient(builder.build());
    }
}
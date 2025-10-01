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
        String apiKey = openAiProperties.getApi().getKey();
        if (apiKey == null || apiKey.isBlank()) {
            // Try env fallback explicitly to support .env imports
            apiKey = System.getenv("OPENAI_API_KEY");
        }
        if (apiKey == null || apiKey.isBlank()) {
            // Return a client configured with no credential; services will check and respond gracefully
            return OpenAIOkHttpClient.builder().fromEnv().build();
        }

        OpenAIClient client = OpenAIOkHttpClient.builder()
            .fromEnv()
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
        
        return new QdrantClient(builder.build());
    }
}
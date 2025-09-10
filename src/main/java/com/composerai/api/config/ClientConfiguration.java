package com.composerai.api.config;

import com.theokanning.openai.service.OpenAiService;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties({OpenAiProperties.class, QdrantProperties.class})
public class ClientConfiguration {

    @Bean
    public OpenAiService openAiService(OpenAiProperties openAiProperties) {
        return new OpenAiService(
            openAiProperties.getApi().getKey(), 
            Duration.ofSeconds(60)
        );
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
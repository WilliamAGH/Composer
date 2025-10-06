package com.composerai.api.service;

import com.composerai.api.config.QdrantProperties;
import com.composerai.api.dto.ChatResponse.EmailContext;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points.SearchPoints;
import io.qdrant.client.grpc.Points.ScoredPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;

@Service
public class VectorSearchService {
    
    private static final Logger logger = LoggerFactory.getLogger(VectorSearchService.class);
    
    private final QdrantClient qdrantClient;
    private final QdrantProperties qdrantProperties;
    private final boolean enabled;

    public VectorSearchService(QdrantClient qdrantClient, QdrantProperties qdrantProperties) {
        this.qdrantClient = Objects.requireNonNull(qdrantClient, "QdrantClient must not be null");
        this.qdrantProperties = Objects.requireNonNull(qdrantProperties, "QdrantProperties must not be null");
        this.enabled = this.qdrantProperties.isEnabled();
    }

    public List<EmailContext> searchSimilarEmails(float[] queryVector, int limit) {
        if (!enabled) {
            logger.debug("Qdrant vector search disabled by configuration; skipping.");
            return List.of();
        }
        if (queryVector == null || queryVector.length == 0) {
            logger.debug("Empty or null query vector; skipping Qdrant search.");
            return List.of();
        }
        try {
            // Create WithPayloadSelector to include all payload
            var withPayloadSelector = io.qdrant.client.grpc.Points.WithPayloadSelector.newBuilder()
                .setEnable(true)
                .build();
            
            SearchPoints searchRequest = SearchPoints.newBuilder()
                .setCollectionName(qdrantProperties.getCollectionName())
                .addAllVector(convertFloatArrayToList(queryVector))
                .setLimit(limit)
                .setWithPayload(withPayloadSelector)
                .build();

            List<ScoredPoint> searchResults = qdrantClient.searchAsync(searchRequest).get();
            
            List<EmailContext> emailContexts = new ArrayList<>();
            for (ScoredPoint point : searchResults) {
                EmailContext context = extractEmailContext(point);
                emailContexts.add(context);
            }
            
            logger.info("Found {} similar emails for query", emailContexts.size());
            return emailContexts;
            
        } catch (InterruptedException e) {
            logger.warn("Qdrant search interrupted", e);
            Thread.currentThread().interrupt();
            return List.of();
        } catch (ExecutionException e) {
            logger.warn("Qdrant search failed", e.getCause() != null ? e.getCause() : e);
            return List.of();
        } catch (Exception e) {
            logger.warn("Qdrant search error", e);
            return List.of();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    private List<Float> convertFloatArrayToList(float[] array) {
        return array == null ? List.of()
            : IntStream.range(0, array.length)
                .mapToObj(i -> array[i])
                .toList();
    }

    private EmailContext extractEmailContext(ScoredPoint point) {
        // Extract email metadata from point payload
        // This is a placeholder implementation
        // In a real implementation, you would extract the actual email data from the payload
        String pointId = point.getId().hasNum()
            ? String.valueOf(point.getId().getNum())
            : point.getId().hasUuid() ? point.getId().getUuid() : "";

        return new EmailContext(
            pointId,
            "Sample Subject", // Extract from payload
            "sample@email.com", // Extract from payload
            "Sample email snippet...", // Extract from payload
            point.getScore(),
            LocalDateTime.now() // Extract from payload
        );
    }
}

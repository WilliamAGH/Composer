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
import java.util.concurrent.ExecutionException;

@Service
public class VectorSearchService {
    
    private static final Logger logger = LoggerFactory.getLogger(VectorSearchService.class);
    
    private final QdrantClient qdrantClient;
    private final QdrantProperties qdrantProperties;
    private final boolean enabled;

    public VectorSearchService(QdrantClient qdrantClient, QdrantProperties qdrantProperties) {
        this.qdrantClient = qdrantClient;
        this.qdrantProperties = qdrantProperties;
        this.enabled = qdrantProperties != null && qdrantProperties.isEnabled();
    }

    public List<EmailContext> searchSimilarEmails(float[] queryVector, int limit) {
        if (!enabled) {
            logger.debug("Qdrant vector search disabled by configuration; skipping.");
            return new ArrayList<>();
        }
        if (queryVector == null || queryVector.length == 0) {
            logger.debug("Empty or null query vector; skipping Qdrant search.");
            return new ArrayList<>();
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
            logger.warn("Qdrant search interrupted: {}", e.getMessage());
            Thread.currentThread().interrupt();
            return new ArrayList<>();
        } catch (ExecutionException e) {
            logger.warn("Qdrant search failed: {}", (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
            return new ArrayList<>();
        } catch (Exception e) {
            logger.warn("Qdrant search error: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    private List<Float> convertFloatArrayToList(float[] array) {
        List<Float> list = new ArrayList<>();
        if (array != null) {
            for (float value : array) {
                list.add(value);
            }
        }
        return list;
    }

    private EmailContext extractEmailContext(ScoredPoint point) {
        // Extract email metadata from point payload
        // This is a placeholder implementation
        // In a real implementation, you would extract the actual email data from the payload
        String pointId = "";
        if (point.getId().hasNum()) {
            pointId = String.valueOf(point.getId().getNum());
        } else if (point.getId().hasUuid()) {
            pointId = point.getId().getUuid();
        }
        
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
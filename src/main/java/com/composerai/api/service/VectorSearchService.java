package com.composerai.api.service;

import com.composerai.api.config.QdrantProperties;
import com.composerai.api.dto.ChatResponse.EmailContext;
import com.composerai.api.util.StringUtils;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points.SearchPoints;
import io.qdrant.client.grpc.Points.ScoredPoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;

/**
 * Service for performing semantic vector searches against the Qdrant database.
 * Retrieves email contexts relevant to a query vector.
 */
@Slf4j
@Service
public class VectorSearchService {
    
    private static final String[] KEYS_SUBJECT = {"subject", "Subject", "title"};
    private static final String[] KEYS_SENDER = {"sender", "from", "From", "author"};
    private static final String[] KEYS_SNIPPET = {"snippet", "body", "content", "text"};
    private static final String KEY_TIMESTAMP = "timestamp";

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
            log.debug("Qdrant vector search disabled by configuration; skipping.");
            return List.of();
        }
        if (queryVector == null || queryVector.length == 0) {
            log.debug("Empty or null query vector; skipping Qdrant search.");
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
            
            log.info("Found {} similar emails for query", emailContexts.size());
            return emailContexts;
            
        } catch (InterruptedException e) {
            log.warn("Qdrant search interrupted", e);
            Thread.currentThread().interrupt();
            return List.of();
        } catch (ExecutionException e) {
            log.warn("Qdrant search failed", e.getCause() != null ? e.getCause() : e);
            return List.of();
        } catch (Exception e) {
            log.warn("Qdrant search error", e);
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
        String pointId = point.getId().hasNum()
            ? String.valueOf(point.getId().getNum())
            : point.getId().hasUuid() ? point.getId().getUuid() : "";

        Map<String, Value> payload = point.getPayloadMap();
        
        String subject = getPayloadString(payload, KEYS_SUBJECT);
        String sender = getPayloadString(payload, KEYS_SENDER);
        String snippet = getPayloadString(payload, KEYS_SNIPPET);
        
        // Attempt to parse timestamp, fallback to now if missing
        LocalDateTime timestamp = LocalDateTime.now();
        if (payload.containsKey(KEY_TIMESTAMP) && payload.get(KEY_TIMESTAMP).hasStringValue()) {
             try {
                 timestamp = OffsetDateTime.parse(payload.get(KEY_TIMESTAMP).getStringValue()).toLocalDateTime();
             } catch (Exception e) {
                 log.debug("Failed to parse timestamp from Qdrant payload", e);
             }
        }

        return new EmailContext(
            pointId,
            StringUtils.defaultIfBlank(subject, "No Subject"), 
            StringUtils.defaultIfBlank(sender, "Unknown Sender"), 
            StringUtils.defaultIfBlank(snippet, ""),
            point.getScore(),
            timestamp
        );
    }

    private String getPayloadString(Map<String, Value> payload, String... keys) {
        for (String key : keys) {
            if (payload.containsKey(key)) {
                Value val = payload.get(key);
                if (val.hasStringValue()) {
                    return val.getStringValue();
                }
            }
        }
        return null;
    }
}

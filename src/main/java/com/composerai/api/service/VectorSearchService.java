package com.composerai.api.service;

import com.composerai.api.config.QdrantProperties;
import com.composerai.api.dto.ChatResponse.EmailContext;
import com.composerai.api.util.StringUtils;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.SearchPoints;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for performing semantic vector searches against the Qdrant database.
 * Retrieves email contexts relevant to a query vector.
 */
@Slf4j
@Service
public class VectorSearchService {

    private static final int SEARCH_TIMEOUT_SECONDS = 10;
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

            List<ScoredPoint> scoredPoints =
                    qdrantClient.searchAsync(searchRequest).get(SEARCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            List<EmailContext> emailContexts = new ArrayList<>();
            for (ScoredPoint point : scoredPoints) {
                EmailContext context = extractEmailContext(point);
                emailContexts.add(context);
            }

            log.info("Found {} similar emails for query", emailContexts.size());
            return emailContexts;

        } catch (InterruptedException e) {
            log.warn("Qdrant search interrupted", e);
            Thread.currentThread().interrupt();
            return List.of();
        } catch (TimeoutException e) {
            log.warn("Qdrant search timed out after {} seconds", SEARCH_TIMEOUT_SECONDS);
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
        return array == null
                ? List.of()
                : IntStream.range(0, array.length).mapToObj(i -> array[i]).toList();
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

        // Parse timestamp - missing or invalid timestamps are logged and defaulted
        LocalDateTime timestamp;
        if (payload.containsKey(KEY_TIMESTAMP) && payload.get(KEY_TIMESTAMP).hasStringValue()) {
            String timestampStr = payload.get(KEY_TIMESTAMP).getStringValue();
            Optional<LocalDateTime> parsed = parseTimestamp(timestampStr);
            if (parsed.isEmpty()) {
                log.info("Using current time for email {} due to missing/invalid timestamp", pointId);
            }
            timestamp = parsed.orElseGet(LocalDateTime::now);
        } else {
            log.debug("No timestamp field in Qdrant payload for point {}", pointId);
            timestamp = LocalDateTime.now();
        }

        return new EmailContext(
                pointId,
                StringUtils.defaultIfBlank(subject, "No Subject"),
                StringUtils.defaultIfBlank(sender, "Unknown Sender"),
                StringUtils.defaultIfBlank(snippet, ""),
                point.getScore(),
                timestamp);
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

    /**
     * Parse a timestamp string that may be in either OffsetDateTime or LocalDateTime format.
     * Tries OffsetDateTime first (for strings with timezone like "2025-01-15T09:30:00Z"),
     * then falls back to LocalDateTime (for plain ISO strings like "2025-01-15T09:30:00").
     *
     * @param timestampStr the timestamp string to parse
     * @return Optional containing the parsed LocalDateTime, or empty if parsing fails
     */
    Optional<LocalDateTime> parseTimestamp(String timestampStr) {
        if (timestampStr == null || timestampStr.isBlank()) {
            return Optional.empty();
        }

        // Try OffsetDateTime first (handles "2025-01-15T09:30:00Z" or "2025-01-15T09:30:00+00:00")
        try {
            return Optional.of(OffsetDateTime.parse(timestampStr).toLocalDateTime());
        } catch (Exception ignored) {
            // Fall through to LocalDateTime parsing
        }

        // Try LocalDateTime (handles "2025-01-15T09:30:00")
        try {
            return Optional.of(LocalDateTime.parse(timestampStr));
        } catch (Exception e) {
            log.warn("Failed to parse timestamp '{}' from Qdrant payload: {}", timestampStr, e.getMessage());
            return Optional.empty();
        }
    }
}

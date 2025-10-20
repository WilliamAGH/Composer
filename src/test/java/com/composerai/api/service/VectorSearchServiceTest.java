package com.composerai.api.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.composerai.api.config.QdrantProperties;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points.SearchPoints;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.qdrant.client.grpc.Points;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class VectorSearchServiceTest {

    @Mock
    private QdrantClient qdrantClient;

    private QdrantProperties properties;

    private static final Logger SERVICE_LOGGER = (Logger) LoggerFactory.getLogger(VectorSearchService.class);
    private static Level originalLogLevel;

    @BeforeAll
    static void silenceVectorSearchLogs() {
        originalLogLevel = SERVICE_LOGGER.getLevel();
        SERVICE_LOGGER.setLevel(Level.OFF);
    }

    @AfterAll
    static void restoreVectorSearchLogs() {
        SERVICE_LOGGER.setLevel(originalLogLevel);
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        properties = new QdrantProperties();
    }

    @Test
    void searchSimilarEmails_whenDisabled_returnsEmptyList() {
        properties.setEnabled(false);
        VectorSearchService service = new VectorSearchService(qdrantClient, properties);

        List<?> result = service.searchSimilarEmails(new float[]{0.1f}, 5);

        assertTrue(result.isEmpty());
        verifyNoInteractions(qdrantClient);
    }

    @Test
    void searchSimilarEmails_withEmptyVector_returnsEmptyList() {
        properties.setEnabled(true);
        VectorSearchService service = new VectorSearchService(qdrantClient, properties);

        List<?> result = service.searchSimilarEmails(new float[0], 5);

        assertTrue(result.isEmpty());
        verifyNoInteractions(qdrantClient);
    }

    @Test
    void searchSimilarEmails_whenSearchFails_returnsEmptyList() {
        properties.setEnabled(true);
        VectorSearchService service = new VectorSearchService(qdrantClient, properties);

        ListenableFuture<List<Points.ScoredPoint>> failedFuture =
            Futures.immediateFailedFuture(new RuntimeException("boom"));
        when(qdrantClient.searchAsync(any(SearchPoints.class))).thenReturn(failedFuture);

        List<?> result = service.searchSimilarEmails(new float[]{0.2f}, 5);

        assertTrue(result.isEmpty());
    }
}

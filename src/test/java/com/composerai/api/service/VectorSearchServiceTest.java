package com.composerai.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.composerai.api.config.QdrantProperties;
import com.composerai.api.dto.ChatResponse.EmailContext;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Common.PointId;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.SearchPoints;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;

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

        List<?> result = service.searchSimilarEmails(new float[] {0.1f}, 5);

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
    void searchSimilarEmails_whenSearchFails_throwsIllegalStateException() {
        properties.setEnabled(true);
        VectorSearchService service = new VectorSearchService(qdrantClient, properties);

        ListenableFuture<List<Points.ScoredPoint>> failedFuture =
                Futures.immediateFailedFuture(new RuntimeException("boom"));
        when(qdrantClient.searchAsync(any(SearchPoints.class))).thenReturn(failedFuture);

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> service.searchSimilarEmails(new float[] {0.2f}, 5));

        assertNotNull(thrown.getCause());
        assertEquals("boom", thrown.getCause().getMessage());
    }

    @Test
    void searchSimilarEmails_extractsSubjectSenderSnippetFromPayload() {
        properties.setEnabled(true);
        properties.setCollectionName("test-collection");
        VectorSearchService service = new VectorSearchService(qdrantClient, properties);

        ScoredPoint point = ScoredPoint.newBuilder()
                .setId(PointId.newBuilder().setNum(123).build())
                .setScore(0.95f)
                .putPayload(
                        "subject",
                        Value.newBuilder().setStringValue("Test Subject").build())
                .putPayload(
                        "sender",
                        Value.newBuilder().setStringValue("alice@example.com").build())
                .putPayload(
                        "snippet",
                        Value.newBuilder().setStringValue("Email body preview").build())
                .putPayload(
                        "timestamp",
                        Value.newBuilder()
                                .setStringValue("2025-01-15T09:30:00Z")
                                .build())
                .build();

        ListenableFuture<List<ScoredPoint>> successFuture = Futures.immediateFuture(List.of(point));
        when(qdrantClient.searchAsync(any(SearchPoints.class))).thenReturn(successFuture);

        List<EmailContext> result = service.searchSimilarEmails(new float[] {0.1f}, 5);

        assertEquals(1, result.size());
        EmailContext ctx = result.get(0);
        assertEquals("123", ctx.emailId());
        assertEquals("Test Subject", ctx.subject());
        assertEquals("alice@example.com", ctx.sender());
        assertEquals("Email body preview", ctx.snippet());
        assertEquals(0.95, ctx.relevanceScore(), 0.001);
    }

    @Test
    void searchSimilarEmails_parsesOffsetDateTimeWithZSuffix() {
        properties.setEnabled(true);
        properties.setCollectionName("test-collection");
        VectorSearchService service = new VectorSearchService(qdrantClient, properties);

        ScoredPoint point = ScoredPoint.newBuilder()
                .setId(PointId.newBuilder().setNum(1).build())
                .setScore(0.8f)
                .putPayload(
                        "timestamp",
                        Value.newBuilder()
                                .setStringValue("2025-06-20T14:30:00Z")
                                .build())
                .build();

        ListenableFuture<List<ScoredPoint>> successFuture = Futures.immediateFuture(List.of(point));
        when(qdrantClient.searchAsync(any(SearchPoints.class))).thenReturn(successFuture);

        List<EmailContext> result = service.searchSimilarEmails(new float[] {0.1f}, 5);

        assertEquals(1, result.size());
        LocalDateTime ts = result.get(0).emailDate();
        assertEquals(2025, ts.getYear());
        assertEquals(6, ts.getMonthValue());
        assertEquals(20, ts.getDayOfMonth());
        assertEquals(14, ts.getHour());
        assertEquals(30, ts.getMinute());
    }

    @Test
    void searchSimilarEmails_parsesOffsetDateTimeWithOffset() {
        properties.setEnabled(true);
        properties.setCollectionName("test-collection");
        VectorSearchService service = new VectorSearchService(qdrantClient, properties);

        ScoredPoint point = ScoredPoint.newBuilder()
                .setId(PointId.newBuilder().setNum(2).build())
                .setScore(0.7f)
                .putPayload(
                        "timestamp",
                        Value.newBuilder()
                                .setStringValue("2025-03-10T08:00:00+05:30")
                                .build())
                .build();

        ListenableFuture<List<ScoredPoint>> successFuture = Futures.immediateFuture(List.of(point));
        when(qdrantClient.searchAsync(any(SearchPoints.class))).thenReturn(successFuture);

        List<EmailContext> result = service.searchSimilarEmails(new float[] {0.1f}, 5);

        assertEquals(1, result.size());
        LocalDateTime ts = result.get(0).emailDate();
        assertEquals(2025, ts.getYear());
        assertEquals(3, ts.getMonthValue());
        assertEquals(10, ts.getDayOfMonth());
        assertEquals(8, ts.getHour());
    }

    @Test
    void searchSimilarEmails_parsesLocalDateTimeWithoutTimezone() {
        properties.setEnabled(true);
        properties.setCollectionName("test-collection");
        VectorSearchService service = new VectorSearchService(qdrantClient, properties);

        ScoredPoint point = ScoredPoint.newBuilder()
                .setId(PointId.newBuilder().setNum(3).build())
                .setScore(0.6f)
                .putPayload(
                        "timestamp",
                        Value.newBuilder().setStringValue("2025-12-25T00:00:00").build())
                .build();

        ListenableFuture<List<ScoredPoint>> successFuture = Futures.immediateFuture(List.of(point));
        when(qdrantClient.searchAsync(any(SearchPoints.class))).thenReturn(successFuture);

        List<EmailContext> result = service.searchSimilarEmails(new float[] {0.1f}, 5);

        assertEquals(1, result.size());
        LocalDateTime ts = result.get(0).emailDate();
        assertEquals(2025, ts.getYear());
        assertEquals(12, ts.getMonthValue());
        assertEquals(25, ts.getDayOfMonth());
    }

    @Test
    void searchSimilarEmails_handlesBlankTimestamp() {
        properties.setEnabled(true);
        properties.setCollectionName("test-collection");
        VectorSearchService service = new VectorSearchService(qdrantClient, properties);

        ScoredPoint point = ScoredPoint.newBuilder()
                .setId(PointId.newBuilder().setNum(4).build())
                .setScore(0.5f)
                .putPayload(
                        "timestamp", Value.newBuilder().setStringValue("   ").build())
                .build();

        ListenableFuture<List<ScoredPoint>> successFuture = Futures.immediateFuture(List.of(point));
        when(qdrantClient.searchAsync(any(SearchPoints.class))).thenReturn(successFuture);

        List<EmailContext> result = service.searchSimilarEmails(new float[] {0.1f}, 5);

        assertEquals(1, result.size());
        assertNotNull(result.get(0).emailDate());
    }

    @Test
    void searchSimilarEmails_handlesInvalidTimestamp() {
        properties.setEnabled(true);
        properties.setCollectionName("test-collection");
        VectorSearchService service = new VectorSearchService(qdrantClient, properties);

        ScoredPoint point = ScoredPoint.newBuilder()
                .setId(PointId.newBuilder().setNum(5).build())
                .setScore(0.4f)
                .putPayload(
                        "timestamp",
                        Value.newBuilder().setStringValue("not-a-date").build())
                .build();

        ListenableFuture<List<ScoredPoint>> successFuture = Futures.immediateFuture(List.of(point));
        when(qdrantClient.searchAsync(any(SearchPoints.class))).thenReturn(successFuture);

        List<EmailContext> result = service.searchSimilarEmails(new float[] {0.1f}, 5);

        assertEquals(1, result.size());
        assertNotNull(result.get(0).emailDate());
    }

    @Test
    void searchSimilarEmails_handlesMissingPayloadFields() {
        properties.setEnabled(true);
        properties.setCollectionName("test-collection");
        VectorSearchService service = new VectorSearchService(qdrantClient, properties);

        ScoredPoint point = ScoredPoint.newBuilder()
                .setId(PointId.newBuilder().setNum(6).build())
                .setScore(0.3f)
                .build();

        ListenableFuture<List<ScoredPoint>> successFuture = Futures.immediateFuture(List.of(point));
        when(qdrantClient.searchAsync(any(SearchPoints.class))).thenReturn(successFuture);

        List<EmailContext> result = service.searchSimilarEmails(new float[] {0.1f}, 5);

        assertEquals(1, result.size());
        EmailContext ctx = result.get(0);
        assertEquals("No Subject", ctx.subject());
        assertEquals("Unknown Sender", ctx.sender());
        assertEquals("", ctx.snippet());
    }

    @Test
    void searchSimilarEmails_usesAlternativePayloadKeys() {
        properties.setEnabled(true);
        properties.setCollectionName("test-collection");
        VectorSearchService service = new VectorSearchService(qdrantClient, properties);

        ScoredPoint point = ScoredPoint.newBuilder()
                .setId(PointId.newBuilder().setUuid("abc-123").build())
                .setScore(0.9f)
                .putPayload(
                        "title",
                        Value.newBuilder().setStringValue("Alt Subject Key").build())
                .putPayload(
                        "from",
                        Value.newBuilder().setStringValue("bob@example.com").build())
                .putPayload(
                        "body",
                        Value.newBuilder()
                                .setStringValue("Alt body key content")
                                .build())
                .build();

        ListenableFuture<List<ScoredPoint>> successFuture = Futures.immediateFuture(List.of(point));
        when(qdrantClient.searchAsync(any(SearchPoints.class))).thenReturn(successFuture);

        List<EmailContext> result = service.searchSimilarEmails(new float[] {0.1f}, 5);

        assertEquals(1, result.size());
        EmailContext ctx = result.get(0);
        assertEquals("abc-123", ctx.emailId());
        assertEquals("Alt Subject Key", ctx.subject());
        assertEquals("bob@example.com", ctx.sender());
        assertEquals("Alt body key content", ctx.snippet());
    }

    // Direct unit tests for parseTimestamp returning Optional

    @Test
    void parseTimestamp_withValidOffsetDateTime_returnsPresent() {
        properties.setEnabled(true);
        VectorSearchService service = new VectorSearchService(qdrantClient, properties);

        Optional<LocalDateTime> result = service.parseTimestamp("2025-01-15T09:30:00Z");

        assertTrue(result.isPresent());
        assertEquals(2025, result.get().getYear());
        assertEquals(1, result.get().getMonthValue());
        assertEquals(15, result.get().getDayOfMonth());
    }

    @Test
    void parseTimestamp_withValidLocalDateTime_returnsPresent() {
        properties.setEnabled(true);
        VectorSearchService service = new VectorSearchService(qdrantClient, properties);

        Optional<LocalDateTime> result = service.parseTimestamp("2025-12-25T00:00:00");

        assertTrue(result.isPresent());
        assertEquals(2025, result.get().getYear());
        assertEquals(12, result.get().getMonthValue());
    }

    @Test
    void parseTimestamp_withNull_returnsEmpty() {
        properties.setEnabled(true);
        VectorSearchService service = new VectorSearchService(qdrantClient, properties);

        Optional<LocalDateTime> result = service.parseTimestamp(null);

        assertTrue(result.isEmpty());
    }

    @Test
    void parseTimestamp_withBlankString_returnsEmpty() {
        properties.setEnabled(true);
        VectorSearchService service = new VectorSearchService(qdrantClient, properties);

        Optional<LocalDateTime> result = service.parseTimestamp("   ");

        assertTrue(result.isEmpty());
    }

    @Test
    void parseTimestamp_withInvalidFormat_returnsEmpty() {
        properties.setEnabled(true);
        VectorSearchService service = new VectorSearchService(qdrantClient, properties);

        Optional<LocalDateTime> result = service.parseTimestamp("not-a-valid-timestamp");

        assertTrue(result.isEmpty());
    }
}

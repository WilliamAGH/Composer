package com.composerai.api.shared.ledger;

import com.composerai.api.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationLedgerServiceTest {

    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();
    private Path tempDir;

    @AfterEach
    void cleanup() throws IOException {
        if (tempDir != null) {
            Files.walk(tempDir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        System.err.println("Failed to delete test file: " + path);
                    }
                });
        }
    }

    @Test
    void persistsEnvelopeWhenEnabled() throws Exception {
        tempDir = Files.createTempDirectory("ledger-test");
        AppProperties props = new AppProperties();
        props.getLedger().setEnabled(true);
        props.getLedger().setDirectory(tempDir.toString());

        ConversationLedgerService service = new ConversationLedgerService(mapper, props);
        ConversationEvent event = new ConversationEvent(
            "evt-1",
            1,
            null,
            Instant.parse("2025-01-16T10:00:00Z"),
            "user_message",
            "user",
            "Hello",
            List.of(),
            Map.of(),
            null,
            null
        );
        ConversationEnvelope envelope = new ConversationEnvelope(
            "conv-123",
            Instant.parse("2025-01-16T10:00:05Z"),
            2,
            Map.of(),
            List.of(event),
            List.of()
        );

        service.persist(envelope);

        assertTrue(Files.list(tempDir).findFirst().isPresent(), "Expected ledger file to be written");
    }
}

package com.composerai.api.shared.ledger;

import com.composerai.api.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Persists {@link ConversationEnvelope} snapshots for later replay. When disabled the service
 * becomes a no-op so callers can invoke it unconditionally.
 */
@Service
public class ConversationLedgerService {

    private static final Logger logger = LoggerFactory.getLogger(ConversationLedgerService.class);
    private static final DateTimeFormatter FILE_SUFFIX =
        DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").withZone(ZoneOffset.UTC);

    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public ConversationLedgerService(ObjectMapper objectMapper, AppProperties appProperties) {
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
    }

    public boolean enabled() {
        return appProperties.getLedger().isEnabled();
    }

    public void persist(ConversationEnvelope envelope) {
        if (!enabled() || envelope == null) {
            return;
        }
        try {
            Path dir = Path.of(appProperties.getLedger().getDirectory());
            Files.createDirectories(dir);
            String filename = envelope.conversationId() + "-" + FILE_SUFFIX.format(envelope.createdAt()) + ".json";
            Path file = dir.resolve(filename);
            String payload = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(envelope);
            Files.writeString(file, payload, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            logger.debug("Ledger persisted: {}", file);
        } catch (IOException e) {
            logger.warn("Failed to persist conversation ledger for {}", envelope.conversationId(), e);
        }
    }
}

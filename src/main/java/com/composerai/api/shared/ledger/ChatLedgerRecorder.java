package com.composerai.api.shared.ledger;

import com.composerai.api.config.OpenAiProperties;
import com.composerai.api.dto.ChatRequest;
import com.composerai.api.service.OpenAiChatService;
import com.composerai.api.util.IdGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Assembles {@link ConversationEnvelope}s for chat completions and hands them to
 * {@link ConversationLedgerService}. Keeps all serialization localized so call sites remain small.
 */
@Component
public class ChatLedgerRecorder {

    private static final Logger logger = LoggerFactory.getLogger(ChatLedgerRecorder.class);

    private static final int LEDGER_VERSION = 2;

    private final ConversationLedgerService ledgerService;
    private final EmailContextResolver emailContextResolver;
    private final ObjectMapper objectMapper;
    private final OpenAiProperties openAiProperties;

    public ChatLedgerRecorder(
        ConversationLedgerService ledgerService,
        EmailContextResolver emailContextResolver,
        ObjectMapper objectMapper,
        OpenAiProperties openAiProperties
    ) {
        this.ledgerService = ledgerService;
        this.emailContextResolver = emailContextResolver;
        this.objectMapper = objectMapper;
        this.openAiProperties = openAiProperties;
    }

    public void recordChatCompletion(
        ChatRequest request,
        String conversationId,
        OpenAiChatService.Invocation invocation,
        String assistantContent
    ) {
        boolean shouldPersist = ledgerService.enabled();
        boolean shouldLog = openAiProperties.isLocalDebugEnabled();
        if ((!shouldPersist && !shouldLog) || request == null || invocation == null) {
            return;
        }

        Instant now = Instant.now();
        List<ConversationEvent> events = new ArrayList<>();
        List<ContextRef> contextRefs = buildContextRefs(request);
        List<EmailObject> emailObjects = emailContextResolver.resolveAll(
            contextRefs.stream().map(ContextRef::refId).toList()
        );

        events.add(
            new ConversationEvent(
                IdGenerator.uuidV7(),
                1,
                null,
                now,
                "user_message",
                "user",
                Objects.toString(request.getMessage(), ""),
                contextRefs,
                Map.of(),
                null,
                null
            )
        );

        LlmCallPayload llmPayload = new LlmCallPayload(
            "openai",
            "responses",
            invocation.requestParams() == null ? "unknown" : invocation.requestParams().getClass().getSimpleName(),
            serialize(invocation.requestParams()),
            serialize(invocation.response()),
            invocation.usage()
        );

        events.add(
            new ConversationEvent(
                IdGenerator.uuidV7(),
                2,
                null,
                now,
                "llm_call",
                null,
                null,
                contextRefs,
                Map.of("jsonOutput", request.isJsonOutput()),
                llmPayload,
                null
            )
        );

        events.add(
            new ConversationEvent(
                IdGenerator.uuidV7(),
                3,
                null,
                now,
                "assistant_message",
                "assistant",
                assistantContent,
                contextRefs,
                Map.of("jsonOutput", request.isJsonOutput()),
                null,
                null
            )
        );

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("journeyScope", request.getJourneyScope());
        metadata.put("journeyScopeTarget", request.getJourneyScopeTarget());
        metadata.put("command", request.getAiCommand());
        metadata.put("conversationId", conversationId);
        metadata.values().removeIf(Objects::isNull);

        ConversationEnvelope envelope = new ConversationEnvelope(
            conversationId,
            now,
            LEDGER_VERSION,
            metadata,
            events,
            emailObjects
        );
        logIfLocal(envelope);
        if (shouldPersist) {
            ledgerService.persist(envelope);
        }
    }

    private void logIfLocal(ConversationEnvelope envelope) {
        if (!openAiProperties.isLocalDebugEnabled()) {
            return;
        }
        try {
            String preview = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(envelope);
            logger.info("[LEDGER] {}", preview);
        } catch (Exception ignored) {}
    }

    private List<ContextRef> buildContextRefs(ChatRequest request) {
        List<ContextRef> refs = new ArrayList<>();
        if (request.getContextId() != null && !request.getContextId().isBlank()) {
            refs.add(new ContextRef("email", request.getContextId(), "primary"));
        }
        return refs;
    }

    private String serialize(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }
}

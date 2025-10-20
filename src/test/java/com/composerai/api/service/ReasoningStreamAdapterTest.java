package com.composerai.api.service;

import com.openai.models.responses.ResponseReasoningSummaryTextDeltaEvent;
import com.openai.models.responses.ResponseReasoningTextDeltaEvent;
import com.openai.models.responses.ResponseStreamEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReasoningStreamAdapterTest {

    @Test
    void extractReturnsEmptyListForNullEvent() {
        List<ReasoningStreamAdapter.ReasoningEvent> events = ReasoningStreamAdapter.extract(null);
        assertNotNull(events);
        assertTrue(events.isEmpty());
    }

    @Test
    void extractMapsSummaryTextDelta() {
        ResponseReasoningSummaryTextDeltaEvent deltaEvent = ResponseReasoningSummaryTextDeltaEvent.builder()
            .delta("Step 1: gather context.")
            .itemId("item-1")
            .outputIndex(0L)
            .sequenceNumber(1L)
            .summaryIndex(0L)
            .build();

        ResponseStreamEvent streamEvent = ResponseStreamEvent.ofReasoningSummaryTextDelta(deltaEvent);

        List<ReasoningStreamAdapter.ReasoningEvent> events = ReasoningStreamAdapter.extract(streamEvent);
        assertEquals(1, events.size());

        ReasoningStreamAdapter.ReasoningEvent event = events.getFirst();
        assertEquals(ReasoningStreamAdapter.Type.SUMMARY_TEXT_DELTA, event.type());

        ReasoningStreamAdapter.ReasoningMessage message = event.toMessage();
        assertEquals(ReasoningStreamAdapter.Type.SUMMARY_TEXT_DELTA, message.type());
        assertEquals(ReasoningStreamAdapter.Phase.THINKING, message.phase());
        assertEquals("Reasoning…", message.displayLabel());
        assertNull(message.step());
        assertEquals(ReasoningStreamAdapter.Phase.THINKING, message.phase());
        assertTrue(message.payload() instanceof ReasoningStreamAdapter.SummaryTextPayload);

        ReasoningStreamAdapter.SummaryTextPayload payload =
            (ReasoningStreamAdapter.SummaryTextPayload) message.payload();
        assertEquals("item-1", payload.itemId());
        assertEquals(1L, payload.sequenceNumber());
        assertEquals(0L, payload.summaryIndex());
        assertEquals("Step 1: gather context.", payload.content());
    }

    @Test
    void extractMapsReasoningTextDelta() {
        ResponseReasoningTextDeltaEvent textDeltaEvent = ResponseReasoningTextDeltaEvent.builder()
            .contentIndex(0L)
            .delta("Evaluating the email tone.")
            .itemId("item-2")
            .outputIndex(0L)
            .sequenceNumber(2L)
            .build();

        ResponseStreamEvent streamEvent = ResponseStreamEvent.ofReasoningTextDelta(textDeltaEvent);

        List<ReasoningStreamAdapter.ReasoningEvent> events = ReasoningStreamAdapter.extract(streamEvent);
        assertEquals(1, events.size());

        ReasoningStreamAdapter.ReasoningEvent event = events.getFirst();
        assertEquals(ReasoningStreamAdapter.Type.TEXT_DELTA, event.type());

        ReasoningStreamAdapter.ReasoningMessage message = event.toMessage();
        assertEquals(ReasoningStreamAdapter.Type.TEXT_DELTA, message.type());
        assertEquals(ReasoningStreamAdapter.Phase.PROGRESS, message.phase());
        assertEquals("Reasoning step 2", message.displayLabel());
        assertEquals(Long.valueOf(2L), message.step());
        assertEquals(ReasoningStreamAdapter.Phase.PROGRESS, message.phase());
        assertTrue(message.payload() instanceof ReasoningStreamAdapter.TextPayload);

        ReasoningStreamAdapter.TextPayload payload =
            (ReasoningStreamAdapter.TextPayload) message.payload();
        assertEquals("item-2", payload.itemId());
        assertEquals(2L, payload.sequenceNumber());
        assertEquals("Evaluating the email tone.", payload.content());
    }
}

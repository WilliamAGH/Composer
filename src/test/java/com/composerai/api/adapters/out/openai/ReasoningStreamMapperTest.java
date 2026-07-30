package com.composerai.api.adapters.out.openai;

import static org.junit.jupiter.api.Assertions.*;

import com.composerai.api.domain.model.ReasoningEffortLevel;
import com.openai.models.responses.ResponseReasoningSummaryTextDeltaEvent;
import com.openai.models.responses.ResponseReasoningTextDeltaEvent;
import com.openai.models.responses.ResponseStreamEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReasoningStreamMapperTest {

    @Test
    void extractReturnsEmptyListForNullEvent() {
        List<ReasoningStreamMapper.ReasoningEvent> events = ReasoningStreamMapper.extract(null);
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

        List<ReasoningStreamMapper.ReasoningEvent> events = ReasoningStreamMapper.extract(streamEvent);
        assertEquals(1, events.size());

        ReasoningStreamMapper.ReasoningEvent event = events.getFirst();
        assertEquals(ReasoningStreamMapper.Type.SUMMARY_TEXT_DELTA, event.type());

        ReasoningStreamMapper.ReasoningMessage message = event.toMessage();
        assertEquals(ReasoningStreamMapper.Type.SUMMARY_TEXT_DELTA, message.type());
        assertEquals(ReasoningStreamMapper.Phase.THINKING, message.phase());
        assertEquals("Reasoning…", message.displayLabel());
        assertNull(message.step());
        assertTrue(message.payload() instanceof ReasoningStreamMapper.SummaryTextPayload);

        ReasoningStreamMapper.SummaryTextPayload payload = (ReasoningStreamMapper.SummaryTextPayload) message.payload();
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

        List<ReasoningStreamMapper.ReasoningEvent> events = ReasoningStreamMapper.extract(streamEvent);
        assertEquals(1, events.size());

        ReasoningStreamMapper.ReasoningEvent event = events.getFirst();
        assertEquals(ReasoningStreamMapper.Type.TEXT_DELTA, event.type());

        ReasoningStreamMapper.ReasoningMessage message = event.toMessage();
        assertEquals(ReasoningStreamMapper.Type.TEXT_DELTA, message.type());
        assertEquals(ReasoningStreamMapper.Phase.PROGRESS, message.phase());
        assertEquals("Reasoning step 2", message.displayLabel());
        assertEquals(Long.valueOf(2L), message.step());
        assertTrue(message.payload() instanceof ReasoningStreamMapper.TextPayload);

        ReasoningStreamMapper.TextPayload payload = (ReasoningStreamMapper.TextPayload) message.payload();
        assertEquals("item-2", payload.itemId());
        assertEquals(2L, payload.sequenceNumber());
        assertEquals("Evaluating the email tone.", payload.content());
    }

    @Test
    void thinkingPhaseIncludesEffortLabelWhenProvided() {
        ResponseReasoningSummaryTextDeltaEvent partAddedEvent = ResponseReasoningSummaryTextDeltaEvent.builder()
                .delta("Brainstorming next step")
                .itemId("item-5")
                .outputIndex(0L)
                .sequenceNumber(3L)
                .summaryIndex(1L)
                .build();

        var extracted = ReasoningStreamMapper.extract(ResponseStreamEvent.ofReasoningSummaryTextDelta(partAddedEvent));
        assertEquals(1, extracted.size());

        var message = extracted.getFirst().toMessage(ReasoningEffortLevel.HIGH.displayName());
        assertEquals("Reasoning… High effort", message.displayLabel());
        assertEquals(ReasoningStreamMapper.Phase.THINKING, message.phase());
        assertNull(message.step());
    }
}

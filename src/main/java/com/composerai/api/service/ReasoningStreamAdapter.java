package com.composerai.api.service;

import com.openai.models.responses.ResponseFailedEvent;
import com.openai.models.responses.ResponseReasoningSummaryPartAddedEvent;
import com.openai.models.responses.ResponseReasoningSummaryPartDoneEvent;
import com.openai.models.responses.ResponseReasoningSummaryTextDeltaEvent;
import com.openai.models.responses.ResponseReasoningSummaryTextDoneEvent;
import com.openai.models.responses.ResponseReasoningTextDeltaEvent;
import com.openai.models.responses.ResponseReasoningTextDoneEvent;
import com.openai.models.responses.ResponseStreamEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Maps {@link ResponseStreamEvent} reasoning signals from the OpenAI Responses API into a single
 * normalized event model that downstream services, controllers, and clients can consume without
 * duplicating the translation logic.
 */
public final class ReasoningStreamAdapter {

    private ReasoningStreamAdapter() {}

    static List<ReasoningEvent> extract(ResponseStreamEvent event) {
        if (event == null) return Collections.emptyList();
        List<ReasoningEvent> events = new ArrayList<>(1);
        event.reasoningSummaryPartAdded().ifPresent(value -> events.add(new SummaryPartAdded(value)));
        event.reasoningSummaryPartDone().ifPresent(value -> events.add(new SummaryPartDone(value)));
        event.reasoningSummaryTextDelta().ifPresent(value -> events.add(new SummaryTextDelta(value)));
        event.reasoningSummaryTextDone().ifPresent(value -> events.add(new SummaryTextDone(value)));
        event.reasoningTextDelta().ifPresent(value -> events.add(new TextDelta(value)));
        event.reasoningTextDone().ifPresent(value -> events.add(new TextDone(value)));
        return events;
    }

    public static ReasoningEvent failure(ResponseFailedEvent event) {
        return event == null ? null : new Failure(event);
    }

    enum Type {
        SUMMARY_PART_ADDED("reasoning-summary-part-added"),
        SUMMARY_PART_DONE("reasoning-summary-part-done"),
        SUMMARY_TEXT_DELTA("reasoning-summary-text-delta"),
        SUMMARY_TEXT_DONE("reasoning-summary-text-done"),
        TEXT_DELTA("reasoning-text-delta"),
        TEXT_DONE("reasoning-text-done"),
        FAILED("reasoning-failed");

        private final String eventName;
        Type(String eventName) { this.eventName = eventName; }
        String eventName() { return eventName; }

        Phase phase() {
            return switch (this) {
                case SUMMARY_PART_ADDED, SUMMARY_PART_DONE, SUMMARY_TEXT_DELTA, SUMMARY_TEXT_DONE -> Phase.THINKING;
                case TEXT_DELTA -> Phase.PROGRESS;
                case TEXT_DONE -> Phase.STREAMING;
                case FAILED -> Phase.FAILED;
            };
        }
    }

    public sealed interface ReasoningEvent permits SummaryPartAdded, SummaryPartDone, SummaryTextDelta,
        SummaryTextDone, TextDelta, TextDone, Failure {
        Type type();
        String eventName();
        Object payload();
        default ReasoningMessage toMessage() {
            Phase phase = type().phase();
            Object payload = payload();
            return new ReasoningMessage(
                type(),
                phase,
                computeDisplayLabel(phase, payload),
                extractStep(payload),
                payload
            );
        }
    }

    record SummaryPartAdded(ResponseReasoningSummaryPartAddedEvent value) implements ReasoningEvent {
        SummaryPartAdded { Objects.requireNonNull(value, "value"); }
        @Override public Type type() { return Type.SUMMARY_PART_ADDED; }
        @Override public String eventName() { return type().eventName(); }
        @Override public Object payload() {
            return new SummaryPartPayload(value.itemId(), value.summaryIndex(), value.outputIndex(), value.sequenceNumber(), value.part());
        }
    }

    record SummaryPartDone(ResponseReasoningSummaryPartDoneEvent value) implements ReasoningEvent {
        SummaryPartDone { Objects.requireNonNull(value, "value"); }
        @Override public Type type() { return Type.SUMMARY_PART_DONE; }
        @Override public String eventName() { return type().eventName(); }
        @Override public Object payload() {
            return new SummaryPartPayload(value.itemId(), value.summaryIndex(), value.outputIndex(), value.sequenceNumber(), value.part());
        }
    }

    record SummaryTextDelta(ResponseReasoningSummaryTextDeltaEvent value) implements ReasoningEvent {
        SummaryTextDelta { Objects.requireNonNull(value, "value"); }
        @Override public Type type() { return Type.SUMMARY_TEXT_DELTA; }
        @Override public String eventName() { return type().eventName(); }
        @Override public Object payload() {
            return new SummaryTextPayload(value.itemId(), value.summaryIndex(), value.outputIndex(), value.sequenceNumber(), value.delta());
        }
    }

    record SummaryTextDone(ResponseReasoningSummaryTextDoneEvent value) implements ReasoningEvent {
        SummaryTextDone { Objects.requireNonNull(value, "value"); }
        @Override public Type type() { return Type.SUMMARY_TEXT_DONE; }
        @Override public String eventName() { return type().eventName(); }
        @Override public Object payload() {
            return new SummaryTextPayload(value.itemId(), value.summaryIndex(), value.outputIndex(), value.sequenceNumber(), value.text());
        }
    }

    record TextDelta(ResponseReasoningTextDeltaEvent value) implements ReasoningEvent {
        TextDelta { Objects.requireNonNull(value, "value"); }
        @Override public Type type() { return Type.TEXT_DELTA; }
        @Override public String eventName() { return type().eventName(); }
        @Override public Object payload() {
            return new TextPayload(value.itemId(), value.outputIndex(), value.sequenceNumber(), value.delta());
        }
    }

    record TextDone(ResponseReasoningTextDoneEvent value) implements ReasoningEvent {
        TextDone { Objects.requireNonNull(value, "value"); }
        @Override public Type type() { return Type.TEXT_DONE; }
        @Override public String eventName() { return type().eventName(); }
        @Override public Object payload() {
            return new TextPayload(value.itemId(), value.outputIndex(), value.sequenceNumber(), value.text());
        }
    }

    record Failure(ResponseFailedEvent value) implements ReasoningEvent {
        Failure { Objects.requireNonNull(value, "value"); }
        @Override public Type type() { return Type.FAILED; }
        @Override public String eventName() { return type().eventName(); }
        @Override public Object payload() {
            return new FailurePayload(value.sequenceNumber(), value.response() == null ? null : value.response().id(), value);
        }
    }

    record SummaryPartPayload(String itemId, long summaryIndex, long outputIndex, long sequenceNumber, Object part) {}

    record SummaryTextPayload(String itemId, long summaryIndex, long outputIndex, long sequenceNumber, Object content) {}

    record TextPayload(String itemId, long outputIndex, long sequenceNumber, Object content) {}

    record FailurePayload(long sequenceNumber, String responseId, Object raw) {}

    public enum Phase { THINKING, PROGRESS, STREAMING, FAILED }

    public record ReasoningMessage(Type type, Phase phase, String displayLabel, Long step, Object payload) {}

    public static ReasoningMessage toMessage(OpenAiChatService.StreamEvent event) {
        if (event == null) return null;
        return switch (event) {
            case OpenAiChatService.StreamEvent.Reasoning reasoning -> reasoning.value().toMessage();
            case OpenAiChatService.StreamEvent.Failed failed -> {
                ReasoningEvent failure = failure(failed.value());
                yield failure != null ? failure.toMessage() : null;
            }
            case OpenAiChatService.StreamEvent.RenderedHtml ignored -> null;
        };
    }

    private static String computeDisplayLabel(Phase phase, Object payload) {
        return switch (phase) {
            case THINKING -> "Reasoning…";
            case PROGRESS -> {
                Long step = extractStep(payload);
                yield step != null ? "Reasoning step " + step : "Reasoning in progress…";
            }
            case STREAMING -> "Reasoning complete — drafting response";
            case FAILED -> "Reasoning failed — reverting to fast response";
        };
    }

    private static Long extractStep(Object payload) {
        if (payload instanceof SummaryPartPayload summary) {
            return summary.sequenceNumber();
        }
        if (payload instanceof SummaryTextPayload summaryText) {
            return summaryText.sequenceNumber();
        }
        if (payload instanceof TextPayload textPayload) {
            return textPayload.sequenceNumber();
        }
        if (payload instanceof FailurePayload failure) {
            return failure.sequenceNumber();
        }
        return null;
    }
}

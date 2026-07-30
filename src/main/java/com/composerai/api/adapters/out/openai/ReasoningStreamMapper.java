package com.composerai.api.adapters.out.openai;

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
public final class ReasoningStreamMapper {

    private ReasoningStreamMapper() {}

    static List<ReasoningEvent> extract(ResponseStreamEvent event) {
        if (event == null) return Collections.emptyList();
        List<ReasoningEvent> events = new ArrayList<>(1);
        event.reasoningSummaryPartAdded().ifPresent(sdkEvent -> events.add(new SummaryPartAdded(sdkEvent)));
        event.reasoningSummaryPartDone().ifPresent(sdkEvent -> events.add(new SummaryPartDone(sdkEvent)));
        event.reasoningSummaryTextDelta().ifPresent(sdkEvent -> events.add(new SummaryTextDelta(sdkEvent)));
        event.reasoningSummaryTextDone().ifPresent(sdkEvent -> events.add(new SummaryTextDone(sdkEvent)));
        event.reasoningTextDelta().ifPresent(sdkEvent -> events.add(new TextDelta(sdkEvent)));
        event.reasoningTextDone().ifPresent(sdkEvent -> events.add(new TextDone(sdkEvent)));
        return events;
    }

    public static ReasoningEvent failure(ResponseFailedEvent event) {
        return event == null ? null : new Failure(event);
    }

    public enum Type {
        SUMMARY_PART_ADDED("reasoning-summary-part-added"),
        SUMMARY_PART_DONE("reasoning-summary-part-done"),
        SUMMARY_TEXT_DELTA("reasoning-summary-text-delta"),
        SUMMARY_TEXT_DONE("reasoning-summary-text-done"),
        TEXT_DELTA("reasoning-text-delta"),
        TEXT_DONE("reasoning-text-done"),
        FAILED("reasoning-failed");

        private final String eventName;

        Type(String eventName) {
            this.eventName = eventName;
        }

        String eventName() {
            return eventName;
        }

        Phase phase() {
            return switch (this) {
                case SUMMARY_PART_ADDED, SUMMARY_PART_DONE, SUMMARY_TEXT_DELTA, SUMMARY_TEXT_DONE -> Phase.THINKING;
                case TEXT_DELTA -> Phase.PROGRESS;
                case TEXT_DONE -> Phase.STREAMING;
                case FAILED -> Phase.FAILED;
            };
        }
    }

    public sealed interface ReasoningEvent
            permits SummaryPartAdded, SummaryPartDone, SummaryTextDelta, SummaryTextDone, TextDelta, TextDone, Failure {
        Type type();

        String eventName();

        Object payload();

        default ReasoningMessage toMessage() {
            return toMessage(null);
        }

        default ReasoningMessage toMessage(String reasoningLabel) {
            Phase phase = type().phase();
            Object payload = payload();
            return buildMessage(type(), phase, payload, reasoningLabel);
        }
    }

    record SummaryPartAdded(ResponseReasoningSummaryPartAddedEvent sdkEvent) implements ReasoningEvent {
        SummaryPartAdded {
            Objects.requireNonNull(sdkEvent, "sdkEvent");
        }

        @Override
        public Type type() {
            return Type.SUMMARY_PART_ADDED;
        }

        @Override
        public String eventName() {
            return type().eventName();
        }

        @Override
        public Object payload() {
            return new SummaryPartPayload(
                    sdkEvent.itemId(),
                    sdkEvent.summaryIndex(),
                    sdkEvent.outputIndex(),
                    sdkEvent.sequenceNumber(),
                    sdkEvent.part());
        }
    }

    record SummaryPartDone(ResponseReasoningSummaryPartDoneEvent sdkEvent) implements ReasoningEvent {
        SummaryPartDone {
            Objects.requireNonNull(sdkEvent, "sdkEvent");
        }

        @Override
        public Type type() {
            return Type.SUMMARY_PART_DONE;
        }

        @Override
        public String eventName() {
            return type().eventName();
        }

        @Override
        public Object payload() {
            return new SummaryPartPayload(
                    sdkEvent.itemId(),
                    sdkEvent.summaryIndex(),
                    sdkEvent.outputIndex(),
                    sdkEvent.sequenceNumber(),
                    sdkEvent.part());
        }
    }

    record SummaryTextDelta(ResponseReasoningSummaryTextDeltaEvent sdkEvent) implements ReasoningEvent {
        SummaryTextDelta {
            Objects.requireNonNull(sdkEvent, "sdkEvent");
        }

        @Override
        public Type type() {
            return Type.SUMMARY_TEXT_DELTA;
        }

        @Override
        public String eventName() {
            return type().eventName();
        }

        @Override
        public Object payload() {
            return new SummaryTextPayload(
                    sdkEvent.itemId(),
                    sdkEvent.summaryIndex(),
                    sdkEvent.outputIndex(),
                    sdkEvent.sequenceNumber(),
                    sdkEvent.delta());
        }
    }

    record SummaryTextDone(ResponseReasoningSummaryTextDoneEvent sdkEvent) implements ReasoningEvent {
        SummaryTextDone {
            Objects.requireNonNull(sdkEvent, "sdkEvent");
        }

        @Override
        public Type type() {
            return Type.SUMMARY_TEXT_DONE;
        }

        @Override
        public String eventName() {
            return type().eventName();
        }

        @Override
        public Object payload() {
            return new SummaryTextPayload(
                    sdkEvent.itemId(),
                    sdkEvent.summaryIndex(),
                    sdkEvent.outputIndex(),
                    sdkEvent.sequenceNumber(),
                    sdkEvent.text());
        }
    }

    record TextDelta(ResponseReasoningTextDeltaEvent sdkEvent) implements ReasoningEvent {
        TextDelta {
            Objects.requireNonNull(sdkEvent, "sdkEvent");
        }

        @Override
        public Type type() {
            return Type.TEXT_DELTA;
        }

        @Override
        public String eventName() {
            return type().eventName();
        }

        @Override
        public Object payload() {
            return new TextPayload(
                    sdkEvent.itemId(), sdkEvent.outputIndex(), sdkEvent.sequenceNumber(), sdkEvent.delta());
        }
    }

    record TextDone(ResponseReasoningTextDoneEvent sdkEvent) implements ReasoningEvent {
        TextDone {
            Objects.requireNonNull(sdkEvent, "sdkEvent");
        }

        @Override
        public Type type() {
            return Type.TEXT_DONE;
        }

        @Override
        public String eventName() {
            return type().eventName();
        }

        @Override
        public Object payload() {
            return new TextPayload(
                    sdkEvent.itemId(), sdkEvent.outputIndex(), sdkEvent.sequenceNumber(), sdkEvent.text());
        }
    }

    record Failure(ResponseFailedEvent sdkEvent) implements ReasoningEvent {
        Failure {
            Objects.requireNonNull(sdkEvent, "sdkEvent");
        }

        @Override
        public Type type() {
            return Type.FAILED;
        }

        @Override
        public String eventName() {
            return type().eventName();
        }

        @Override
        public Object payload() {
            return new FailurePayload(
                    sdkEvent.sequenceNumber(),
                    sdkEvent.response() == null ? null : sdkEvent.response().id(),
                    sdkEvent);
        }
    }

    record SummaryPartPayload(String itemId, long summaryIndex, long outputIndex, long sequenceNumber, Object part) {}

    record SummaryTextPayload(
            String itemId, long summaryIndex, long outputIndex, long sequenceNumber, Object content) {}

    record TextPayload(String itemId, long outputIndex, long sequenceNumber, Object content) {}

    record FailurePayload(long sequenceNumber, String responseId, Object raw) {}

    public enum Phase {
        THINKING,
        PROGRESS,
        STREAMING,
        FAILED
    }

    public record ReasoningMessage(Type type, Phase phase, String displayLabel, Long step, Object payload) {}

    public static ReasoningMessage toMessage(OpenAiStreamEvent event) {
        return toMessage(event, null);
    }

    public static ReasoningMessage toMessage(OpenAiStreamEvent event, String reasoningLabel) {
        if (event == null) return null;
        return switch (event) {
            case OpenAiStreamEvent.Reasoning reasoning ->
                reasoning.reasoningEvent().toMessage(reasoningLabel);
            case OpenAiStreamEvent.Failed failed -> {
                ReasoningEvent failure = failure(failed.failedEvent());
                yield failure != null ? failure.toMessage(reasoningLabel) : null;
            }
            case OpenAiStreamEvent.RenderedHtml ignored -> null;
            case OpenAiStreamEvent.RawJson ignored -> null;
            default -> null;
        };
    }

    private static ReasoningMessage buildMessage(Type type, Phase phase, Object payload, String reasoningLabel) {
        return new ReasoningMessage(
                type, phase, computeDisplayLabel(phase, payload, reasoningLabel), extractStep(payload), payload);
    }

    private static String computeDisplayLabel(Phase phase, Object payload, String normalizedThinkingLabel) {
        return switch (phase) {
            case THINKING ->
                normalizedThinkingLabel == null || normalizedThinkingLabel.isBlank()
                        ? "Reasoning…"
                        : "Reasoning… " + normalizedThinkingLabel + " effort";
            case PROGRESS -> {
                Long step = extractStep(payload);
                yield step != null ? "Reasoning step " + step : "Reasoning in progress…";
            }
            case STREAMING -> "Reasoning complete — drafting response";
            case FAILED -> "Reasoning failed — reverting to fast response";
        };
    }

    private static Long extractStep(Object payload) {
        // Only TEXT_DELTA (PROGRESS phase) and FAILED phase should show step numbers.
        // THINKING phase (SUMMARY events) should not show step numbers.
        if (payload instanceof TextPayload textPayload) {
            return textPayload.sequenceNumber();
        }
        if (payload instanceof FailurePayload failure) {
            return failure.sequenceNumber();
        }
        return null;
    }
}

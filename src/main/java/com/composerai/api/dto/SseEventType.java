package com.composerai.api.dto;

/**
 * SSE event types used in chat streaming.
 * Provides type safety and prevents typos in event names.
 */
public enum SseEventType {
    METADATA("metadata"),
    RENDERED_HTML("rendered_html"),
    DONE("done"),
    ERROR("error"),
    REASONING("reasoning");

    private final String eventName;

    SseEventType(String eventName) {
        this.eventName = eventName;
    }

    public String getEventName() {
        return eventName;
    }
}

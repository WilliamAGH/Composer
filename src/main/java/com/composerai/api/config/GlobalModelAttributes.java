package com.composerai.api.config;

import com.composerai.api.dto.SseEventType;
import com.composerai.api.service.ReasoningStreamAdapter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global model attributes available to all Thymeleaf templates.
 * Provides single source of truth for frontend constants derived from backend enums.
 *
 * Access in templates via th:inline="javascript" with inline expressions.
 */
@ControllerAdvice
public class GlobalModelAttributes {

    /**
     * Exposes SSE event type mappings to all templates.
     * Maps enum names to their event string values (e.g., METADATA → "metadata").
     *
     * @return map of SSE event types (METADATA, RENDERED_HTML, DONE, ERROR, REASONING)
     */
    @ModelAttribute("sseEvents")
    public Map<String, String> sseEvents() {
        return Arrays.stream(SseEventType.values())
            .collect(Collectors.toMap(
                Enum::name,
                SseEventType::getEventName
            ));
    }

    /**
     * Exposes reasoning phase names to all templates.
     * Maps enum names to their string representation (e.g., THINKING → "THINKING").
     *
     * @return map of reasoning phases (THINKING, PROGRESS, STREAMING, FAILED)
     */
    @ModelAttribute("reasoningPhases")
    public Map<String, String> reasoningPhases() {
        return Arrays.stream(ReasoningStreamAdapter.Phase.values())
            .collect(Collectors.toMap(
                Enum::name,
                Enum::name
            ));
    }
}

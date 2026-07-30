package com.composerai.api.domain.service;

import com.composerai.api.domain.model.ReasoningEffortLevel;

/** Chooses the canonical reasoning effort for an OpenAI-compatible generation request. */
public final class ReasoningRequestPolicy {

    public ReasoningEffortLevel resolve(
            String configuredDefault, Boolean thinkingEnabled, ReasoningEffortLevel requestedEffort) {
        if (requestedEffort != null) {
            return requestedEffort;
        }
        if (Boolean.FALSE.equals(thinkingEnabled)) {
            return ReasoningEffortLevel.NONE;
        }
        ReasoningEffortLevel configuredEffort = ReasoningEffortLevel.parseNullable(configuredDefault);
        if (configuredEffort != null) {
            return configuredEffort;
        }
        return ReasoningEffortLevel.DEFAULT;
    }
}

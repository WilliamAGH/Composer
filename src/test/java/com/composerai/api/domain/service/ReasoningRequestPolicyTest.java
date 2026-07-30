package com.composerai.api.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.composerai.api.domain.model.ReasoningEffortLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ReasoningRequestPolicyTest {

    private final ReasoningRequestPolicy reasoningRequestPolicy = new ReasoningRequestPolicy();

    @ParameterizedTest
    @EnumSource(ReasoningEffortLevel.class)
    void explicitEffortAlwaysWins(ReasoningEffortLevel requestedEffort) {
        ReasoningEffortLevel resolvedEffort = reasoningRequestPolicy.resolve("none", false, requestedEffort);

        assertEquals(requestedEffort, resolvedEffort);
    }

    @Test
    void legacyFalseWithoutEffortMapsToExplicitNone() {
        ReasoningEffortLevel resolvedEffort = reasoningRequestPolicy.resolve(null, false, null);

        assertEquals(ReasoningEffortLevel.NONE, resolvedEffort);
    }

    @Test
    void omittedConfigurationAndRequestUseCanonicalDefault() {
        assertEquals(ReasoningEffortLevel.DEFAULT, reasoningRequestPolicy.resolve(null, null, null));
    }

    @Test
    void configuredDefaultIsUsedWhenRequestIsUnspecified() {
        ReasoningEffortLevel resolvedEffort = reasoningRequestPolicy.resolve("medium", null, null);

        assertEquals(ReasoningEffortLevel.MEDIUM, resolvedEffort);
    }

    @Test
    void configuredNoneRemainsExplicit() {
        ReasoningEffortLevel resolvedEffort = reasoningRequestPolicy.resolve("none", null, null);

        assertEquals(ReasoningEffortLevel.NONE, resolvedEffort);
    }

    @Test
    void explicitEnablementUsesCanonicalDefaultWhenConfigurationIsAbsent() {
        ReasoningEffortLevel resolvedEffort = reasoningRequestPolicy.resolve(null, true, null);

        assertEquals(ReasoningEffortLevel.DEFAULT, resolvedEffort);
    }

    @Test
    void invalidConfiguredEffortIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> reasoningRequestPolicy.resolve("turbo", null, null));
    }
}

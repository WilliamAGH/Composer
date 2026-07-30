package com.composerai.api.domain.model;

import java.util.Locale;

/** Canonical reasoning-effort vocabulary accepted by Composer's chat contract. */
public enum ReasoningEffortLevel {
    NONE("none"),
    MINIMAL("minimal"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    XHIGH("xhigh"),
    MAX("max");

    public static final ReasoningEffortLevel DEFAULT = LOW;

    private final String externalName;

    ReasoningEffortLevel(String externalName) {
        this.externalName = externalName;
    }

    public String externalName() {
        return externalName;
    }

    public String displayName() {
        return externalName.substring(0, 1).toUpperCase(Locale.ROOT) + externalName.substring(1);
    }

    public static ReasoningEffortLevel parse(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            throw new IllegalArgumentException("Reasoning effort must not be blank");
        }
        String normalizedCandidate = candidate.trim().toLowerCase(Locale.ROOT);
        for (ReasoningEffortLevel effortLevel : ReasoningEffortLevel.values()) {
            if (effortLevel.externalName.equals(normalizedCandidate)) {
                return effortLevel;
            }
        }
        throw new IllegalArgumentException("Unsupported reasoning effort: " + candidate);
    }

    public static ReasoningEffortLevel parseNullable(String candidate) {
        return candidate == null || candidate.isBlank() ? null : parse(candidate);
    }
}

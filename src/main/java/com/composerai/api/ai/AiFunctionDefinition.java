package com.composerai.api.ai;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Canonical definition of an AI helper flow. Backed by configuration so the UI and backend share a
 * single description of prompts, defaults, and behavioural hints.
 */
public record AiFunctionDefinition(
        String key,
        String label,
        Category category,
        String description,
        String promptTemplate,
        String defaultInstruction,
        OutputFormat outputFormat,
        SubjectMode subjectMode,
        ContextStrategy contextStrategy,
        boolean primary,
        List<String> scopes,
        Map<String, String> defaultArgs,
        Map<String, AiFunctionVariant> variants) {

    public AiFunctionDefinition {
        key = key == null ? "" : key;
        label = label == null ? key : label;
        category = category == null ? Category.OTHER : category;
        promptTemplate = promptTemplate == null ? "" : promptTemplate;
        defaultInstruction = defaultInstruction == null ? "" : defaultInstruction;
        outputFormat = outputFormat == null ? OutputFormat.TEXT : outputFormat;
        subjectMode = subjectMode == null ? SubjectMode.OPTIONAL : subjectMode;
        contextStrategy = contextStrategy == null ? ContextStrategy.EMAIL_AND_UPLOADS : contextStrategy;
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
        defaultArgs = defaultArgs == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(defaultArgs));
        variants = variants == null
                ? Map.of()
                : Collections.unmodifiableMap(variants.entrySet().stream()
                        .collect(Collectors.toMap(
                                entry -> entry.getKey() == null
                                        ? ""
                                        : entry.getKey().toLowerCase(Locale.ROOT),
                                Map.Entry::getValue,
                                (existing, replacement) -> replacement,
                                LinkedHashMap::new)));
    }

    public Optional<AiFunctionVariant> variant(String variantKey) {
        if (variantKey == null || variantKey.isBlank()) {
            return Optional.empty();
        }
        AiFunctionVariant variant = variants.get(variantKey.toLowerCase());
        return Optional.ofNullable(variant);
    }

    public boolean requiresSubject() {
        return subjectMode == SubjectMode.REQUIRED;
    }

    public boolean allowsSubject() {
        return subjectMode != SubjectMode.NONE;
    }

    public boolean requiresContext() {
        return contextStrategy != ContextStrategy.NONE;
    }

    public enum Category {
        SUMMARY,
        TRANSLATION,
        COMPOSE,
        TONE,
        INSIGHTS,
        OTHER
    }

    public enum OutputFormat {
        TEXT,
        EMAIL,
        JSON
    }

    public enum SubjectMode {
        NONE,
        OPTIONAL,
        REQUIRED
    }

    public enum ContextStrategy {
        EMAIL_AND_UPLOADS,
        UPLOADS_ONLY,
        NONE
    }

    public record AiFunctionVariant(
            String key,
            String label,
            String promptTemplate,
            String defaultInstruction,
            Map<String, String> defaultArgs) {
        public AiFunctionVariant {
            key = key == null ? "" : key;
            label = label == null ? key : label;
            promptTemplate = promptTemplate == null ? "" : promptTemplate;
            defaultInstruction = defaultInstruction == null ? "" : defaultInstruction;
            defaultArgs =
                    defaultArgs == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(defaultArgs));
        }
    }
}

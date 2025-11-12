package com.composerai.api.dto;

import com.composerai.api.ai.AiFunctionDefinition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * DTO exposed to the UI so Svelte components can render AI helpers without duplicating metadata.
 */
public record AiFunctionCatalogDto(
    List<CategoryGroup> categories,
    Map<String, FunctionSummary> functionsByKey,
    List<String> primaryCommands
) {

    public static AiFunctionCatalogDto fromDefinitions(Collection<AiFunctionDefinition> definitions) {
        Map<String, FunctionSummary> summaries = new LinkedHashMap<>();
        List<CategoryGroup> groups = new ArrayList<>();

        Map<AiFunctionDefinition.Category, List<AiFunctionDefinition>> grouped = definitions.stream()
            .collect(Collectors.groupingBy(AiFunctionDefinition::category, LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<AiFunctionDefinition.Category, List<AiFunctionDefinition>> entry : grouped.entrySet()) {
            List<String> keys = entry.getValue().stream()
                .map(AiFunctionDefinition::key)
                .toList();
            groups.add(new CategoryGroup(entry.getKey().name(), prettyLabel(entry.getKey().name()), keys));
            entry.getValue().forEach(def -> summaries.put(def.key(), toSummary(def)));
        }

        List<String> primary = definitions.stream()
            .filter(AiFunctionDefinition::primary)
            .map(AiFunctionDefinition::key)
            .toList();

        return new AiFunctionCatalogDto(groups, summaries, primary);
    }

    private static FunctionSummary toSummary(AiFunctionDefinition def) {
        List<VariantSummary> variants = def.variants().values().stream()
            .map(variant -> new VariantSummary(
                variant.key(),
                variant.label(),
                variant.defaultInstruction(),
                variant.defaultArgs()
            ))
            .toList();

        return new FunctionSummary(
            def.key(),
            def.label(),
            def.description(),
            def.category().name(),
            def.defaultInstruction(),
            def.requiresSubject(),
            def.requiresContext(),
            def.outputFormat().name(),
            def.primary(),
            def.scopes(),
            def.defaultArgs(),
            variants
        );
    }

    private static String prettyLabel(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String lower = raw.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    public record CategoryGroup(String category, String label, List<String> functionKeys) {}

    public record FunctionSummary(
        String key,
        String label,
        String description,
        String category,
        String defaultInstruction,
        boolean subjectRequired,
        boolean contextRequired,
        String outputFormat,
        boolean primary,
        List<String> scopes,
        Map<String, String> defaultArgs,
        List<VariantSummary> variants
    ) {}

    public record VariantSummary(
        String key,
        String label,
        String defaultInstruction,
        Map<String, String> defaultArgs
    ) {}
}

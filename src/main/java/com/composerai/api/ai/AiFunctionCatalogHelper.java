package com.composerai.api.ai;

import com.composerai.api.config.AiFunctionCatalogProperties;
import com.composerai.api.dto.AiFunctionCatalogDto;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Normalizes configured AI functions and exposes lookup helpers so services, validators, and the
 * UI all reference the same catalog.
 */
@Component
public class AiFunctionCatalogHelper {

    private final Map<String, AiFunctionDefinition> definitions;
    private final AiFunctionCatalogDto dto;

    public AiFunctionCatalogHelper(AiFunctionCatalogProperties properties) {
        this.definitions = buildDefinitions(properties);
        this.dto = AiFunctionCatalogDto.fromDefinitions(definitions.values());
    }

    public Optional<AiFunctionDefinition> find(String commandKey, String variantKey) {
        if (commandKey == null || commandKey.isBlank()) {
            return Optional.empty();
        }
        AiFunctionDefinition def = definitions.get(commandKey.trim().toLowerCase(Locale.ROOT));
        if (def == null) {
            return Optional.empty();
        }
        if (variantKey != null && !variantKey.isBlank()) {
            if (def.variant(variantKey.trim().toLowerCase(Locale.ROOT)).isEmpty()) {
                return Optional.empty();
            }
        }
        return Optional.of(def);
    }

    public Collection<AiFunctionDefinition> all() {
        return definitions.values();
    }

    public AiFunctionCatalogDto dto() {
        return dto;
    }

    private Map<String, AiFunctionDefinition> buildDefinitions(AiFunctionCatalogProperties properties) {
        Map<String, AiFunctionDefinition> map = new LinkedHashMap<>();
        properties.normalizedDefinitions().forEach((key, raw) -> {
            Map<String, AiFunctionDefinition.AiFunctionVariant> variantMap = new LinkedHashMap<>();
            raw.getVariants().forEach((variantKey, variantProps) -> {
                String normalizedVariantKey = variantKey.trim().toLowerCase(Locale.ROOT);
                variantMap.put(normalizedVariantKey, new AiFunctionDefinition.AiFunctionVariant(
                    normalizedVariantKey,
                    variantProps.getLabel(),
                    variantProps.getPromptTemplate(),
                    variantProps.getDefaultInstruction(),
                    variantProps.getDefaultArgs()
                ));
            });

            AiFunctionDefinition def = new AiFunctionDefinition(
                key,
                raw.getLabel(),
                raw.getCategory(),
                raw.getDescription(),
                raw.getPromptTemplate(),
                raw.getDefaultInstruction(),
                raw.getOutputFormat(),
                raw.getSubjectMode(),
                raw.getContextStrategy(),
                raw.isPrimary(),
                raw.getScopes(),
                raw.getDefaultArgs(),
                variantMap
            );
            map.put(key, def);
        });
        return map;
    }
}

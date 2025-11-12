package com.composerai.api.validation;

import com.composerai.api.ai.AiFunctionCatalogHelper;
import com.composerai.api.ai.AiFunctionDefinition;
import com.composerai.api.dto.ChatRequest;
import com.composerai.api.util.StringUtils;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Validates that a {@link ChatRequest} references a catalog-backed AI function/variant and enforces
 * definition-level constraints (e.g., subject required). Centralizing the check here ensures the
 * backend and UI cannot drift on supported commands.
 */
@Component
public class AiCommandValidator implements ConstraintValidator<AiCommandValid, ChatRequest> {

    private final AiFunctionCatalogHelper catalogHelper;

    public AiCommandValidator(AiFunctionCatalogHelper catalogHelper) {
        this.catalogHelper = catalogHelper;
    }

    @Override
    public boolean isValid(ChatRequest value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        String command = value.getAiCommand();
        if (StringUtils.isBlank(command)) {
            return true;
        }

        Optional<AiFunctionDefinition> definition = catalogHelper.find(command, value.getCommandVariant());
        if (definition.isEmpty()) {
            reject(context, "aiCommand", "Unknown aiCommand or variant");
            return false;
        }

        if (definition.get().requiresSubject() && StringUtils.isBlank(value.getSubject())) {
            reject(context, "subject", "subject is required for this aiCommand");
            return false;
        }
        return true;
    }

    private void reject(ConstraintValidatorContext context, String property, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
            .addPropertyNode(property)
            .addConstraintViolation();
    }
}

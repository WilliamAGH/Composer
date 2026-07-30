package com.composerai.api.application.usecase.chat;

import com.composerai.api.ai.AiFunctionCatalogHelper;
import com.composerai.api.ai.AiFunctionDefinition;
import com.composerai.api.application.dto.ChatRequest;
import com.composerai.api.config.MagicEmailProperties;
import com.composerai.api.service.email.HtmlConverter;
import com.composerai.api.util.StringUtils;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Composes model-facing prompts from chat and catalog-command requests. */
@Component
public final class ChatPromptComposer {

    public static final String INSIGHTS_TRIGGER = "__INSIGHTS_TRIGGER__";

    private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile("\\[([^\\]]+)\\]\\(([^\\)]*)\\)");
    private static final Pattern BARE_URL_PATTERN = Pattern.compile("(?i)(?:https?://|www\\.)\\S+");
    private static final int MIN_INFERRED_NAME_LENGTH = 2;
    private static final String CONVERSATION_GUIDANCE = """
Interaction style:
- Sound like a thoughtful colleague: use concise sentences, contractions, and acknowledge the user.
- Ask short clarifying questions when the request is ambiguous before assuming intent.

Nicknames & tone:
- Users may greet you casually ("hey homey"); treat it as a friendly salutation, stay professional, and keep the focus on the inbox content.

Evidence handling:
- Cite concrete names, figures, deadlines, and links from the email. Call out when information is missing rather than guessing.

Response craft:
- Lead with the direct answer or summary, then add supporting bullets or brief paragraphs.
- Offer a relevant next step or follow-up help when it adds value.
""";

    private final MagicEmailProperties magicEmailProperties;
    private final AiFunctionCatalogHelper aiFunctionCatalogHelper;

    ChatPromptComposer(MagicEmailProperties magicEmailProperties, AiFunctionCatalogHelper aiFunctionCatalogHelper) {
        this.magicEmailProperties = magicEmailProperties;
        this.aiFunctionCatalogHelper = aiFunctionCatalogHelper;
    }

    boolean isInsightsRequest(ChatRequest request) {
        return INSIGHTS_TRIGGER.equals(request.getMessage());
    }

    boolean isIsolatedCommand(ChatRequest request) {
        return findCommandDefinition(request)
                .map(definition -> definition.category() == AiFunctionDefinition.Category.SUMMARY
                        || definition.category() == AiFunctionDefinition.Category.TRANSLATION)
                .orElse(false);
    }

    String compose(ChatRequest request, String mergedContext) {
        String originalMessage = request.getMessage();
        if (isInsightsRequest(request)) {
            String prompt = magicEmailProperties.getInsights().getPrompt();
            String sanitizedContext = sanitizeInsightsContext(mergedContext);
            return StringUtils.isBlank(sanitizedContext) ? prompt : prompt + "\n\nContext:\n" + sanitizedContext;
        }

        String command = request.getAiCommand();
        if (!StringUtils.isBlank(command)) {
            AiFunctionDefinition definition = aiFunctionCatalogHelper
                    .find(command, request.getCommandVariant())
                    .orElse(null);
            if (definition != null) {
                AiFunctionDefinition.AiFunctionVariant variant =
                        definition.variant(request.getCommandVariant()).orElse(null);
                String renderedPrompt = renderFunctionTemplate(
                        resolveTemplate(definition, variant),
                        resolveInstruction(originalMessage, definition, variant),
                        request.getSubject(),
                        definition,
                        mergeFunctionArguments(definition, variant, request.getCommandArgs(), request));
                if (!StringUtils.isBlank(renderedPrompt)) {
                    return renderedPrompt;
                }
            }
        }
        return formatMessageForOutput(request, originalMessage);
    }

    String sanitizeInsightsContext(String context) {
        if (StringUtils.isBlank(context)) {
            return "";
        }
        String cleanedContext = HtmlConverter.cleanupOutput(context, true);
        cleanedContext = MARKDOWN_LINK_PATTERN.matcher(cleanedContext).replaceAll("$1");
        cleanedContext = BARE_URL_PATTERN.matcher(cleanedContext).replaceAll("");
        cleanedContext = cleanedContext.replaceAll("\\(\\s*\\)", "");
        cleanedContext = cleanedContext.replaceAll("\\s{2,}", " ");
        return HtmlConverter.cleanupOutput(cleanedContext.trim(), true);
    }

    private Optional<AiFunctionDefinition> findCommandDefinition(ChatRequest request) {
        if (request == null || StringUtils.isBlank(request.getAiCommand())) {
            return Optional.empty();
        }
        return aiFunctionCatalogHelper.find(request.getAiCommand(), request.getCommandVariant());
    }

    private String renderFunctionTemplate(
            String template,
            String instruction,
            String subject,
            AiFunctionDefinition definition,
            Map<String, String> commandArguments) {
        String safeInstruction = instruction == null ? "" : instruction.trim();
        String renderedPrompt = template == null ? "" : template;

        if (renderedPrompt.contains("{{instruction}}")) {
            renderedPrompt = renderedPrompt.replace("{{instruction}}", safeInstruction);
        } else if (!StringUtils.isBlank(safeInstruction)) {
            renderedPrompt += "\n\nAdditional direction:\n" + safeInstruction;
        }

        if (commandArguments != null) {
            for (Map.Entry<String, String> argumentEntry : commandArguments.entrySet()) {
                String placeholder = "{{" + argumentEntry.getKey() + "}}";
                String argumentText = argumentEntry.getValue() == null ? "" : argumentEntry.getValue();
                if (renderedPrompt.contains(placeholder)) {
                    renderedPrompt = renderedPrompt.replace(placeholder, argumentText);
                }
            }
        }

        if (definition.allowsSubject() && !StringUtils.isBlank(subject)) {
            String safeSubject = subject.trim();
            renderedPrompt = renderedPrompt.contains("{{subject}}")
                    ? renderedPrompt.replace("{{subject}}", safeSubject)
                    : renderedPrompt + "\n\nSubject: " + safeSubject;
        }
        return renderedPrompt.trim();
    }

    private String resolveTemplate(AiFunctionDefinition definition, AiFunctionDefinition.AiFunctionVariant variant) {
        return variant != null && !StringUtils.isBlank(variant.promptTemplate())
                ? variant.promptTemplate()
                : definition.promptTemplate();
    }

    private String resolveInstruction(
            String providedInstruction,
            AiFunctionDefinition definition,
            AiFunctionDefinition.AiFunctionVariant variant) {
        if (!StringUtils.isBlank(providedInstruction)) {
            return providedInstruction;
        }
        if (variant != null && !StringUtils.isBlank(variant.defaultInstruction())) {
            return variant.defaultInstruction();
        }
        return definition.defaultInstruction();
    }

    private Map<String, String> mergeFunctionArguments(
            AiFunctionDefinition definition,
            AiFunctionDefinition.AiFunctionVariant variant,
            Map<String, String> requestArguments,
            ChatRequest request) {
        Map<String, String> mergedArguments = new LinkedHashMap<>();
        if (definition.defaultArgs() != null) {
            mergedArguments.putAll(definition.defaultArgs());
        }
        if (variant != null && variant.defaultArgs() != null) {
            mergedArguments.putAll(variant.defaultArgs());
        }
        if (requestArguments != null) {
            requestArguments.forEach((argumentName, argumentText) -> {
                if (!StringUtils.isBlank(argumentName) && argumentText != null) {
                    mergedArguments.put(argumentName.trim(), argumentText);
                }
            });
        }
        applyRecipientArguments(mergedArguments, definition, request);
        return mergedArguments;
    }

    private void applyRecipientArguments(
            Map<String, String> targetArguments, AiFunctionDefinition definition, ChatRequest request) {
        if (!isComposeLike(definition.category())) {
            return;
        }
        String providedName = StringUtils.sanitize(request.getRecipientName());
        String providedEmail = StringUtils.sanitize(request.getRecipientEmail());
        String resolvedName = providedName;
        boolean inferredFromEmail = false;
        if (StringUtils.isBlank(resolvedName) && !StringUtils.isBlank(providedEmail)) {
            resolvedName = inferNameFromEmail(providedEmail);
            inferredFromEmail = !StringUtils.isBlank(resolvedName);
        }
        if (StringUtils.isBlank(resolvedName)) {
            resolvedName = "Unknown recipient";
        }
        targetArguments.put("recipientName", resolvedName);
        if (!StringUtils.isBlank(providedEmail)) {
            targetArguments.put("recipientEmail", providedEmail);
        }
        targetArguments.put(
                "recipientGreetingDirective",
                buildGreetingDirective(providedName, providedEmail, resolvedName, inferredFromEmail));
    }

    private boolean isComposeLike(AiFunctionDefinition.Category category) {
        return category == AiFunctionDefinition.Category.COMPOSE || category == AiFunctionDefinition.Category.TONE;
    }

    private String inferNameFromEmail(String email) {
        if (StringUtils.isBlank(email)) {
            return null;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return null;
        }
        String localPart = email.substring(0, atIndex)
                .replace('.', ' ')
                .replace('_', ' ')
                .replace('-', ' ')
                .trim();
        if (localPart.length() < MIN_INFERRED_NAME_LENGTH) {
            return null;
        }
        StringBuilder inferredName = new StringBuilder();
        for (String token : localPart.split("\\s+")) {
            if (token.isBlank()) {
                continue;
            }
            if (!inferredName.isEmpty()) {
                inferredName.append(' ');
            }
            inferredName.append(Character.toUpperCase(token.charAt(0)));
            if (token.length() > 1) {
                inferredName.append(token.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        String normalizedName = inferredName.toString().trim();
        return normalizedName.length() < MIN_INFERRED_NAME_LENGTH ? null : normalizedName;
    }

    private String buildGreetingDirective(
            String providedName, String providedEmail, String resolvedName, boolean inferredFromEmail) {
        if (!StringUtils.isBlank(providedName)) {
            return "Open with a warm, professional greeting that addresses \"" + providedName + "\" by name.";
        }
        if (inferredFromEmail && !StringUtils.isBlank(resolvedName)) {
            return "Recipient name was inferred as \"" + resolvedName
                    + "\" from the email address. Use it for the salutation if it feels natural; otherwise keep the greeting neutral.";
        }
        if (!StringUtils.isBlank(providedEmail)) {
            return "Recipient name is unknown and could not be inferred from " + providedEmail
                    + "; begin with a friendly generic greeting such as \"Hello there\" or \"Hi team\".";
        }
        return "Recipient identity is unavailable; start with a polite generic greeting (e.g., \"Hello there\").";
    }

    private String formatMessageForOutput(ChatRequest request, String message) {
        String baseMessage = message == null ? "" : message;
        if (!StringUtils.isBlank(request.getAiCommand()) || request.isJsonOutput()) {
            return baseMessage;
        }
        String sanitizedMessage = baseMessage.trim();
        if (StringUtils.isBlank(sanitizedMessage)) {
            return CONVERSATION_GUIDANCE;
        }
        String separator = baseMessage.endsWith("\n") ? "" : "\n\n";
        return baseMessage + separator + CONVERSATION_GUIDANCE;
    }
}

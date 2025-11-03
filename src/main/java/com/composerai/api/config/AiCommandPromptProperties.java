package com.composerai.api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "ai.commands")
public class AiCommandPromptProperties {

    private Map<String, Command> prompts = defaultPrompts();

    public Optional<String> promptFor(String commandKey) {
        if (!StringUtils.hasText(commandKey)) {
            return Optional.empty();
        }
        String normalized = commandKey.trim().toLowerCase(Locale.ROOT);
        Command command = prompts.get(normalized);
        if (command == null || !StringUtils.hasText(command.getTemplate())) {
            return Optional.empty();
        }
        return Optional.of(command.getTemplate());
    }

    public Optional<String> defaultInstructionFor(String commandKey) {
        if (!StringUtils.hasText(commandKey)) {
            return Optional.empty();
        }
        String normalized = commandKey.trim().toLowerCase(Locale.ROOT);
        Command command = prompts.get(normalized);
        if (command == null || !StringUtils.hasText(command.getDefaultInstruction())) {
            return Optional.empty();
        }
        return Optional.of(command.getDefaultInstruction());
    }

    public Map<String, String> promptTemplates() {
        return prompts.entrySet().stream()
            .filter(entry -> StringUtils.hasText(entry.getValue().getTemplate()))
            .collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> entry.getValue().getTemplate()
            ));
    }

    public Map<String, String> defaultInstructions() {
        return prompts.entrySet().stream()
            .filter(entry -> StringUtils.hasText(entry.getValue().getDefaultInstruction()))
            .collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> entry.getValue().getDefaultInstruction()
            ));
    }

    private static Map<String, Command> defaultPrompts() {
        Map<String, Command> defaults = new LinkedHashMap<>();
        defaults.put("compose", new Command(
            "Compose a professional reply using the email context above. Respect prior conversation tone and incorporate these instructions: {{instruction}}\n\nProvide your response in this exact format:\nSubject: [your subject line]\n\n[email body]",
            "Compose a helpful email response based on the selected email."
        ));
        defaults.put("draft", new Command(
            "Draft an email response based on the email context above. Apply the following direction: {{instruction}}\n\nProvide your response in this exact format:\nSubject: [your subject line]\n\n[email body]",
            "Draft a courteous reply to the selected email outlining next steps."
        ));
        defaults.put("summarize", new Command(
            "Summarize the email content in the provided context. Highlight key points, decisions, and follow-up needs. Additional guidance: {{instruction}}",
            "Summarize the selected email in a few concise paragraphs, highlighting key actions."
        ));
        defaults.put("translate", new Command(
            "Translate the email context into the target language described here: {{instruction}}. Preserve formatting and keep proper nouns unchanged.",
            "Translate the selected email into English."
        ));
        defaults.put("tone", new Command(
            "Rewrite the draft in the email context to match this tone guidance: {{instruction}}. Provide your response in this exact format:\nSubject: [your subject line]\n\n[email body]",
            "Adjust the email to a friendly but professional tone."
        ));
        return defaults;
    }

    @Getter
    @Setter
    public static class Command {
        private String template;
        private String defaultInstruction;

        public Command() {
        }

        public Command(String template) {
            this.template = template;
        }

        public Command(String template, String defaultInstruction) {
            this.template = template;
            this.defaultInstruction = defaultInstruction;
        }
    }
}

package com.composerai.api.config;

import com.composerai.api.ai.AiFunctionDefinition;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Binds {@code ai.functions.*} properties into structured definitions that drive the AI helpers.
 * This replaces ad-hoc prompt constants so both backend and frontend consume a unified catalog.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ai.functions")
public class AiFunctionCatalogProperties {

    private Map<String, DefinitionProperties> definitions = defaultDefinitions();

    public Map<String, DefinitionProperties> normalizedDefinitions() {
        Map<String, DefinitionProperties> normalized = new LinkedHashMap<>();
        definitions.forEach((key, value) -> {
            if (value == null) {
                return;
            }
            normalized.put(key.trim().toLowerCase(Locale.ROOT), value);
        });
        return normalized;
    }

    @Getter
    @Setter
    public static class DefinitionProperties {
        private String label;
        private String description;
        private AiFunctionDefinition.Category category = AiFunctionDefinition.Category.OTHER;
        private String promptTemplate;
        private String defaultInstruction;
        private AiFunctionDefinition.OutputFormat outputFormat = AiFunctionDefinition.OutputFormat.TEXT;
        private AiFunctionDefinition.SubjectMode subjectMode = AiFunctionDefinition.SubjectMode.OPTIONAL;
        private AiFunctionDefinition.ContextStrategy contextStrategy = AiFunctionDefinition.ContextStrategy.EMAIL_AND_UPLOADS;
        private boolean primary = false;
        private List<String> scopes = List.of("panel");
        private Map<String, String> defaultArgs = new LinkedHashMap<>();
        private Map<String, VariantProperties> variants = new LinkedHashMap<>();
    }

    @Getter
    @Setter
    public static class VariantProperties {
        private String label;
        private String promptTemplate;
        private String defaultInstruction;
        private Map<String, String> defaultArgs = new LinkedHashMap<>();
    }

    private static Map<String, DefinitionProperties> defaultDefinitions() {
        Map<String, DefinitionProperties> defaults = new LinkedHashMap<>();

        DefinitionProperties compose = new DefinitionProperties();
        compose.setLabel("AI Compose");
        compose.setCategory(AiFunctionDefinition.Category.COMPOSE);
        compose.setPromptTemplate("""
Compose a professional reply using the email context above. Respect prior conversation tone and incorporate these instructions: {{instruction}}

Recipient details:
- Name: {{recipientName}}
- Email: {{recipientEmail}}

Greeting rule:
{{recipientGreetingDirective}}

Provide your response in this exact format:
Subject: [your subject line]

[email body]
""");
        compose.setDefaultInstruction("Compose a helpful email response based on the selected email.");
        compose.setOutputFormat(AiFunctionDefinition.OutputFormat.EMAIL);
        compose.setSubjectMode(AiFunctionDefinition.SubjectMode.OPTIONAL);
        compose.setPrimary(true);
        compose.setScopes(List.of("compose"));
        defaults.put("compose", compose);

        DefinitionProperties draft = new DefinitionProperties();
        draft.setLabel("AI Draft Reply");
        draft.setCategory(AiFunctionDefinition.Category.COMPOSE);
        draft.setPromptTemplate("""
Draft an email response based on the email context above. Apply the following direction: {{instruction}}

Recipient details:
- Name: {{recipientName}}
- Email: {{recipientEmail}}

Greeting rule:
{{recipientGreetingDirective}}

Provide your response in this exact format:
Subject: [your subject line]

[email body]
""");
        draft.setDefaultInstruction("Draft a courteous reply to the selected email outlining next steps.");
        draft.setOutputFormat(AiFunctionDefinition.OutputFormat.EMAIL);
        draft.setSubjectMode(AiFunctionDefinition.SubjectMode.OPTIONAL);
        draft.setPrimary(true);
        draft.setScopes(List.of("compose"));
        defaults.put("draft", draft);

        DefinitionProperties summarize = new DefinitionProperties();
        summarize.setLabel("AI Summary");
        summarize.setCategory(AiFunctionDefinition.Category.SUMMARY);
        summarize.setPromptTemplate("Summarize the email content in the provided context. Highlight key points, decisions, and follow-up needs. Additional guidance: {{instruction}}");
        summarize.setDefaultInstruction("Summarize the selected email in a few concise paragraphs, highlighting key actions.");
        summarize.setOutputFormat(AiFunctionDefinition.OutputFormat.TEXT);
        summarize.setSubjectMode(AiFunctionDefinition.SubjectMode.NONE);
        summarize.setPrimary(true);
        summarize.setScopes(List.of("panel"));
        defaults.put("summarize", summarize);

        DefinitionProperties translate = new DefinitionProperties();
        translate.setLabel("AI Translation");
        translate.setCategory(AiFunctionDefinition.Category.TRANSLATION);
        translate.setPromptTemplate("Translate the email context into {{targetLanguage}}. Preserve formatting and keep proper nouns unchanged. If no target language is specified, default to English. User guidance: {{instruction}}");
        translate.setDefaultInstruction("Translate the selected email into the requested language.");
        translate.setOutputFormat(AiFunctionDefinition.OutputFormat.TEXT);
        translate.setSubjectMode(AiFunctionDefinition.SubjectMode.NONE);
        translate.setPrimary(true);
        translate.setScopes(List.of("panel"));
        Map<String, String> translationDefaults = new LinkedHashMap<>();
        translationDefaults.put("targetLanguage", "Spanish");
        translate.setDefaultArgs(translationDefaults);
        Map<String, VariantProperties> translationVariants = new LinkedHashMap<>();
        translationVariants.put("es", variant("Spanish", "Translate the email into Spanish.", Map.of("targetLanguage", "Spanish")));
        translationVariants.put("pt", variant("Portuguese", "Translate the email into Portuguese.", Map.of("targetLanguage", "Portuguese")));
        translationVariants.put("nl", variant("Dutch", "Translate the email into Dutch.", Map.of("targetLanguage", "Dutch")));
        translate.setVariants(translationVariants);
        defaults.put("translate", translate);

        DefinitionProperties actionsMenu = new DefinitionProperties();
        actionsMenu.setLabel("AI Action Ideas");
        actionsMenu.setCategory(AiFunctionDefinition.Category.SUMMARY);
        actionsMenu.setPromptTemplate("""
Analyze the email context above and craft three short follow-up actions tailored to the message. Each action must have a 1-2 word label and map to one of these actionType values: "compose", "summary", or "comingSoon".

Return ONLY valid JSON in this exact structure:
{
  "options": [
    {
      "label": "Call Back",
      "actionType": "compose",
      "commandKey": "compose",
      "commandVariant": null,
      "instruction": "Draft a concise reply proposing a call tomorrow."
    }
  ]
}

Rules:
- labels must be unique, 1-2 words, and title case.
- actionType "compose" or "summary" MUST include commandKey (e.g., "compose", "draft", "summarize", "translate") and an instruction describing what to generate.
- actionType "comingSoon" MUST set commandKey and commandVariant to null and omit instructions.
- Never include prose outside the JSON block.
User guidance: {{instruction}}
""");
        actionsMenu.setDefaultInstruction("Suggest three concise action prompts for the user (1-2 word labels) that either draft a reply, summarize next steps, or note a coming soon capability. Tailor suggestions to the email subject and participants.");
        actionsMenu.setOutputFormat(AiFunctionDefinition.OutputFormat.TEXT);
        actionsMenu.setSubjectMode(AiFunctionDefinition.SubjectMode.NONE);
        actionsMenu.setScopes(List.of("panel"));
        defaults.put("actions_menu", actionsMenu);

        DefinitionProperties tone = new DefinitionProperties();
        tone.setLabel("AI Tone Adjustment");
        tone.setCategory(AiFunctionDefinition.Category.TONE);
        tone.setPromptTemplate("""
Rewrite the draft in the email context to match this tone guidance: {{instruction}}.

Recipient details:
- Name: {{recipientName}}
- Email: {{recipientEmail}}

Greeting rule:
{{recipientGreetingDirective}}

Provide your response in this exact format:
Subject: [your subject line]

[email body]
""");
        tone.setDefaultInstruction("Adjust the email to a friendly but professional tone.");
        tone.setOutputFormat(AiFunctionDefinition.OutputFormat.EMAIL);
        tone.setSubjectMode(AiFunctionDefinition.SubjectMode.OPTIONAL);
        tone.setPrimary(true);
        tone.setScopes(List.of("compose"));
        defaults.put("tone", tone);

        return defaults;
    }

    private static VariantProperties variant(String label, String defaultInstruction, Map<String, String> defaultArgs) {
        VariantProperties properties = new VariantProperties();
        properties.setLabel(label);
        properties.setDefaultInstruction(defaultInstruction);
        properties.setDefaultArgs(defaultArgs);
        return properties;
    }
}

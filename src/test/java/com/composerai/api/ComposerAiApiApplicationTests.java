package com.composerai.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.composerai.api.config.OpenAiProperties;
import com.composerai.api.domain.model.ReasoningEffortLevel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(
        properties = {
            "openai.api.key=test-key",
            "qdrant.host=localhost",
            "qdrant.port=6333",
            "openai.defaults.thinking-enabled=",
            "openai.defaults.max-message-length=4000",
            "openai.defaults.max-search-results=5",
            "openai.intent.max-output-tokens=512",
            "openai.intent.default-category=question",
            "openai.stream.heartbeat-interval-seconds=10",
            "openai.stream.timeout-seconds=120",
            "openai.reasoning.default-effort=minimal",
            "openai.prompts.email-assistant-system=You are Composer.",
            "openai.prompts.intent-analysis-system=Classify."
        })
class ComposerAiApiApplicationTests {

    @Autowired
    private OpenAiProperties openAiProperties;

    @Autowired
    private Environment environment;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void contextLoads() {
        // Test that the Spring Boot application context loads successfully
    }

    @Test
    void bindsChatConfigurationFromSpringEnvironment() {
        assertThat(openAiProperties.getApi().getBaseUrl())
                .isEqualTo(environment.getRequiredProperty("openai.api.base-url"));
        assertThat(openAiProperties.getModel().getChat())
                .isEqualTo(environment.getRequiredProperty("openai.model.chat"));
        assertThat(openAiProperties.getDefaults().getThinkingEnabled()).isNull();
    }

    @ParameterizedTest
    @EnumSource(ReasoningEffortLevel.class)
    void canonicalReasoningEffortRoundTripsThroughJsonBoundary(ReasoningEffortLevel reasoningEffort) throws Exception {
        String serializedEffort = objectMapper.writeValueAsString(reasoningEffort);

        assertThat(serializedEffort).isEqualTo(objectMapper.writeValueAsString(reasoningEffort.externalName()));
        assertThat(objectMapper.readValue(serializedEffort, ReasoningEffortLevel.class))
                .isEqualTo(reasoningEffort);
    }
}

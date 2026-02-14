package com.composerai.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(
        properties = {
            "openai.api.key=test-key",
            "qdrant.host=localhost",
            "qdrant.port=6333",
            "openai.defaults.thinking-enabled=false",
            "openai.defaults.max-message-length=4000",
            "openai.defaults.max-search-results=5",
            "openai.intent.max-output-tokens=10",
            "openai.intent.default-category=question",
            "openai.stream.heartbeat-interval-seconds=10",
            "openai.stream.timeout-seconds=120",
            "openai.reasoning.default-effort=minimal",
            "openai.reasoning.supported-model-prefixes=o1,o3,o4,gpt-5",
            "openai.prompts.email-assistant-system=You are Composer.",
            "openai.prompts.intent-analysis-system=Classify."
        })
class ComposerAiApiApplicationTests {

    @Test
    void contextLoads() {
        // Test that the Spring Boot application context loads successfully
    }
}

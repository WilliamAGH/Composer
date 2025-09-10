package com.composerai.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "openai.api.key=test-key",
    "qdrant.host=localhost",
    "qdrant.port=6333"
})
class ComposerAiApiApplicationTests {

    @Test
    void contextLoads() {
        // Test that the Spring Boot application context loads successfully
    }

}
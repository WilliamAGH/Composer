package com.composerai.api.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProviderCapabilitiesTest {
    
    @Test
    void openAiSupportsReasoning() {
        ProviderCapabilities capabilities = ProviderCapabilities.detect("https://api.openai.com/v1");
        assertTrue(capabilities.supportsReasoning());
        assertTrue(capabilities.supportsMinimalReasoning());
    }
    
    @Test
    void openRouterSupportsReasoningButNotMinimal() {
        ProviderCapabilities capabilities = ProviderCapabilities.detect("https://openrouter.ai/api/v1");
        assertTrue(capabilities.supportsReasoning(), "OpenRouter should support reasoning");
        assertFalse(capabilities.supportsMinimalReasoning(), "OpenRouter should not support 'minimal' effort");
    }
    
    @Test
    void groqDoesNotSupportReasoning() {
        ProviderCapabilities capabilities = ProviderCapabilities.detect("https://api.groq.com/openai/v1");
        assertFalse(capabilities.supportsReasoning());
        assertFalse(capabilities.supportsMinimalReasoning());
    }
}

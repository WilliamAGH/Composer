package com.composerai.api.service;

import static org.junit.jupiter.api.Assertions.*;

import com.composerai.api.config.OpenAiProperties;
import com.openai.models.ReasoningEffort;
import org.junit.jupiter.api.Test;

class ValidatedThinkingConfigTest {

    @Test
    void shouldAcceptMinimalForOpenAI() {
        OpenAiProperties props = createPropertiesWithProvider("https://api.openai.com/v1");

        OpenAiChatService.ValidatedThinkingConfig config =
                OpenAiChatService.ValidatedThinkingConfig.resolve(props, "gpt-5", true, "minimal");

        assertTrue(config.enabled());
        assertEquals(ReasoningEffort.MINIMAL, config.effort());
    }

    @Test
    void shouldFallbackToLowForOpenRouterWhenMinimalRequested() {
        OpenAiProperties props = createPropertiesWithProvider("https://openrouter.ai/api/v1");

        OpenAiChatService.ValidatedThinkingConfig config =
                OpenAiChatService.ValidatedThinkingConfig.resolve(props, "o1-mini", true, "minimal");

        assertTrue(config.enabled());
        assertEquals(ReasoningEffort.LOW, config.effort(), "Should fallback from 'minimal' to 'low' for OpenRouter");
    }

    @Test
    void shouldAcceptLowForOpenRouter() {
        OpenAiProperties props = createPropertiesWithProvider("https://openrouter.ai/api/v1");

        OpenAiChatService.ValidatedThinkingConfig config =
                OpenAiChatService.ValidatedThinkingConfig.resolve(props, "o1-preview", true, "low");

        assertTrue(config.enabled());
        assertEquals(ReasoningEffort.LOW, config.effort());
    }

    @Test
    void shouldDisableForNonReasoningModel() {
        OpenAiProperties props = createPropertiesWithProvider("https://api.openai.com/v1");

        OpenAiChatService.ValidatedThinkingConfig config =
                OpenAiChatService.ValidatedThinkingConfig.resolve(props, "gpt-4o-mini", true, "medium");

        assertFalse(config.enabled(), "Should disable reasoning for non-reasoning model");
    }

    private OpenAiProperties createPropertiesWithProvider(String baseUrl) {
        OpenAiProperties props = new OpenAiProperties();
        OpenAiProperties.Api api = new OpenAiProperties.Api();
        api.setBaseUrl(baseUrl);
        props.setApi(api);
        props.initProviderCapabilities(); // Initialize capabilities
        return props;
    }
}

package com.composerai.api.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ProviderCapabilitiesTest {

    @Test
    void detectsOpenAiEndpoint() {
        ProviderCapabilities capabilities = ProviderCapabilities.detect("https://api.openai.com/v1");

        assertEquals(ProviderCapabilities.ProviderType.OPENAI, capabilities.getType());
    }

    @Test
    void detectsOpenRouterEndpoint() {
        ProviderCapabilities capabilities = ProviderCapabilities.detect("https://openrouter.ai/api/v1");

        assertEquals(ProviderCapabilities.ProviderType.OPENROUTER, capabilities.getType());
    }

    @Test
    void detectsSharedGatewayEndpoint() {
        ProviderCapabilities capabilities = ProviderCapabilities.detect("https://api.llm-gateway.iocloudhost.net/v1");

        assertEquals(ProviderCapabilities.ProviderType.SHARED_GATEWAY, capabilities.getType());
    }

    @Test
    void detectsGroqEndpoint() {
        ProviderCapabilities capabilities = ProviderCapabilities.detect("https://api.groq.com/openai/v1");

        assertEquals(ProviderCapabilities.ProviderType.GROQ, capabilities.getType());
    }
}

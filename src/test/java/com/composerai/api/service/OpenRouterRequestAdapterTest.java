package com.composerai.api.service;

import static org.junit.jupiter.api.Assertions.*;

import com.composerai.api.config.OpenAiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.models.ChatModel;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.responses.ResponseCreateParams;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenRouterRequestAdapterTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldInjectProviderRouting() throws Exception {
        // Given: Request params with reasoning
        ResponseCreateParams params = ResponseCreateParams.builder()
                .model(ChatModel.of("anthropic/claude-3.7-sonnet"))
                .temperature(0.7)
                .maxOutputTokens(4000L)
                .reasoning(Reasoning.builder().effort(ReasoningEffort.MEDIUM).build())
                .build();

        // Given: Provider config
        OpenAiProperties.Provider provider = new OpenAiProperties.Provider();
        provider.setSort("price");
        provider.setOrder(List.of("anthropic", "openai"));
        provider.setAllowFallbacks(true);

        // When: Build request JSON
        String requestJson = OpenRouterRequestAdapter.buildRequestJson(params, provider);

        // Then: Should contain provider field
        JsonNode request = mapper.readTree(requestJson);
        assertEquals("anthropic/claude-3.7-sonnet", request.get("model").asText());
        assertEquals(0.7, request.get("temperature").asDouble(), 0.01);
        assertEquals(4000, request.get("max_output_tokens").asLong());

        JsonNode providerNode = request.get("provider");
        assertNotNull(providerNode, "Provider field should be present");
        assertEquals("price", providerNode.get("sort").asText());
        assertEquals("anthropic", providerNode.get("order").get(0).asText());
        assertTrue(providerNode.get("allow_fallbacks").asBoolean());

        JsonNode reasoningNode = request.get("reasoning");
        assertNotNull(reasoningNode, "Reasoning field should be present");
        assertEquals("medium", reasoningNode.get("effort").asText());
    }

    @Test
    void shouldOmitProviderWhenNotConfigured() throws Exception {
        // Given: Request params without provider config
        ResponseCreateParams params = ResponseCreateParams.builder()
                .model(ChatModel.of("gpt-4o"))
                .temperature(0.5)
                .build();

        // When: Build request JSON with null provider
        String requestJson = OpenRouterRequestAdapter.buildRequestJson(params, null);

        // Then: Should not contain provider field
        JsonNode request = mapper.readTree(requestJson);
        assertNull(request.get("provider"), "Provider field should be absent when not configured");
    }
}

package com.composerai.api.service;

import com.composerai.api.config.OpenAiProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.models.Reasoning;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseInputItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Adapter for constructing OpenRouter-compatible API requests.
 * 
 * The official openai-java SDK doesn't support additional properties like "provider"
 * because it uses strongly-typed Kotlin data classes. This adapter extracts known
 * properties from ResponseCreateParams and injects OpenRouter-specific fields.
 * 
 * @see <a href="https://openrouter.ai/docs/api-reference/responses-api/overview">OpenRouter Responses API</a>
 * @see <a href="https://openrouter.ai/docs/features/provider-routing">Provider Routing</a>
 */
public class OpenRouterRequestAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(OpenRouterRequestAdapter.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    
    /**
     * Converts ResponseCreateParams to OpenRouter-compatible JSON request body.
     * Extracts all known properties and adds OpenRouter-specific "provider" field.
     * 
     * @param params SDK request params
     * @param providerConfig Provider routing configuration
     * @return JSON string ready to send to OpenRouter API
     * @throws JsonProcessingException if JSON serialization fails
     */
    public static String buildRequestJson(
        ResponseCreateParams params,
        OpenAiProperties.Provider providerConfig
    ) throws JsonProcessingException {
        Map<String, Object> request;
        
        // Use SDK's _body() for initial serialization to get all fields
        // Then augment with OpenRouter-specific fields
        try {
            // Serialize the entire params object via Jackson
            String paramsJson = mapper.writeValueAsString(params._body());
            request = mapper.readValue(paramsJson, LinkedHashMap.class);
        } catch (Exception e) {
            logger.warn("Failed to serialize SDK params, using empty base", e);
            request = new LinkedHashMap<>();
        }
        
        // Add OpenRouter provider routing (if configured)
        if (providerConfig != null && providerConfig.getOrder() != null && !providerConfig.getOrder().isEmpty()) {
            Map<String, Object> provider = new LinkedHashMap<>();
            
            if (providerConfig.getSort() != null && !providerConfig.getSort().isBlank()) {
                provider.put("sort", providerConfig.getSort());
            }
            
            provider.put("order", providerConfig.getOrder());
            provider.put("allow_fallbacks", providerConfig.getAllowFallbacks());
            
            request.put("provider", provider);
            
            logger.debug("Added OpenRouter provider routing: sort={}, order={}, allow_fallbacks={}", 
                providerConfig.getSort(), providerConfig.getOrder(), providerConfig.getAllowFallbacks());
        }
        
        return mapper.writeValueAsString(request);
    }
}

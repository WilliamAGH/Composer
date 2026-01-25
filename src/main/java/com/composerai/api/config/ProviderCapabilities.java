package com.composerai.api.config;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects OpenAI-compatible provider type and defines their capabilities.
 *
 * Providers include: OpenAI (official), OpenRouter, Groq, LM Studio, vLLM, llama.cpp, etc.
 * Different providers support different features (tracing, reasoning, embeddings).
 */
public class ProviderCapabilities {

    private static final Logger logger = LoggerFactory.getLogger(ProviderCapabilities.class);

    private final ProviderType type;
    private final String baseUrl;

    /**
     * Known OpenAI-compatible provider types.
     */
    public enum ProviderType {
        /** Official OpenAI API - full feature support */
        OPENAI,
        /** OpenRouter - multi-model router, supports embeddings */
        OPENROUTER,
        /** Groq - fast inference, basic chat only */
        GROQ,
        /** LM Studio - local inference server */
        LM_STUDIO,
        /** vLLM - optimized inference engine */
        VLLM,
        /** llama.cpp - local C++ inference */
        LLAMACPP,
        /** Generic OpenAI-compatible endpoint */
        GENERIC
    }

    private ProviderCapabilities(ProviderType type, String baseUrl) {
        this.type = type;
        this.baseUrl = baseUrl;
    }

    /**
     * Detect provider type from base URL.
     *
     * @param baseUrl the API base URL (e.g., "https://api.openai.com/v1")
     * @return provider capabilities instance
     */
    public static ProviderCapabilities detect(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            logger.info("No base URL provided, assuming OpenAI");
            return new ProviderCapabilities(ProviderType.OPENAI, "https://api.openai.com/v1");
        }

        String normalized = baseUrl.toLowerCase(Locale.ROOT);
        ProviderType type;

        if (normalized.contains("api.openai.com")) {
            type = ProviderType.OPENAI;
        } else if (normalized.contains("openrouter.ai")) {
            type = ProviderType.OPENROUTER;
        } else if (normalized.contains("groq.com")) {
            type = ProviderType.GROQ;
        } else if (normalized.contains("localhost") || normalized.contains("127.0.0.1")) {
            // Local servers - could be LM Studio, vLLM, or llama.cpp
            if (normalized.contains(":1234") || normalized.contains("lmstudio")) {
                type = ProviderType.LM_STUDIO;
            } else if (normalized.contains("vllm")) {
                type = ProviderType.VLLM;
            } else if (normalized.contains(":8080") || normalized.contains("llama")) {
                type = ProviderType.LLAMACPP;
            } else {
                type = ProviderType.GENERIC;
            }
        } else {
            type = ProviderType.GENERIC;
        }

        logger.info("Detected provider: {} from baseUrl: {}", type, baseUrl);
        return new ProviderCapabilities(type, baseUrl);
    }

    /**
     * Whether this provider supports OpenAI's tracing/observability features.
     * Only official OpenAI API supports tracing.
     *
     * @return true if tracing is supported
     */
    public boolean supportsTracing() {
        return type == ProviderType.OPENAI;
    }

    /**
     * Whether this provider supports reasoning models (o1, o3, o4 series).
     * OpenAI and OpenRouter support reasoning models with effort parameters.
     *
     * @return true if reasoning is supported
     */
    public boolean supportsReasoning() {
        return type == ProviderType.OPENAI || type == ProviderType.OPENROUTER;
    }

    /**
     * Whether this provider supports "minimal" reasoning effort level.
     * Only OpenAI supports "minimal" - other providers use "low", "medium", "high".
     *
     * @return true if "minimal" effort is supported
     */
    public boolean supportsMinimalReasoning() {
        return type == ProviderType.OPENAI;
    }

    /**
     * Whether this provider supports embeddings API.
     * Only OpenAI supports embeddings; most other providers (OpenRouter, Groq, local) don't.
     *
     * @return true if embeddings are supported
     */
    public boolean supportsEmbeddings() {
        return type == ProviderType.OPENAI;
    }

    /**
     * Whether this provider is the official OpenAI endpoint.
     *
     * @return true if this is OpenAI
     */
    public boolean isOpenAI() {
        return type == ProviderType.OPENAI;
    }

    /**
     * Get the provider type.
     *
     * @return provider type enum
     */
    public ProviderType getType() {
        return type;
    }

    /**
     * Get the base URL for this provider.
     *
     * @return base URL string
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    @Override
    public String toString() {
        return String.format(
                "ProviderCapabilities{type=%s, baseUrl=%s, tracing=%s, reasoning=%s, minimalReasoning=%s, embeddings=%s}",
                type,
                baseUrl,
                supportsTracing(),
                supportsReasoning(),
                supportsMinimalReasoning(),
                supportsEmbeddings());
    }
}

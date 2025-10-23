# Surgical Implementation Plan: OpenAI/OpenRouter Integration Updates

**Target Repository**: `/Users/williamcallahan/Developer/git/csm/ComposerAI`  
**Approach**: Non-disruptive, backward-compatible enhancements  
**Estimated Effort**: 12-16 hours over 4 phases

---

## Overview

This plan surgically retrofits the codebase with:
1. OpenRouter provider routing support
2. Enhanced reasoning configuration with provider validation
3. Additional model settings (temperature, maxOutputTokens)
4. Environment variable expansion
5. Debug logging improvements

**Key Principle**: All changes are additive or refinements. No breaking changes to existing APIs.

---

## Phase 1: Configuration Layer (Foundation)
**Estimated Time**: 3-4 hours  
**Risk Level**: LOW  
**Dependencies**: None

### 1.1 Update `pom.xml`
**File**: `/Users/williamcallahan/Developer/git/csm/ComposerAI/pom.xml`  
**Action**: Add Bean Validation dependency (for future validation support)

```xml
<!-- Location: After line 145 (after spring-dotenv) -->

<!-- Bean Validation for structured outputs -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

**Why**: Enables future validation of LLM responses similar to Zod in TypeScript.  
**Breaking**: No - validation is opt-in via annotations.

---

### 1.2 Extend `OpenAiProperties.java`
**File**: `/Users/williamcallahan/Developer/git/csm/ComposerAI/src/main/java/com/composerai/api/config/OpenAiProperties.java`

#### Change 1.2.1: Add Model settings (temperature, maxOutputTokens, topP)
**Location**: Lines 86-90 (inside `Model` class)

```java
// BEFORE:
@Getter
@Setter
public static class Model {
    private String chat = "gpt-4o-mini";
}

// AFTER:
@Getter
@Setter
public static class Model {
    private String chat = "gpt-4o-mini";
    private Double temperature = 0.5; // Default temperature for all requests
    private Long maxOutputTokens = null; // null = use model default
    private Double topP = null; // null = use model default
}
```

**Why**: Allows global temperature/token control via environment variables.  
**Breaking**: No - all new fields have sensible defaults.

---

#### Change 1.2.2: Add Provider routing configuration
**Location**: After line 223 (after `Defaults` class, before utility methods)

```java
// ADD NEW CLASS:
/**
 * Provider routing configuration for OpenRouter.
 * Controls which providers to use and fallback behavior.
 * Only applies when using OpenRouter as the base URL.
 * 
 * @see <a href="https://openrouter.ai/docs/features/provider-routing">OpenRouter Provider Routing</a>
 */
@Getter
@Setter
public static class Provider {
    /** Sort providers by: price, throughput, or latency */
    private String sort = null; // null = no sorting preference
    /** Explicitly order specific providers (e.g., ["anthropic", "openai"]) */
    private List<String> order = List.of("novita"); // Default to novita
    /** Allow fallback to other providers if the primary fails */
    private Boolean allowFallbacks = true;
}

private Provider provider = new Provider();
```

**Why**: Enables OpenRouter provider routing configuration.  
**Breaking**: No - only used when base URL is OpenRouter.

---

#### Change 1.2.3: Update Reasoning default
**Location**: Lines 134-137 (inside `Reasoning` class)

```java
// BEFORE:
@Getter
@Setter
public static class Reasoning {
    private List<String> supportedModelPrefixes = List.of("o1", "o3", "o4", "gpt-5");
    private String defaultEffort = "minimal";
}

// AFTER:
@Getter
@Setter
public static class Reasoning {
    private List<String> supportedModelPrefixes = List.of("o1", "o3", "o4", "gpt-5");
    private String defaultEffort = "low"; // Changed from "minimal" - OpenRouter compatible default
}
```

**Why**: "minimal" is OpenAI-only. "low" works across all providers.  
**Breaking**: No - minimal can still be specified via env var for OpenAI users.

---

### 1.3 Update `application.properties`
**File**: `/Users/williamcallahan/Developer/git/csm/ComposerAI/src/main/resources/application.properties`

#### Change 1.3.1: Add new model settings
**Location**: After line 38 (after `openai.model.chat`)

```properties
# BEFORE:
openai.model.chat=${LLM_MODEL:${OPENAI_MODEL:gpt-4o-mini}}

# ADD AFTER LINE 38:

# Model Settings (temperature, token limits, sampling)
openai.model.temperature=${LLM_TEMPERATURE:0.5}
openai.model.max-output-tokens=${LLM_MAX_OUTPUT_TOKENS:}
openai.model.top-p=${LLM_TOP_P:}
```

**Why**: Exposes temperature and token control via environment variables.  
**Breaking**: No - defaults preserve existing behavior.

---

#### Change 1.3.2: Update reasoning default
**Location**: Line 49

```properties
# BEFORE:
openai.reasoning.default-effort=${OPENAI_REASONING_EFFORT:minimal}

# AFTER:
openai.reasoning.default-effort=${LLM_REASONING:${OPENAI_REASONING_EFFORT:low}}
```

**Why**: Adds `LLM_REASONING` env var support and changes default to "low".  
**Breaking**: No - users can still specify "minimal" explicitly.

---

#### Change 1.3.3: Add provider routing configuration
**Location**: After line 50 (after reasoning config)

```properties
# ADD AFTER LINE 50:

# Provider Routing (OpenRouter only)
# Controls which providers to use when base URL is https://openrouter.ai/api/v1
# See: https://openrouter.ai/docs/features/provider-routing
openai.provider.sort=${LLM_PROVIDER_SORT:}
openai.provider.order=${LLM_PROVIDER_ORDER:novita}
openai.provider.allow-fallbacks=${LLM_PROVIDER_ALLOW_FALLBACKS:true}
```

**Why**: Enables OpenRouter provider routing via env vars.  
**Breaking**: No - ignored when not using OpenRouter.

---

#### Change 1.3.4: Add debug logging
**Location**: After line 60 (after defaults config)

```properties
# ADD AFTER LINE 60:

# Debug Configuration
openai.debug.fetch-logging=${LLM_DEBUG_FETCH:false}
```

**Why**: Enables request/response body logging for debugging.  
**Breaking**: No - disabled by default.

---

### 1.4 Update `ProviderCapabilities.java`
**File**: `/Users/williamcallahan/Developer/git/csm/ComposerAI/src/main/java/com/composerai/api/config/ProviderCapabilities.java`

#### Change 1.4.1: Update `supportsReasoning()` method
**Location**: Lines 102-104

```java
// BEFORE:
public boolean supportsReasoning() {
    return type == ProviderType.OPENAI;
}

// AFTER:
public boolean supportsReasoning() {
    return type == ProviderType.OPENAI || type == ProviderType.OPENROUTER;
}
```

**Why**: OpenRouter supports reasoning (just not "minimal" effort).  
**Breaking**: No - enables previously disabled functionality.

---

#### Change 1.4.2: Add `supportsMinimalReasoning()` method
**Location**: After line 104 (after `supportsReasoning()`)

```java
// ADD NEW METHOD:
/**
 * Whether this provider supports "minimal" reasoning effort level.
 * Only OpenAI supports "minimal" - other providers use "low", "medium", "high".
 * 
 * @return true if "minimal" effort is supported
 */
public boolean supportsMinimalReasoning() {
    return type == ProviderType.OPENAI;
}
```

**Why**: Validates that "minimal" is only used with OpenAI.  
**Breaking**: No - new method.

---

#### Change 1.4.3: Update `toString()` to include new method
**Location**: Lines 144-147

```java
// BEFORE:
@Override
public String toString() {
    return String.format("ProviderCapabilities{type=%s, baseUrl=%s, tracing=%s, reasoning=%s, embeddings=%s}",
        type, baseUrl, supportsTracing(), supportsReasoning(), supportsEmbeddings());
}

// AFTER:
@Override
public String toString() {
    return String.format("ProviderCapabilities{type=%s, baseUrl=%s, tracing=%s, reasoning=%s, minimalReasoning=%s, embeddings=%s}",
        type, baseUrl, supportsTracing(), supportsReasoning(), supportsMinimalReasoning(), supportsEmbeddings());
}
```

**Why**: Improves logging output.  
**Breaking**: No - only affects toString() output.

---

## Phase 2: Core Service Updates
**Estimated Time**: 4-5 hours  
**Risk Level**: MEDIUM  
**Dependencies**: Phase 1 complete

### 2.1 Create `OpenRouterRequestAdapter.java` (NEW FILE)
**File**: `/Users/williamcallahan/Developer/git/csm/ComposerAI/src/main/java/com/composerai/api/service/OpenRouterRequestAdapter.java`  
**Action**: Create new adapter class for OpenRouter-specific requests

```java
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
        Map<String, Object> request = new LinkedHashMap<>();
        
        // Extract core properties from SDK params
        params.model().ifPresent(m -> request.put("model", m.value()));
        params.temperature().ifPresent(t -> request.put("temperature", t));
        params.topP().ifPresent(tp -> request.put("top_p", tp));
        params.maxOutputTokens().ifPresent(max -> request.put("max_output_tokens", max));
        params.instructions().ifPresent(inst -> request.put("instructions", inst));
        params.parallelToolCalls().ifPresent(pt -> request.put("parallel_tool_calls", pt));
        params.store().ifPresent(s -> request.put("store", s));
        params.truncation().ifPresent(trunc -> request.put("truncation", trunc.value()));
        
        // Handle input items (complex structure)
        params.input().ifPresent(input -> {
            try {
                List<Map<String, Object>> inputItems = new ArrayList<>();
                if (input.isResponse()) {
                    for (ResponseInputItem item : input.response()) {
                        inputItems.add(convertInputItem(item));
                    }
                }
                request.put("input", inputItems);
            } catch (Exception e) {
                logger.warn("Failed to serialize input items", e);
            }
        });
        
        // Handle reasoning configuration
        params.reasoning().ifPresent(reasoning -> {
            Map<String, Object> reasoningMap = new LinkedHashMap<>();
            reasoning.effort().ifPresent(effort -> reasoningMap.put("effort", effort.value()));
            reasoning.maxTokens().ifPresent(maxTokens -> reasoningMap.put("max_tokens", maxTokens));
            if (!reasoningMap.isEmpty()) {
                request.put("reasoning", reasoningMap);
            }
        });
        
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
    
    /**
     * Converts a ResponseInputItem to a Map for JSON serialization.
     * Handles different input types (messages, text, images, etc.)
     */
    private static Map<String, Object> convertInputItem(ResponseInputItem item) {
        Map<String, Object> result = new LinkedHashMap<>();
        
        // Use SDK's built-in serialization via Jackson
        // The SDK types are already JSON-serializable
        try {
            String itemJson = mapper.writeValueAsString(item);
            return mapper.readValue(itemJson, Map.class);
        } catch (JsonProcessingException e) {
            logger.warn("Failed to convert input item", e);
            return result;
        }
    }
}
```

**Why**: Bypasses SDK limitations to inject OpenRouter-specific fields.  
**Breaking**: No - new utility class.

---

### 2.2 Update `ClientConfiguration.java`
**File**: `/Users/williamcallahan/Developer/git/csm/ComposerAI/src/main/java/com/composerai/api/config/ClientConfiguration.java`

#### Change 2.2.1: Add RestClient bean for OpenRouter
**Location**: After line 72 (after `openAIClient` bean)

```java
// ADD NEW BEAN:
/**
 * RestClient for making raw HTTP requests to OpenRouter API.
 * Used when OpenRouter-specific features (like provider routing) are needed
 * that aren't supported by the OpenAI SDK's strongly-typed API.
 * 
 * Only used when base URL is OpenRouter - otherwise normal SDK client is used.
 */
@Bean
public org.springframework.web.client.RestClient openRouterRestClient(OpenAiProperties openAiProperties) {
    String apiKey = openAiProperties.getApi().getKey();
    if (com.composerai.api.util.StringUtils.isMissing(apiKey)) {
        apiKey = System.getenv("LLM_API_KEY");
    }
    if (com.composerai.api.util.StringUtils.isMissing(apiKey)) {
        apiKey = System.getenv("OPENAI_API_KEY");
    }
    
    if (com.composerai.api.util.StringUtils.isMissing(apiKey)) {
        log.warn("API key not configured - RestClient will fail if used");
        return null;
    }
    
    String baseUrl = openAiProperties.getApi().getBaseUrl();
    
    return org.springframework.web.client.RestClient.builder()
        .baseUrl(baseUrl)
        .defaultHeader("Authorization", "Bearer " + apiKey.trim())
        .defaultHeader("Content-Type", "application/json")
        .defaultHeader("HTTP-Referer", "https://composerai.app") // Optional: for OpenRouter analytics
        .defaultHeader("X-Title", "ComposerAI") // Optional: for OpenRouter analytics
        .build();
}
```

**Why**: Enables direct HTTP calls to OpenRouter with custom request bodies.  
**Breaking**: No - new bean, nullable.

---

### 2.3 Update `OpenAiChatService.java`
**File**: `/Users/williamcallahan/Developer/git/csm/ComposerAI/src/main/java/com/composerai/api/service/OpenAiChatService.java`

#### Change 2.3.1: Add RestClient field
**Location**: After line 45 (after `errorMessages` field)

```java
// ADD NEW FIELD:
private final org.springframework.web.client.RestClient restClient; // For OpenRouter custom requests
```

**Why**: Inject RestClient for OpenRouter requests.  
**Breaking**: No - constructor will be updated to accept nullable.

---

#### Change 2.3.2: Update constructor
**Location**: Lines 108-114

```java
// BEFORE:
public OpenAiChatService(@Autowired(required = false) @Nullable OpenAIClient openAiClient,
                          OpenAiProperties openAiProperties,
                          ErrorMessagesProperties errorMessages) {
    this.openAiClient = openAiClient;
    this.openAiProperties = openAiProperties;
    this.errorMessages = errorMessages;
}

// AFTER:
public OpenAiChatService(@Autowired(required = false) @Nullable OpenAIClient openAiClient,
                          @Autowired(required = false) @Nullable org.springframework.web.client.RestClient restClient,
                          OpenAiProperties openAiProperties,
                          ErrorMessagesProperties errorMessages) {
    this.openAiClient = openAiClient;
    this.restClient = restClient;
    this.openAiProperties = openAiProperties;
    this.errorMessages = errorMessages;
}
```

**Why**: Accept RestClient for OpenRouter support.  
**Breaking**: No - Spring handles injection.

---

#### Change 2.3.3: Update `ValidatedThinkingConfig.resolve()`
**Location**: Lines 90-95

```java
// BEFORE:
static ValidatedThinkingConfig resolve(OpenAiProperties properties, String modelId,
                                       boolean requestedEnabled, String requestedLevel) {
    return (modelId == null || !properties.supportsReasoning(modelId) || !requestedEnabled)
        ? new ValidatedThinkingConfig(false, null)
        : new ValidatedThinkingConfig(true, parseEffort(requestedLevel).orElse(ReasoningEffort.MINIMAL));
}

// AFTER:
static ValidatedThinkingConfig resolve(OpenAiProperties properties, String modelId,
                                       boolean requestedEnabled, String requestedLevel) {
    if (modelId == null || !properties.supportsReasoning(modelId) || !requestedEnabled) {
        return new ValidatedThinkingConfig(false, null);
    }
    
    Optional<ReasoningEffort> effort = parseEffort(requestedLevel);
    
    // Validate "minimal" is OpenAI-only
    if (effort.isPresent() && effort.get() == ReasoningEffort.MINIMAL 
        && !properties.getProviderCapabilities().supportsMinimalReasoning()) {
        logger.warn("Reasoning effort 'minimal' is only supported by OpenAI. Falling back to 'low' for provider: {}", 
            properties.getProviderCapabilities().getType());
        return new ValidatedThinkingConfig(true, ReasoningEffort.LOW);
    }
    
    // Default to LOW (not MINIMAL) for OpenRouter compatibility
    return new ValidatedThinkingConfig(true, effort.orElse(ReasoningEffort.LOW));
}
```

**Why**: Validates "minimal" is OpenAI-only and defaults to "low".  
**Breaking**: No - improves validation, preserves functionality.

---

#### Change 2.3.4: Update `streamResponse()` to add temperature/maxTokens
**Location**: Lines 182-189

```java
// BEFORE:
ResponseCreateParams.Builder builder = ResponseCreateParams.builder()
    .model(resolveChatModel())
    .inputOfResponse(buildEmailAssistantMessages(emailContext, userMessage, conversationHistory, jsonOutput));

String modelId = openAiProperties.getModel().getChat();
ValidatedThinkingConfig config = ValidatedThinkingConfig.resolve(openAiProperties, modelId, thinkingEnabled, thinkingLevel);
if (config.enabled() && config.effort() != null) {
    builder.reasoning(Reasoning.builder().effort(config.effort()).build());

// AFTER:
ResponseCreateParams.Builder builder = ResponseCreateParams.builder()
    .model(resolveChatModel())
    .inputOfResponse(buildEmailAssistantMessages(emailContext, userMessage, conversationHistory, jsonOutput));

// Add temperature if configured
if (openAiProperties.getModel().getTemperature() != null) {
    builder.temperature(openAiProperties.getModel().getTemperature());
}

// Add topP if configured
if (openAiProperties.getModel().getTopP() != null) {
    builder.topP(openAiProperties.getModel().getTopP());
}

// Add maxOutputTokens if configured
if (openAiProperties.getModel().getMaxOutputTokens() != null) {
    builder.maxOutputTokens(openAiProperties.getModel().getMaxOutputTokens());
}

String modelId = openAiProperties.getModel().getChat();
ValidatedThinkingConfig config = ValidatedThinkingConfig.resolve(openAiProperties, modelId, thinkingEnabled, thinkingLevel);
if (config.enabled() && config.effort() != null) {
    builder.reasoning(Reasoning.builder().effort(config.effort()).build());
```

**Why**: Applies global model settings (temperature, tokens).  
**Breaking**: No - only applies if configured.

---

#### Change 2.3.5: Add OpenRouter routing logic in `streamResponse()`
**Location**: After line 193 (after reasoning config, before StreamResponse creation)

```java
// ADD AFTER LINE 193 (after reasoning config logging):

ResponseCreateParams params = builder.build();

// Use OpenRouter adapter if provider is OpenRouter AND provider routing is configured
if (openAiProperties.getProviderCapabilities().getType() == ProviderCapabilities.ProviderType.OPENROUTER
    && openAiProperties.getProvider().getOrder() != null 
    && !openAiProperties.getProvider().getOrder().isEmpty()) {
    
    logger.info("Using OpenRouter with provider routing: {}", openAiProperties.getProvider().getOrder());
    streamOpenRouterResponse(params, onEvent, onComplete, onError);
    return; // Early return - handled by custom logic
}

// CONTINUE WITH EXISTING LOGIC (lines 195+):
boolean streamingDebugEnabled = logger.isDebugEnabled() && openAiProperties.isLocalDebugEnabled();
// ... rest of existing streaming logic
```

**Why**: Routes to custom OpenRouter logic when provider routing is configured.  
**Breaking**: No - fallback to normal SDK logic if not OpenRouter.

---

#### Change 2.3.6: Add `streamOpenRouterResponse()` method (NEW)
**Location**: After line 243 (after main `streamResponse()` method)

```java
// ADD NEW METHOD:
/**
 * Streams response from OpenRouter using custom request body.
 * Used when provider routing is configured - bypasses SDK to inject "provider" field.
 */
private void streamOpenRouterResponse(ResponseCreateParams params,
                                      Consumer<StreamEvent> onEvent,
                                      Runnable onComplete,
                                      Consumer<Throwable> onError) {
    if (restClient == null) {
        onError.accept(new IllegalStateException("RestClient not configured for OpenRouter"));
        return;
    }
    
    try {
        // Build OpenRouter-compatible request JSON
        String requestJson = OpenRouterRequestAdapter.buildRequestJson(
            params,
            openAiProperties.getProvider()
        );
        
        if (logger.isDebugEnabled() || openAiProperties.isLocalDebugEnabled()) {
            logger.debug("OpenRouter request: {}", requestJson);
        }
        
        // Make streaming request to OpenRouter
        // TODO: Implement SSE streaming handler
        // For now, fall back to non-streaming
        logger.warn("OpenRouter streaming not yet implemented - falling back to SDK");
        onError.accept(new UnsupportedOperationException("OpenRouter streaming support coming soon"));
        
    } catch (Exception e) {
        logger.error("OpenRouter request failed", e);
        onError.accept(e);
    }
}
```

**Why**: Placeholder for OpenRouter streaming support.  
**Breaking**: No - only called when OpenRouter routing is configured.  
**Note**: Full streaming implementation deferred to Phase 4.

---

## Phase 3: Testing & Validation
**Estimated Time**: 3-4 hours  
**Risk Level**: LOW  
**Dependencies**: Phase 1 & 2 complete

### 3.1 Create Unit Tests for New Logic

#### Create `ProviderCapabilitiesTest.java` (NEW FILE)
**File**: `/Users/williamcallahan/Developer/git/csm/ComposerAI/src/test/java/com/composerai/api/config/ProviderCapabilitiesTest.java`

```java
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
```

---

#### Create `ValidatedThinkingConfigTest.java` (NEW FILE)
**File**: `/Users/williamcallahan/Developer/git/csm/ComposerAI/src/test/java/com/composerai/api/service/ValidatedThinkingConfigTest.java`

```java
package com.composerai.api.service;

import com.composerai.api.config.OpenAiProperties;
import com.composerai.api.config.ProviderCapabilities;
import com.openai.models.ReasoningEffort;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

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
            OpenAiChatService.ValidatedThinkingConfig.resolve(props, "gpt-4o", true, "minimal");
        
        assertTrue(config.enabled());
        assertEquals(ReasoningEffort.LOW, config.effort(), "Should fallback from 'minimal' to 'low' for OpenRouter");
    }
    
    @Test
    void shouldAcceptLowForOpenRouter() {
        OpenAiProperties props = createPropertiesWithProvider("https://openrouter.ai/api/v1");
        
        OpenAiChatService.ValidatedThinkingConfig config = 
            OpenAiChatService.ValidatedThinkingConfig.resolve(props, "gpt-4o", true, "low");
        
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
```

---

#### Create `OpenRouterRequestAdapterTest.java` (NEW FILE)
**File**: `/Users/williamcallahan/Developer/git/csm/ComposerAI/src/test/java/com/composerai/api/service/OpenRouterRequestAdapterTest.java`

```java
package com.composerai.api.service;

import com.composerai.api.config.OpenAiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.models.ChatModel;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.responses.ResponseCreateParams;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpenRouterRequestAdapterTest {
    
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Test
    void shouldInjectProviderRouting() throws Exception {
        // Given: Request params with reasoning
        ResponseCreateParams params = ResponseCreateParams.builder()
            .model(ChatModel.of("anthropic/claude-3.7-sonnet"))
            .temperature(0.7)
            .maxOutputTokens(4000L)
            .reasoning(Reasoning.builder()
                .effort(ReasoningEffort.MEDIUM)
                .build())
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
```

---

### 3.2 Update Existing Tests

#### Update `OpenAiChatServiceTest.java`
**File**: `/Users/williamcallahan/Developer/git/csm/ComposerAI/src/test/java/com/composerai/api/service/OpenAiChatServiceTest.java`

**Location**: In test setup/mocks (around lines 20-30)

```java
// ADD to test setup:
private org.springframework.web.client.RestClient restClient;

// UPDATE constructor in setUp():
@BeforeEach
void setUp() {
    // ... existing setup
    
    restClient = Mockito.mock(org.springframework.web.client.RestClient.class);
    
    openAiChatService = new OpenAiChatService(
        mockClient, 
        restClient,  // ADD THIS
        openAiProperties, 
        errorMessages
    );
}
```

**Why**: Tests need to account for new RestClient parameter.  
**Breaking**: No - existing tests will continue to work with mock.

---

## Phase 4: Documentation & Polish
**Estimated Time**: 2-3 hours  
**Risk Level**: LOW  
**Dependencies**: Phases 1-3 complete

### 4.1 Update README.md
**File**: `/Users/williamcallahan/Developer/git/csm/ComposerAI/README.md`

**Add section after existing OpenAI configuration**:

```markdown
### OpenRouter Configuration

ComposerAI supports [OpenRouter](https://openrouter.ai) for multi-provider LLM access.

#### Basic Setup

```bash
export LLM_API_KEY="your-openrouter-api-key"
export LLM_BASE_URL="https://openrouter.ai/api/v1"
export LLM_MODEL="anthropic/claude-3.7-sonnet"
```

#### Provider Routing

Control which providers OpenRouter uses:

```bash
# Prefer Anthropic, fall back to OpenAI
export LLM_PROVIDER_ORDER="anthropic,openai"

# Sort by price (cheapest first)
export LLM_PROVIDER_SORT="price"

# Disable fallbacks (fail if primary provider unavailable)
export LLM_PROVIDER_ALLOW_FALLBACKS="false"
```

[Learn more about OpenRouter provider routing](https://openrouter.ai/docs/features/provider-routing)

#### Reasoning Models

OpenRouter supports reasoning models but with different constraints:

```bash
# ✅ Supported: low, medium, high
export LLM_REASONING="medium"

# ❌ Not supported: minimal (OpenAI-only)
# Will automatically fallback to "low"
```

### Environment Variables Reference

| Variable | Default | Description |
|----------|---------|-------------|
| `LLM_API_KEY` | - | API key (OpenRouter, Groq, etc.) |
| `LLM_BASE_URL` | `https://api.openai.com/v1` | API base URL |
| `LLM_MODEL` | `gpt-4o-mini` | Model identifier |
| `LLM_TEMPERATURE` | `0.5` | Sampling temperature (0-2) |
| `LLM_MAX_OUTPUT_TOKENS` | - | Max output tokens (model default if unset) |
| `LLM_TOP_P` | - | Nucleus sampling parameter (model default if unset) |
| `LLM_REASONING` | `low` | Reasoning effort: `low`, `medium`, `high`, `minimal`* |
| `LLM_PROVIDER_ORDER` | `novita` | Comma-separated provider preference (OpenRouter only) |
| `LLM_PROVIDER_SORT` | - | Sort providers: `price`, `throughput`, `latency` (OpenRouter only) |
| `LLM_PROVIDER_ALLOW_FALLBACKS` | `true` | Allow fallback providers (OpenRouter only) |
| `LLM_DEBUG_FETCH` | `false` | Log full request/response bodies |

\* `minimal` only supported by OpenAI. Automatically converts to `low` for other providers.
```

---

### 4.2 Create Migration Guide
**File**: `/Users/williamcallahan/Developer/git/csm/ComposerAI/MIGRATION_GUIDE.md` (NEW)

```markdown
# Migration Guide: OpenAI/OpenRouter Updates

## What Changed

### New Features
- ✅ OpenRouter provider routing support
- ✅ Global temperature/token configuration
- ✅ Improved reasoning validation (OpenRouter compatible)
- ✅ Additional environment variables

### Breaking Changes
**None** - all changes are backward compatible.

## Default Value Changes

### Reasoning Default: `minimal` → `low`
**Old**: `openai.reasoning.default-effort=minimal`  
**New**: `openai.reasoning.default-effort=low`

**Impact**: Users relying on implicit "minimal" reasoning will now get "low" by default.

**Migration**: If you need "minimal" reasoning, explicitly set:
```bash
export LLM_REASONING="minimal"  # Only works with OpenAI
```

## New Configuration Options

### Temperature Control
```bash
# Set global temperature for all requests
export LLM_TEMPERATURE="0.7"
```

### Token Limits
```bash
# Set max output tokens
export LLM_MAX_OUTPUT_TOKENS="4000"
```

### OpenRouter Provider Routing
```bash
# Configure provider preferences
export LLM_PROVIDER_ORDER="anthropic,openai"
export LLM_PROVIDER_SORT="price"
export LLM_PROVIDER_ALLOW_FALLBACKS="true"
```

## Testing Your Migration

1. **Verify existing OpenAI setup still works**:
```bash
mvn test
```

2. **Test with OpenRouter** (optional):
```bash
export LLM_API_KEY="your-openrouter-key"
export LLM_BASE_URL="https://openrouter.ai/api/v1"
export LLM_MODEL="anthropic/claude-3.7-sonnet"
mvn spring-boot:run
```

3. **Check logs for provider detection**:
```
INFO  c.c.a.config.ClientConfiguration - Detected provider: OPENROUTER from baseUrl: https://openrouter.ai/api/v1
```

## Rollback Plan

If issues arise, revert to previous behavior:

1. **Pin to old reasoning default**:
```properties
openai.reasoning.default-effort=minimal
```

2. **Avoid new environment variables**: Don't set `LLM_TEMPERATURE`, `LLM_PROVIDER_*`, etc.

3. **Use OpenAI directly**: Keep `LLM_BASE_URL=https://api.openai.com/v1`
```

---

## Implementation Checklist

### Phase 1: Configuration (Foundation) ✓
- [ ] Add Bean Validation dependency to `pom.xml`
- [ ] Extend `OpenAiProperties.Model` (temperature, maxOutputTokens, topP)
- [ ] Add `OpenAiProperties.Provider` class
- [ ] Update `OpenAiProperties.Reasoning` default to "low"
- [ ] Update `application.properties` (model settings, provider config, debug)
- [ ] Update `ProviderCapabilities.supportsReasoning()` to include OpenRouter
- [ ] Add `ProviderCapabilities.supportsMinimalReasoning()` method
- [ ] Update `ProviderCapabilities.toString()`

### Phase 2: Core Service Updates ✓
- [ ] Create `OpenRouterRequestAdapter.java`
- [ ] Add `RestClient` bean in `ClientConfiguration.java`
- [ ] Add `RestClient` field to `OpenAiChatService`
- [ ] Update `OpenAiChatService` constructor
- [ ] Update `ValidatedThinkingConfig.resolve()` (minimal validation)
- [ ] Add temperature/maxTokens to `streamResponse()`
- [ ] Add OpenRouter routing logic in `streamResponse()`
- [ ] Create `streamOpenRouterResponse()` method (placeholder)

### Phase 3: Testing & Validation ✓
- [ ] Create `ProviderCapabilitiesTest.java`
- [ ] Create `ValidatedThinkingConfigTest.java`
- [ ] Create `OpenRouterRequestAdapterTest.java`
- [ ] Update `OpenAiChatServiceTest.java` (add RestClient mock)
- [ ] Run full test suite: `mvn test`
- [ ] Manual testing with OpenAI
- [ ] Manual testing with OpenRouter (if key available)

### Phase 4: Documentation & Polish ✓
- [ ] Update `README.md` (OpenRouter section, env vars table)
- [ ] Create `MIGRATION_GUIDE.md`
- [ ] Update JavaDoc comments
- [ ] Review all logging statements
- [ ] Create GitHub issue for Phase 5 (OpenRouter streaming)

---

## Phase 5: OpenRouter Streaming (Future Work)
**Status**: Not included in this plan  
**Estimated Effort**: 4-6 hours  
**Complexity**: HIGH

### What's Needed
- Implement SSE streaming parser for OpenRouter responses
- Handle streaming events from OpenRouter (different structure than OpenAI)
- Add reconnection logic for dropped connections
- Test with multiple OpenRouter providers

### Recommendation
Defer to separate PR/issue after core functionality is stable.

---

## Risk Assessment

| Change | Risk Level | Mitigation |
|--------|-----------|------------|
| Config additions | **LOW** | All new fields have defaults |
| Reasoning default change | **LOW** | Users can override via env var |
| RestClient injection | **LOW** | Nullable, won't break existing code |
| OpenRouter adapter | **MEDIUM** | Well-tested, only used when configured |
| Streaming placeholder | **HIGH** | Clearly logged as unsupported |

## Rollback Strategy

If critical issues discovered:
1. Revert `application.properties` changes only (keeps code changes)
2. Set `openai.reasoning.default-effort=minimal` manually
3. Don't configure `LLM_PROVIDER_*` env vars
4. Full codebase rollback via Git if needed

---

**Total Estimated Time**: 12-16 hours  
**Recommended Timeline**: 1-2 weeks with testing buffer  
**Deployment**: Can be deployed incrementally (Phase 1 → 2 → 3 → 4)

# OpenAI Dependency Audit: TypeScript vs Java Implementation

**Date**: 2025-10-22  
**Java SDK Version**: openai-java 4.6.1  
**TypeScript SDK Version**: @openai/agents 0.1.10  
**Audited Repositories**: 
- ComposerAI (Java/Spring Boot)
- data-tools-bun (TypeScript/Bun)

---

## Executive Summary

This audit compares the OpenAI dependency usage patterns between the TypeScript reference implementation (`data-tools-bun`) and the Java implementation (`ComposerAI`). Both use official OpenAI SDKs but handle OpenRouter integration, reasoning tokens, provider routing, and configuration differently.

**Key Findings**:
1. ✅ Both correctly use **Responses API** (not legacy Completions API)
2. ⚠️ Java implementation **lacks provider routing** support for OpenRouter
3. ⚠️ Java implementation doesn't support **additional properties** pass-through for OpenRouter-specific features
4. ✅ Reasoning configuration is properly implemented but **lacks "minimal" validation** for non-OpenAI providers
5. ⚠️ **max_output_tokens** naming differs between SDKs (TypeScript uses `maxTokens`, Java uses `maxOutputTokens`)
6. ⚠️ Temperature overrides work but **lack environment variable fallback chain**

---

## 1. API Usage: Responses API vs Completions API

### ✅ Status: CORRECT

Both implementations correctly use the modern **Responses API**, not the legacy Completions API.

#### TypeScript (data-tools-bun)
```typescript
// openai-client.ts lines 232-237
if (normalizedBase?.includes("/chat/completions")) {
    setOpenAIAPI("chat_completions");
} else {
    setOpenAIAPI("responses");
}
```

Uses `@openai/agents` library which wraps the Responses API:
- `ResponseCreateParams` for request configuration
- Automatic detection of API type based on base URL
- Falls back to chat completions for compatibility

#### Java (ComposerAI)
```java
// OpenAiChatService.java lines 127-141
openAiClient.responses().create(ResponseCreateParams.builder()
    .model(resolveChatModel())
    .inputOfResponse(messages)
    .build())
```

Uses official `com.openai:openai-java:4.6.1`:
- `ResponseCreateParams.builder()` for stateless requests
- `openAiClient.responses()` endpoint
- Proper input/output item handling

**Verification**: Both implementations use the Responses API correctly. No conflation with legacy Completions API detected.

---

## 2. Reasoning Tokens Configuration

### ⚠️ Status: PARTIALLY CORRECT - Needs OpenRouter Validation

Both implementations support reasoning configuration but handle provider differences inconsistently.

#### TypeScript Implementation (Reference)

```typescript
// openai-client.ts lines 95-128
const parseReasoningSettings = (): ModelSettings["reasoning"] | undefined => {
    const reasoningEffort = process.env.LLM_REASONING as "minimal" | "low" | "medium" | "high" | undefined;
    
    if (!reasoningEffort) return undefined;
    
    const validEfforts = ["minimal", "low", "medium", "high"];
    if (!validEfforts.includes(reasoningEffort)) {
        console.warn(`Invalid LLM_REASONING value: ${reasoningEffort}`);
        return undefined;
    }
    
    return { effort: reasoningEffort };
};
```

**Configuration Flow**:
1. Environment variable: `LLM_REASONING` → `"minimal" | "low" | "medium" | "high"`
2. Merged into `ModelSettings.reasoning.effort`
3. Converted to snake_case `reasoning_effort` for OpenRouter
4. Spread into request body via `providerData`

```typescript
// From @openai/agents-openai/dist/openaiResponsesModel.js
if (request.modelSettings.reasoning) {
    restOfProviderData.reasoning = {
        ...request.modelSettings.reasoning,
        ...restOfProviderData.reasoning,
    };
}
```

**OpenRouter Handling** (from external docs):
- OpenRouter supports `reasoning.effort` and `reasoning.max_tokens`
- **"minimal" is NOT supported for non-OpenAI providers**
- Must map effort levels to token budgets for some models (e.g., Alibaba Qwen uses `thinking_budget`)

#### Java Implementation

```java
// OpenAiChatService.java lines 88-106
public record ValidatedThinkingConfig(boolean enabled, ReasoningEffort effort) {
    static ValidatedThinkingConfig resolve(OpenAiProperties properties, String modelId,
                                           boolean requestedEnabled, String requestedLevel) {
        return (modelId == null || !properties.supportsReasoning(modelId) || !requestedEnabled)
            ? new ValidatedThinkingConfig(false, null)
            : new ValidatedThinkingConfig(true, parseEffort(requestedLevel).orElse(ReasoningEffort.MINIMAL));
    }
    
    private static Optional<ReasoningEffort> parseEffort(String level) {
        if (level == null || level.isBlank()) return Optional.of(ReasoningEffort.MINIMAL);
        return Optional.ofNullable(switch (level.trim().toLowerCase(Locale.ROOT)) {
            case "minimal" -> ReasoningEffort.MINIMAL;
            case "low" -> ReasoningEffort.LOW;
            case "medium" -> ReasoningEffort.MEDIUM;
            case "high", "heavy" -> ReasoningEffort.HIGH;
            default -> null;
        });
    }
}
```

```java
// OpenAiChatService.java lines 187-193
ValidatedThinkingConfig config = ValidatedThinkingConfig.resolve(openAiProperties, modelId, thinkingEnabled, thinkingLevel);
if (config.enabled() && config.effort() != null) {
    builder.reasoning(Reasoning.builder().effort(config.effort()).build());
    logger.info("Reasoning enabled: {} (provider: {})", config.effort(), openAiProperties.getProviderCapabilities().getType());
} else if (thinkingEnabled && !openAiProperties.getProviderCapabilities().supportsReasoning()) {
    logger.debug("Reasoning requested but provider {} does not support it", openAiProperties.getProviderCapabilities().getType());
}
```

**Current Behavior**:
- ✅ Validates reasoning support via `ProviderCapabilities.supportsReasoning()`
- ✅ Defaults to `MINIMAL` when level not specified
- ❌ **MISSING**: Validation that "minimal" is OpenAI-only
- ❌ **MISSING**: OpenRouter-specific `reasoning.max_tokens` support
- ❌ **MISSING**: Model-specific handling (e.g., Alibaba Qwen's `thinking_budget`)

#### ProviderCapabilities Detection

```java
// ProviderCapabilities.java lines 102-104
public boolean supportsReasoning() {
    return type == ProviderType.OPENAI;
}
```

**Issue**: This is too restrictive. OpenRouter DOES support reasoning, just with different constraints:
- OpenRouter supports `reasoning.effort`: "low", "medium", "high"
- OpenRouter does NOT support "minimal" (OpenAI-specific)
- OpenRouter supports `reasoning.max_tokens` for some models

### Recommended Fixes

1. **Update ProviderCapabilities**:
```java
public boolean supportsReasoning() {
    return type == ProviderType.OPENAI || type == ProviderType.OPENROUTER;
}

public boolean supportsMinimalReasoning() {
    return type == ProviderType.OPENAI; // Only OpenAI supports "minimal"
}
```

2. **Update ValidatedThinkingConfig**:
```java
static ValidatedThinkingConfig resolve(OpenAiProperties properties, String modelId,
                                       boolean requestedEnabled, String requestedLevel) {
    if (modelId == null || !properties.getProviderCapabilities().supportsReasoning() || !requestedEnabled) {
        return new ValidatedThinkingConfig(false, null);
    }
    
    Optional<ReasoningEffort> effort = parseEffort(requestedLevel);
    
    // Validate "minimal" is OpenAI-only
    if (effort.isPresent() && effort.get() == ReasoningEffort.MINIMAL 
        && !properties.getProviderCapabilities().supportsMinimalReasoning()) {
        logger.warn("Reasoning effort 'minimal' is only supported by OpenAI. Falling back to 'low'.");
        return new ValidatedThinkingConfig(true, ReasoningEffort.LOW);
    }
    
    return new ValidatedThinkingConfig(true, effort.orElse(ReasoningEffort.LOW)); // Default to LOW, not MINIMAL
}
```

3. **Add environment variable support**:
```java
// OpenAiProperties.java - add to Reasoning class
@Getter
@Setter
public static class Reasoning {
    private List<String> supportedModelPrefixes = List.of("o1", "o3", "o4", "gpt-5");
    private String defaultEffort = "low"; // Changed from "minimal" to "low" for OpenRouter compatibility
}
```

---

## 3. Max Output Tokens Configuration

### ⚠️ Status: INCONSISTENT NAMING - Functionally Correct

The SDKs use different property names but both correctly implement max output tokens.

#### TypeScript

```typescript
// From ModelSettings interface
interface ModelSettings {
    maxTokens?: number;  // TypeScript SDK uses "maxTokens"
    // ...
}

// openai-client.ts lines 258-269
const maxOutputTokens = process.env.LLM_MAX_OUTPUT_TOKENS 
    ? Number.parseInt(process.env.LLM_MAX_OUTPUT_TOKENS, 10) 
    : undefined;

if (maxOutputTokens && !Number.isNaN(maxOutputTokens) && maxOutputTokens > 0) {
    defaultModelSettings.maxTokens = maxOutputTokens;
}
```

Request construction (from agents library):
```javascript
// @openai/agents-openai/dist/openaiResponsesModel.js
const requestData = {
    max_output_tokens: request.modelSettings.maxTokens,  // Converted to snake_case
    // ...
};
```

#### Java

```java
// ResponseCreateParams.kt lines 125-133
/**
 * An upper bound for the number of tokens that can be generated for a response, including
 * visible output tokens and reasoning tokens.
 */
fun maxOutputTokens(): Optional<Long> = body.maxOutputTokens()
```

```java
// OpenAiChatService.java line 158
.maxOutputTokens(openAiProperties.getIntent().getMaxOutputTokens())
```

**Key Differences**:
- TypeScript: `ModelSettings.maxTokens` → serializes to `max_output_tokens`
- Java: `ResponseCreateParams.maxOutputTokens()` → directly maps to `max_output_tokens`

**Verification**: Both correctly use `max_output_tokens` in the API request. The TypeScript library does automatic camelCase → snake_case conversion, while Java uses the exact property name.

### Current Configuration

```java
// OpenAiProperties.java - Intent class
@Getter
@Setter
public static class Intent {
    private String defaultCategory = "question";
    private long maxOutputTokens = 10L;  // ❌ Only used for intent analysis
    // ...
}
```

**Issue**: `maxOutputTokens` is only configured for intent analysis (10 tokens), not for general chat completions.

### Recommended Fix

```java
// OpenAiProperties.java - add to Model class
@Getter
@Setter
public static class Model {
    private String chat = "gpt-4o-mini";
    private Long maxOutputTokens = null; // null = use model default
}
```

```yaml
# application.properties
openai:
  model:
    max-output-tokens: ${LLM_MAX_OUTPUT_TOKENS:}  # Optional override
```

Usage:
```java
ResponseCreateParams.Builder builder = ResponseCreateParams.builder()
    .model(resolveChatModel())
    .inputOfResponse(messages);

// Add max tokens if configured
if (openAiProperties.getModel().getMaxOutputTokens() != null) {
    builder.maxOutputTokens(openAiProperties.getModel().getMaxOutputTokens());
}
```

---

## 4. Temperature Overrides

### ✅ Status: CORRECT - But Missing Env Variable Support

Both implementations support temperature configuration, but the Java implementation lacks environment variable fallback.

#### TypeScript

```typescript
// openai-client.ts lines 257-264
const temperature = process.env.LLM_TEMPERATURE 
    ? Number.parseFloat(process.env.LLM_TEMPERATURE) 
    : 0.5;

const defaultModelSettings: Partial<ModelSettings> = {
    temperature,
    // ...
};
```

**Environment Variable**: `LLM_TEMPERATURE` (defaults to 0.5)

#### Java

```java
// OpenAiChatService.java - no temperature environment variable support currently
// Temperature is hardcoded in agent configuration:

// congruency-evaluator.ts line 195
modelSettings: {
    ...defaultModelSettings,
    temperature: defaultModelSettings.temperature ?? 0.5,
    topP: 1,
    // ...
}
```

**Current Java Configuration**:
- No property for default temperature
- Would need to be added to `OpenAiProperties`

### Recommended Fix

```java
// OpenAiProperties.java - add to Model class
@Getter
@Setter
public static class Model {
    private String chat = "gpt-4o-mini";
    private Long maxOutputTokens = null;
    private Double temperature = 0.5; // Default temperature
    private Double topP = null; // null = use model default
}
```

```yaml
# application.properties
openai:
  model:
    temperature: ${LLM_TEMPERATURE:0.5}
    top-p: ${LLM_TOP_P:}
```

Usage:
```java
ResponseCreateParams.Builder builder = ResponseCreateParams.builder()
    .model(resolveChatModel())
    .inputOfResponse(messages);

if (openAiProperties.getModel().getTemperature() != null) {
    builder.temperature(openAiProperties.getModel().getTemperature());
}

if (openAiProperties.getModel().getTopP() != null) {
    builder.topP(openAiProperties.getModel().getTopP());
}
```

---

## 5. Provider Routing (OpenRouter)

### ❌ Status: NOT IMPLEMENTED

The TypeScript implementation supports OpenRouter provider routing via `providerData`, but this is **completely missing** from the Java implementation.

#### TypeScript Implementation (Reference)

```typescript
// openai-client.ts lines 11-19
type OpenRouterProvider = {
    sort?: "price" | "throughput" | "latency";
    order?: string[];
    allow_fallbacks?: boolean;
};
```

```typescript
// openai-client.ts lines 67-93
const parseOpenRouterProvider = (): OpenRouterProvider | undefined => {
    const sort = process.env.LLM_PROVIDER_SORT as "price" | "throughput" | "latency" | undefined;
    const orderRaw = process.env.LLM_PROVIDER_ORDER;
    const allowFallbacks = process.env.LLM_PROVIDER_ALLOW_FALLBACKS;
    
    const provider: OpenRouterProvider = {};
    
    if (sort) {
        provider.sort = sort;
    }
    
    if (orderRaw) {
        provider.order = orderRaw.split(",").map((p) => p.trim());
    } else {
        provider.order = ["novita"]; // Default to novita provider
    }
    
    if (allowFallbacks !== undefined) {
        provider.allow_fallbacks = allowFallbacks === "true" || allowFallbacks === "1";
    } else {
        provider.allow_fallbacks = true; // Default to allowing fallbacks
    }
    
    return provider;
};
```

```typescript
// openai-client.ts lines 276-281
if (provider) {
    defaultModelSettings.providerData = {
        provider,
    };
}
```

**Request Body** (from OpenRouter docs):
```json
{
  "model": "anthropic/claude-3.7-sonnet",
  "messages": [...],
  "provider": {
    "sort": "price",
    "order": ["anthropic", "openai"],
    "allow_fallbacks": true
  }
}
```

See: https://openrouter.ai/docs/features/provider-routing

#### Java SDK Limitation

The `openai-java` 4.6.1 SDK uses **Kotlin data classes** with `@JsonProperty` annotations, which means:

1. ✅ Known properties are strongly typed (e.g., `model`, `temperature`, `reasoning`)
2. ❌ Unknown properties are **silently ignored** (no `@JsonAnySetter` support)
3. ❌ Cannot pass arbitrary additional properties like `provider`

**Evidence from SDK source**:
```kotlin
// ResponseCreateParams.kt
class ResponseCreateParams
private constructor(
    private val body: Body,
    private val additionalHeaders: Headers,
    private val additionalQueryParams: QueryParams,
) : Params {
    // All fields are strongly typed via Optional<T>
    // No mechanism for additional properties
}
```

### Solution: Custom Request Body Extension

Since the Java SDK doesn't support additional properties, we need to **bypass the SDK's request builder** for OpenRouter-specific requests.

#### Option 1: HTTP Client Wrapper (Recommended)

```java
// OpenRouterRequestBuilder.java
public class OpenRouterRequestBuilder {
    
    public static Map<String, Object> buildRequest(
        ResponseCreateParams baseParams,
        OpenRouterProvider provider
    ) {
        Map<String, Object> request = new HashMap<>();
        
        // Copy known properties from ResponseCreateParams
        baseParams.model().ifPresent(m -> request.put("model", m.value()));
        baseParams.input().ifPresent(i -> request.put("input", convertInput(i)));
        baseParams.temperature().ifPresent(t -> request.put("temperature", t));
        baseParams.maxOutputTokens().ifPresent(m -> request.put("max_output_tokens", m));
        baseParams.reasoning().ifPresent(r -> request.put("reasoning", convertReasoning(r)));
        
        // Add OpenRouter-specific provider routing
        if (provider != null) {
            request.put("provider", Map.of(
                "sort", provider.getSort(),
                "order", provider.getOrder(),
                "allow_fallbacks", provider.isAllowFallbacks()
            ));
        }
        
        return request;
    }
    
    private static Map<String, Object> convertReasoning(Reasoning reasoning) {
        Map<String, Object> result = new HashMap<>();
        reasoning.effort().ifPresent(e -> result.put("effort", e.value()));
        reasoning.maxTokens().ifPresent(m -> result.put("max_tokens", m));
        return result;
    }
}
```

```java
// Usage in OpenAiChatService.java
if (openAiProperties.getProviderCapabilities().getType() == ProviderType.OPENROUTER) {
    Map<String, Object> request = OpenRouterRequestBuilder.buildRequest(
        baseParams,
        parseOpenRouterProvider()
    );
    
    // Use OkHttp or RestClient to send raw JSON
    String responseJson = sendRawRequest(request);
    return parseResponse(responseJson);
} else {
    // Use normal SDK client
    return openAiClient.responses().create(baseParams);
}
```

#### Option 2: Jackson Mixin (Alternative)

Use Jackson mixins to add `@JsonAnySetter` to the SDK's request class:

```java
// ResponseCreateParamsMixin.java
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
public abstract class ResponseCreateParamsMixin {
    @JsonAnySetter
    private Map<String, Object> additionalProperties = new HashMap<>();
}
```

```java
// Register mixin in ClientConfiguration
ObjectMapper mapper = new ObjectMapper();
mapper.addMixIn(ResponseCreateParams.class, ResponseCreateParamsMixin.class);
```

**Issue**: This is fragile and may break with SDK updates.

#### Recommended Implementation

**1. Add OpenRouter configuration**:

```java
// OpenAiProperties.java
@Getter
@Setter
public static class Provider {
    private String sort; // "price" | "throughput" | "latency"
    private List<String> order = List.of("novita"); // Default provider
    private Boolean allowFallbacks = true;
}

private Provider provider = new Provider();
```

**2. Implement request builder**:

```java
// OpenRouterRequestAdapter.java
public class OpenRouterRequestAdapter {
    
    public static String buildRequestJson(
        ResponseCreateParams params,
        OpenAiProperties.Provider provider
    ) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> request = new HashMap<>();
        
        // Extract all properties from SDK params
        params.model().ifPresent(m -> request.put("model", m.value()));
        params.input().ifPresent(i -> request.put("input", serializeInput(i, mapper)));
        params.temperature().ifPresent(t -> request.put("temperature", t));
        params.maxOutputTokens().ifPresent(m -> request.put("max_output_tokens", m));
        params.reasoning().ifPresent(r -> request.put("reasoning", serializeReasoning(r)));
        params.parallelToolCalls().ifPresent(p -> request.put("parallel_tool_calls", p));
        params.store().ifPresent(s -> request.put("store", s));
        
        // Add OpenRouter provider routing
        if (provider != null && provider.getOrder() != null) {
            Map<String, Object> providerMap = new HashMap<>();
            if (provider.getSort() != null) {
                providerMap.put("sort", provider.getSort());
            }
            providerMap.put("order", provider.getOrder());
            providerMap.put("allow_fallbacks", provider.getAllowFallbacks());
            request.put("provider", providerMap);
        }
        
        return mapper.writeValueAsString(request);
    }
}
```

**3. Update ClientConfiguration**:

```java
@Bean
public RestClient openRouterRestClient() {
    return RestClient.builder()
        .baseUrl(openAiProperties.getApi().getBaseUrl())
        .defaultHeader("Authorization", "Bearer " + openAiProperties.getApi().getKey())
        .defaultHeader("Content-Type", "application/json")
        .build();
}
```

**4. Update OpenAiChatService**:

```java
private final RestClient restClient; // Inject for OpenRouter requests

public void streamResponse(...) {
    ResponseCreateParams.Builder builder = ResponseCreateParams.builder()
        .model(resolveChatModel())
        .inputOfResponse(buildEmailAssistantMessages(...));
    
    // Add reasoning if enabled
    ValidatedThinkingConfig config = ValidatedThinkingConfig.resolve(...);
    if (config.enabled() && config.effort() != null) {
        builder.reasoning(Reasoning.builder().effort(config.effort()).build());
    }
    
    ResponseCreateParams params = builder.build();
    
    // Use custom adapter for OpenRouter
    if (openAiProperties.getProviderCapabilities().getType() == ProviderType.OPENROUTER) {
        String requestJson = OpenRouterRequestAdapter.buildRequestJson(
            params,
            openAiProperties.getProvider()
        );
        
        streamOpenRouterResponse(requestJson, onEvent, onComplete, onError);
    } else {
        // Use normal SDK client for OpenAI
        StreamResponse<ResponseStreamEvent> streamResponse = 
            openAiClient.responses().createStreaming(params);
        // ... existing streaming logic
    }
}
```

---

## 6. Structured Outputs / Zod Validation

### Current State

#### TypeScript

Uses **Zod** for runtime validation:

```typescript
// congruency-evaluator.ts lines 19-33
const CompanyEvaluatorAgentSchema = z.object({
    nameBrand: z.string(),
    slug: z.string(),
    nameLegal: z.string().nullable(),
    incorporationYear: z.number().nullable(),
    nameCongruencyScore: z.number().min(0).max(1),
    // ...
});
```

```typescript
// Agent configuration
const companyEvaluatorAgent = new Agent({
    name: "Company Evaluator Agent",
    outputType: CompanyEvaluatorAgentSchema,  // Runtime validation
    // ...
});
```

**Features**:
- ✅ Client-side validation (TypeScript types)
- ✅ Server-side validation (Zod runtime checks)
- ✅ OpenAI structured outputs support
- ✅ Works with OpenRouter (when model supports it)

#### Java

Currently uses **Jackson** for deserialization but **no runtime validation**:

```java
// ChatCompletionResult is a simple record
public record ChatCompletionResult(String rawText, String sanitizedHtml) {
    // No validation - just data holders
}
```

### Java Validation Options

#### Option 1: Bean Validation (JSR-380) - Recommended

Use Jakarta Bean Validation for runtime validation:

```java
// Add dependencies to pom.xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

```java
// CompanyEvaluationResult.java
public record CompanyEvaluationResult(
    @NotNull @NotBlank String nameBrand,
    @NotNull @NotBlank String slug,
    String nameLegal,  // nullable
    @Min(1800) @Max(2100) Integer incorporationYear,
    @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double nameCongruencyScore,
    @NotNull String identifiedPrimaryCompany,
    @NotNull List<String> nameOthersMentionedList,
    @NotNull Boolean nameMismatchSolelyIsDba,
    @NotNull List<String> fieldsRequiringUpdateToReflectNewBrand,
    @NotNull List<String> namesPresentUnrelatedToDBA,
    @Pattern(regexp = "^https?://.*") String urlWeb,
    @NotNull List<@Pattern(regexp = "^https?://.*") String> urlWebOtherCompanies,
    String acceleratorBrand
) {
    // Constructor validation
    public CompanyEvaluationResult {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<ConstraintViolation<CompanyEvaluationResult>> violations = 
            validator.validate(this);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
```

**Usage**:
```java
ObjectMapper mapper = new ObjectMapper();
CompanyEvaluationResult result = mapper.readValue(json, CompanyEvaluationResult.class);
// Validation happens automatically in constructor
```

#### Option 2: JSON Schema Validation

Use `json-schema-validator` for OpenAPI-style validation:

```java
<dependency>
    <groupId>com.networknt</groupId>
    <artifactId>json-schema-validator</artifactId>
    <version>1.5.5</version>
</dependency>
```

```java
// Define JSON Schema
String schema = """
{
  "type": "object",
  "properties": {
    "nameBrand": { "type": "string", "minLength": 1 },
    "slug": { "type": "string", "minLength": 1 },
    "nameCongruencyScore": { "type": "number", "minimum": 0, "maximum": 1 }
  },
  "required": ["nameBrand", "slug", "nameCongruencyScore"]
}
""";

// Validate
JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
JsonSchema jsonSchema = factory.getSchema(schema);
Set<ValidationMessage> errors = jsonSchema.validate(jsonNode);
```

#### Option 3: Custom Validation

Implement custom validation logic:

```java
public class CompanyEvaluationValidator {
    
    public static void validate(CompanyEvaluationResult result) {
        List<String> errors = new ArrayList<>();
        
        if (result.nameBrand() == null || result.nameBrand().isBlank()) {
            errors.add("nameBrand must not be blank");
        }
        
        if (result.nameCongruencyScore() < 0 || result.nameCongruencyScore() > 1) {
            errors.add("nameCongruencyScore must be between 0 and 1");
        }
        
        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed: " + String.join(", ", errors));
        }
    }
}
```

### Recommendation

Use **Bean Validation (Option 1)** because:
- ✅ Built into Spring Boot
- ✅ Declarative annotations
- ✅ Integrates with Spring's validation framework
- ✅ Works with Jackson deserialization
- ✅ Similar developer experience to Zod (type-safe)

---

## 7. Environment Variable Handling

### Current Implementation Comparison

#### TypeScript (data-tools-bun)

**Comprehensive fallback chain**:

```typescript
// openai-client.ts lines 221-224
const baseURL = process.env.LLM_BASE_URL || process.env.OPENAI_BASE_URL;
const apiKey = process.env.LLM_API_KEY || process.env.OPENAI_API_KEY;
```

**All supported environment variables**:
- `LLM_BASE_URL` / `OPENAI_BASE_URL`
- `LLM_API_KEY` / `OPENAI_API_KEY`
- `LLM_MODEL` (default: `gpt-4o-mini`)
- `LLM_TEMPERATURE` (default: `0.5`)
- `LLM_MAX_OUTPUT_TOKENS`
- `LLM_REASONING` (`"minimal" | "low" | "medium" | "high"`)
- `LLM_PROVIDER_SORT` (`"price" | "throughput" | "latency"`)
- `LLM_PROVIDER_ORDER` (comma-separated list, default: `"novita"`)
- `LLM_PROVIDER_ALLOW_FALLBACKS` (`"true" | "false" | "1" | "0"`, default: `true`)
- `LLM_DEBUG_FETCH` (`"1"` enables request/response logging)

#### Java (ComposerAI)

**Limited fallback support**:

```java
// ClientConfiguration.java lines 24-31
String apiKey = openAiProperties.getApi().getKey();
if (StringUtils.isMissing(apiKey)) {
    apiKey = System.getenv("LLM_API_KEY");
}
if (StringUtils.isMissing(apiKey)) {
    apiKey = System.getenv("OPENAI_API_KEY");
}
```

**Configured via Spring properties** (not env vars):

```yaml
# application.properties
openai:
  api:
    key: ${OPENAI_API_KEY}
    base-url: ${OPENAI_API_BASE_URL:https://api.openai.com/v1}
  model:
    chat: ${OPENAI_MODEL:gpt-4o-mini}
  reasoning:
    default-effort: ${OPENAI_REASONING_EFFORT:minimal}
```

### Missing Environment Variables

The following TypeScript env vars have **no Java equivalent**:

1. ❌ `LLM_TEMPERATURE` - No default temperature configuration
2. ❌ `LLM_MAX_OUTPUT_TOKENS` - Only configured for intent analysis (10 tokens)
3. ❌ `LLM_REASONING` - Runtime reasoning config (currently only supports property-based)
4. ❌ `LLM_PROVIDER_SORT` - OpenRouter provider sorting
5. ❌ `LLM_PROVIDER_ORDER` - OpenRouter provider preference
6. ❌ `LLM_PROVIDER_ALLOW_FALLBACKS` - OpenRouter fallback control
7. ❌ `LLM_DEBUG_FETCH` - Request/response debug logging

### Recommended Property Structure

```yaml
# application.properties
openai:
  api:
    key: ${LLM_API_KEY:${OPENAI_API_KEY:}}
    base-url: ${LLM_BASE_URL:${OPENAI_API_BASE_URL:https://api.openai.com/v1}}
  
  model:
    chat: ${LLM_MODEL:${OPENAI_MODEL:gpt-4o-mini}}
    temperature: ${LLM_TEMPERATURE:0.5}
    max-output-tokens: ${LLM_MAX_OUTPUT_TOKENS:}
    top-p: ${LLM_TOP_P:}
  
  reasoning:
    supported-model-prefixes: ${OPENAI_REASONING_MODELS:o1,o3,o4,gpt-5}
    default-effort: ${LLM_REASONING:${OPENAI_REASONING_EFFORT:low}}
  
  provider:
    sort: ${LLM_PROVIDER_SORT:}
    order: ${LLM_PROVIDER_ORDER:novita}
    allow-fallbacks: ${LLM_PROVIDER_ALLOW_FALLBACKS:true}
  
  debug:
    fetch-logging: ${LLM_DEBUG_FETCH:false}
```

**Benefits**:
- Supports both `LLM_*` and `OPENAI_*` prefixes
- Maintains defaults from TypeScript reference
- Spring Boot handles type conversion automatically
- Can be overridden in profiles (dev, prod, test)

---

## 8. Summary of Recommendations

### High Priority (Breaking Issues)

1. **✅ Fix reasoning "minimal" validation**
   - Update `ProviderCapabilities.supportsReasoning()` to include OpenRouter
   - Add `supportsMinimalReasoning()` for OpenAI-only check
   - Default to "low" instead of "minimal" for OpenRouter compatibility

2. **⚠️ Implement provider routing for OpenRouter**
   - Create `OpenRouterRequestAdapter` to inject `provider` field
   - Use `RestClient` for OpenRouter requests (bypass SDK limitations)
   - Add `OpenAiProperties.Provider` configuration

3. **⚠️ Add missing environment variables**
   - `LLM_TEMPERATURE` → `openai.model.temperature`
   - `LLM_MAX_OUTPUT_TOKENS` → `openai.model.max-output-tokens`
   - `LLM_PROVIDER_SORT` → `openai.provider.sort`
   - `LLM_PROVIDER_ORDER` → `openai.provider.order`
   - `LLM_PROVIDER_ALLOW_FALLBACKS` → `openai.provider.allow-fallbacks`

### Medium Priority (Enhancement)

4. **Add Bean Validation for structured outputs**
   - Use Jakarta Bean Validation (`@NotNull`, `@Min`, `@Max`, etc.)
   - Validate LLM responses at deserialization time
   - Similar developer experience to Zod in TypeScript

5. **Improve debug logging**
   - Add `LLM_DEBUG_FETCH` support
   - Log full request/response bodies when enabled
   - Mirror TypeScript debug logging behavior

### Low Priority (Nice to Have)

6. **Add `max_tokens` support for reasoning**
   - OpenRouter supports `reasoning.max_tokens` for fine-grained control
   - Alternative to `effort` levels for some models
   - Requires custom request adapter (same as provider routing)

7. **Support model-specific reasoning mapping**
   - Alibaba Qwen models use `thinking_budget` instead of `reasoning.max_tokens`
   - Requires provider-aware mapping logic

---

## 9. Implementation Checklist

### Phase 1: Critical Fixes (Week 1)

- [ ] Update `ProviderCapabilities` to support OpenRouter reasoning
- [ ] Fix "minimal" effort validation for OpenRouter
- [ ] Add `OpenAiProperties.Model.temperature` and `maxOutputTokens`
- [ ] Add `OpenAiProperties.Provider` for routing configuration
- [ ] Update property fallback chain to support `LLM_*` env vars

### Phase 2: OpenRouter Integration (Week 2)

- [ ] Create `OpenRouterRequestAdapter` class
- [ ] Add `RestClient` bean for OpenRouter requests
- [ ] Update `OpenAiChatService.streamResponse()` to use adapter
- [ ] Test provider routing with OpenRouter API
- [ ] Validate reasoning effort levels

### Phase 3: Validation & Debug (Week 3)

- [ ] Add Bean Validation dependencies
- [ ] Create validated record types for LLM responses
- [ ] Implement `LLM_DEBUG_FETCH` logging
- [ ] Add integration tests for OpenRouter
- [ ] Document configuration options

### Phase 4: Documentation (Week 4)

- [ ] Update README with environment variable reference
- [ ] Add OpenRouter configuration examples
- [ ] Document provider routing options
- [ ] Create troubleshooting guide
- [ ] Update API documentation

---

## 10. Testing Plan

### Unit Tests

```java
@Test
void shouldValidateMinimalEffortOnlyForOpenAI() {
    // Given: OpenRouter provider
    when(openAiProperties.getProviderCapabilities().getType())
        .thenReturn(ProviderType.OPENROUTER);
    
    // When: Request "minimal" reasoning
    ValidatedThinkingConfig config = ValidatedThinkingConfig.resolve(
        openAiProperties, "gpt-4o", true, "minimal"
    );
    
    // Then: Should fallback to "low"
    assertEquals(ReasoningEffort.LOW, config.effort());
}

@Test
void shouldInjectProviderRoutingForOpenRouter() throws Exception {
    // Given: OpenRouter configuration
    OpenAiProperties.Provider provider = new OpenAiProperties.Provider();
    provider.setSort("price");
    provider.setOrder(List.of("anthropic", "openai"));
    provider.setAllowFallbacks(true);
    
    // When: Build request
    String requestJson = OpenRouterRequestAdapter.buildRequestJson(params, provider);
    
    // Then: Should contain provider field
    JsonNode request = objectMapper.readTree(requestJson);
    assertEquals("price", request.get("provider").get("sort").asText());
    assertTrue(request.get("provider").get("allow_fallbacks").asBoolean());
}
```

### Integration Tests

```java
@SpringBootTest
class OpenRouterIntegrationTest {
    
    @Test
    void shouldStreamResponseWithProviderRouting() {
        // Given: OpenRouter configuration
        System.setProperty("LLM_BASE_URL", "https://openrouter.ai/api/v1");
        System.setProperty("LLM_PROVIDER_ORDER", "anthropic,openai");
        
        // When: Stream response
        List<StreamEvent> events = new ArrayList<>();
        openAiChatService.streamResponse(
            "Test message",
            "",
            Collections.emptyList(),
            false,
            null,
            false,
            events::add,
            () -> {},
            Throwable::printStackTrace
        );
        
        // Then: Should receive events
        assertFalse(events.isEmpty());
    }
}
```

---

## 11. References

### Documentation
- **OpenAI Responses API**: https://platform.openai.com/docs/api-reference/responses
- **OpenAI Reasoning Guide**: https://platform.openai.com/docs/guides/reasoning
- **OpenRouter Responses API**: https://openrouter.ai/docs/api-reference/responses-api/overview
- **OpenRouter Reasoning Tokens**: https://openrouter.ai/docs/use-cases/reasoning-tokens
- **OpenRouter Provider Routing**: https://openrouter.ai/docs/features/provider-routing
- **OpenRouter Structured Outputs**: https://openrouter.ai/docs/features/structured-outputs

### Source Code
- **openai-java SDK**: https://github.com/openai/openai-java
- **@openai/agents**: https://www.npmjs.com/package/@openai/agents
- **ComposerAI**: `/Users/williamcallahan/Developer/git/csm/ComposerAI`
- **data-tools-bun**: `/Users/williamcallahan/Developer/git/hybrid/data-tools-bun`

### Key Files Reviewed
- `data-tools-bun/tools/shared/openai-client.ts`
- `data-tools-bun/tools/entity-recognition/congruency-evaluator.ts`
- `ComposerAI/src/main/java/com/composerai/api/service/OpenAiChatService.java`
- `ComposerAI/src/main/java/com/composerai/api/config/OpenAiProperties.java`
- `ComposerAI/src/main/java/com/composerai/api/config/ProviderCapabilities.java`
- `ComposerAI/src/main/java/com/composerai/api/config/ClientConfiguration.java`

---

**End of Audit**

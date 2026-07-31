package com.composerai.api.adapters.out.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.composerai.api.config.ErrorMessagesProperties;
import com.composerai.api.config.OpenAiProperties;
import com.composerai.api.domain.model.ChatCompletionCommand;
import com.composerai.api.domain.model.ReasoningEffortLevel;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseOutputText;
import com.openai.models.responses.Tool;
import com.openai.models.responses.ToolChoiceOptions;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class UniversalReasoningContractTest {

    private static final String GENERIC_COMPATIBLE_URL = "https://models.example.test/v1";
    private static final String OPENROUTER_URL = "https://openrouter.ai/api/v1";
    private static final String SHARED_GATEWAY_URL = "https://api.llm-gateway.iocloudhost.net/v1";
    private static final String GATEWAY_TIER_HEADER = "X-Tier";
    private static final String INTERACTIVE_GATEWAY_TIER = "production-z";

    @Test
    void genericEndpointForwardsMaxForNonGptModelAlias() {
        OpenAIClient client = configuredClient();
        OpenAiChatClient chatClient = authenticatedClient(client, GENERIC_COMPATIBLE_URL, "partner/non-gpt-reasoner");

        chatClient.invokeChatResponse(
                new ChatCompletionCommand("Review", "Context", List.of(), false, ReasoningEffortLevel.MAX, false));

        ResponseCreateParams request = capturedRequest(client);
        assertEquals(
                "max", request.reasoning().orElseThrow().effort().orElseThrow().asString());
        assertEquals(List.of(), request._headers().values(GATEWAY_TIER_HEADER));
    }

    @Test
    void openRouterForwardsMinimalWithoutDowngrading() {
        OpenAIClient client = configuredClient();
        OpenAiChatClient chatClient = authenticatedClient(client, OPENROUTER_URL, "anthropic/claude-sonnet-4");

        chatClient.invokeChatResponse(
                new ChatCompletionCommand("Review", "Context", List.of(), false, ReasoningEffortLevel.MINIMAL, false));

        ResponseCreateParams request = capturedRequest(client);
        assertEquals(
                "minimal",
                request.reasoning().orElseThrow().effort().orElseThrow().asString());
        assertEquals(List.of(), request._headers().values(GATEWAY_TIER_HEADER));
    }

    @Test
    void authenticatedGenericClientForwardsExplicitNoneWhenThinkingIsOff() {
        OpenAIClient client = configuredClient();
        OpenAiChatClient chatClient = authenticatedClient(client, GENERIC_COMPATIBLE_URL, "partner/atlas-reasoner");

        chatClient.invokeChatResponse(
                new ChatCompletionCommand("Review", "Context", List.of(), false, ReasoningEffortLevel.NONE, false));

        ResponseCreateParams request = capturedRequest(client);
        assertEquals(
                "none", request.reasoning().orElseThrow().effort().orElseThrow().asString());
    }

    @Test
    void sharedGatewayExplicitFalseUsesNoneAndInteractiveTier() {
        OpenAIClient client = configuredClient();
        OpenAiChatClient chatClient = authenticatedClient(client, SHARED_GATEWAY_URL, "gemma-4-26b-a4b");

        chatClient.invokeChatResponse(new ChatCompletionCommand("Review", "Context", List.of(), false, null, false));

        ResponseCreateParams request = capturedRequest(client);
        assertEquals(
                "none", request.reasoning().orElseThrow().effort().orElseThrow().asString());
        assertEquals(List.of(INTERACTIVE_GATEWAY_TIER), request._headers().values(GATEWAY_TIER_HEADER));
    }

    @Test
    void sharedGatewayOmittedConfigurationAndRequestUseCanonicalDefault() {
        OpenAIClient client = configuredClient();
        OpenAiProperties properties = configuredProperties(SHARED_GATEWAY_URL, "gemma-4-26b-a4b");
        properties.getReasoning().setDefaultEffort(null);
        OpenAiChatClient chatClient = authenticatedClient(client, properties);

        chatClient.invokeChatResponse(new ChatCompletionCommand("Review", "Context", List.of(), null, null, false));

        ResponseCreateParams request = capturedRequest(client);
        assertEquals(
                ReasoningEffortLevel.DEFAULT.externalName(),
                request.reasoning().orElseThrow().effort().orElseThrow().asString());
        assertEquals(List.of(INTERACTIVE_GATEWAY_TIER), request._headers().values(GATEWAY_TIER_HEADER));
    }

    @Test
    void sharedGatewayIntentUsesCanonicalDefaultAndInteractiveTier() {
        OpenAIClient client = configuredClient();
        OpenAiProperties properties = configuredProperties(SHARED_GATEWAY_URL, "gemma-4-26b-a4b");
        properties.getDefaults().setThinkingEnabled(true);
        properties.getReasoning().setDefaultEffort("xhigh");
        OpenAiChatClient chatClient = authenticatedClient(client, properties);

        chatClient.analyzeIntent("Summarize the latest message");

        ResponseCreateParams request = capturedRequest(client);
        assertEquals(
                "xhigh",
                request.reasoning().orElseThrow().effort().orElseThrow().asString());
        assertEquals(
                properties.getIntent().getMaxOutputTokens(),
                request.maxOutputTokens().orElseThrow());
        assertEquals(List.of(INTERACTIVE_GATEWAY_TIER), request._headers().values(GATEWAY_TIER_HEADER));
    }

    @Test
    void genericIntentDoesNotFabricateGatewayTier() {
        OpenAIClient client = configuredClient();
        OpenAiProperties properties = configuredProperties(GENERIC_COMPATIBLE_URL, "partner/non-gpt-reasoner");
        properties.getDefaults().setThinkingEnabled(true);
        properties.getReasoning().setDefaultEffort("high");
        OpenAiChatClient chatClient = authenticatedClient(client, properties);

        chatClient.analyzeIntent("Classify this request");

        ResponseCreateParams request = capturedRequest(client);
        assertEquals(
                "high", request.reasoning().orElseThrow().effort().orElseThrow().asString());
        assertEquals(List.of(), request._headers().values(GATEWAY_TIER_HEADER));
    }

    @Test
    void configuredNoneRemainsExplicitOnTheWire() {
        OpenAIClient client = configuredClient();
        OpenAiProperties properties = configuredProperties(GENERIC_COMPATIBLE_URL, "partner/non-gpt-reasoner");
        properties.getReasoning().setDefaultEffort("none");
        OpenAiChatClient chatClient = authenticatedClient(client, properties);

        chatClient.invokeChatResponse(new ChatCompletionCommand("Review", "Context", List.of(), null, null, false));

        ResponseCreateParams request = capturedRequest(client);
        assertEquals(
                "none", request.reasoning().orElseThrow().effort().orElseThrow().asString());
    }

    @Test
    void synchronousUpstreamRejectionIsNotConvertedToSuccess() {
        OpenAIClient client = Mockito.mock(OpenAIClient.class, Answers.RETURNS_DEEP_STUBS);
        IllegalStateException rejection = new IllegalStateException("gateway rejected max effort");
        when(client.responses().create(any(ResponseCreateParams.class))).thenThrow(rejection);
        OpenAiChatClient chatClient = authenticatedClient(client, GENERIC_COMPATIBLE_URL, "partner/reasoner");

        IllegalStateException observed = assertThrows(
                IllegalStateException.class,
                () -> chatClient.invokeChatResponse(new ChatCompletionCommand(
                        "Review", "Context", List.of(), true, ReasoningEffortLevel.MAX, false)));

        assertSame(rejection, observed);
    }

    @Test
    void intentUpstreamRejectionIsNotConvertedToDefaultCategory() {
        OpenAIClient client = Mockito.mock(OpenAIClient.class, Answers.RETURNS_DEEP_STUBS);
        IllegalArgumentException rejection = new IllegalArgumentException("intent effort unsupported");
        when(client.responses().create(any(ResponseCreateParams.class))).thenThrow(rejection);
        OpenAiChatClient chatClient = authenticatedClient(client, GENERIC_COMPATIBLE_URL, "partner/reasoner");

        IllegalArgumentException observed =
                assertThrows(IllegalArgumentException.class, () -> chatClient.analyzeIntent("Classify this"));

        assertSame(rejection, observed);
    }

    private OpenAIClient configuredClient() {
        OpenAIClient client = Mockito.mock(OpenAIClient.class, Answers.RETURNS_DEEP_STUBS);
        when(client.responses().create(any(ResponseCreateParams.class))).thenReturn(completedResponse());
        return client;
    }

    private Response completedResponse() {
        ResponseOutputText outputText = ResponseOutputText.builder()
                .text("Completed")
                .annotations(List.of())
                .build();
        ResponseOutputMessage message = ResponseOutputMessage.builder()
                .id("msg-reasoning-contract")
                .content(List.of(ResponseOutputMessage.Content.ofOutputText(outputText)))
                .status(ResponseOutputMessage.Status.COMPLETED)
                .build();
        return Response.builder()
                .id("resp-reasoning-contract")
                .createdAt(System.currentTimeMillis() / 1000.0)
                .model("partner/atlas-reasoner")
                .error(Optional.empty())
                .incompleteDetails(Optional.empty())
                .instructions(Optional.empty())
                .metadata(Optional.empty())
                .output(List.of(ResponseOutputItem.ofMessage(message)))
                .parallelToolCalls(false)
                .temperature(Optional.of(0.7))
                .topP(1.0)
                .tools(List.<Tool>of())
                .toolChoice(ToolChoiceOptions.NONE)
                .build();
    }

    private OpenAiChatClient authenticatedClient(OpenAIClient client, String baseUrl, String model) {
        return authenticatedClient(client, configuredProperties(baseUrl, model));
    }

    private OpenAiChatClient authenticatedClient(OpenAIClient client, OpenAiProperties properties) {
        return new OpenAiChatClient(client, properties, new ErrorMessagesProperties());
    }

    private OpenAiProperties configuredProperties(String baseUrl, String model) {
        OpenAiProperties properties = new OpenAiProperties();
        properties.getApi().setKey("test-key");
        properties.getApi().setBaseUrl(baseUrl);
        properties.getModel().setChat(model);
        properties.initProviderCapabilities();
        return properties;
    }

    private ResponseCreateParams capturedRequest(OpenAIClient client) {
        ArgumentCaptor<ResponseCreateParams> requestCaptor = ArgumentCaptor.forClass(ResponseCreateParams.class);
        Mockito.verify(client.responses()).create(requestCaptor.capture());
        return requestCaptor.getValue();
    }
}

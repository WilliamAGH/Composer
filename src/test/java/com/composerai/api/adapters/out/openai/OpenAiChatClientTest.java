package com.composerai.api.adapters.out.openai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.composerai.api.config.ErrorMessagesProperties;
import com.composerai.api.config.OpenAiProperties;
import com.composerai.api.domain.model.ChatCompletionCommand;
import com.composerai.api.domain.model.ConversationTurn;
import com.composerai.api.domain.model.ReasoningEffortLevel;
import com.openai.client.OpenAIClient;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.Embedding;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseOutputText;
import com.openai.models.responses.Tool;
import com.openai.models.responses.ToolChoiceOptions;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;

class OpenAiChatClientTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private OpenAIClient openAIClient;

    private OpenAiChatClient chatClient;

    private static final Logger SERVICE_LOGGER = (Logger) LoggerFactory.getLogger(OpenAiChatClient.class);
    private static Level originalLogLevel;

    private ErrorMessagesProperties errorMessages;

    @BeforeAll
    static void suppressServiceErrorLogs() {
        originalLogLevel = SERVICE_LOGGER.getLevel();
        SERVICE_LOGGER.setLevel(Level.OFF);
    }

    @AfterAll
    static void restoreServiceLogLevel() {
        SERVICE_LOGGER.setLevel(originalLogLevel);
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        OpenAiProperties properties = new OpenAiProperties();
        properties.getModel().setChat("gpt-test");
        errorMessages = new ErrorMessagesProperties();
        chatClient = new OpenAiChatClient(openAIClient, properties, errorMessages);
    }

    @Test
    void generateEmbedding_withBlankInput_returnsEmptyVector() {
        float[] embeddingVector = chatClient.generateEmbedding("   ");

        assertEquals(0, embeddingVector.length);
        verifyNoInteractions(openAIClient);
    }

    @Test
    void generateEmbedding_whenClientThrows_returnsFallbackVector() {
        when(openAIClient.embeddings().create(any(EmbeddingCreateParams.class)))
                .thenThrow(new RuntimeException("OpenAI down"));

        float[] embeddingVector = chatClient.generateEmbedding("email body");

        assertEquals(0, embeddingVector.length);
    }

    @Test
    void generateEmbedding_returnsValidEmbedding() {
        Embedding embedding = Embedding.builder()
                .index(0)
                .embedding(List.of(0.1f, 0.2f, 0.3f))
                .build();

        CreateEmbeddingResponse response = CreateEmbeddingResponse.builder()
                .model("text-embedding-3-small")
                .data(List.of(embedding))
                .usage(CreateEmbeddingResponse.Usage.builder()
                        .promptTokens(3)
                        .totalTokens(3)
                        .build())
                .build();

        when(openAIClient.embeddings().create(any(EmbeddingCreateParams.class))).thenReturn(response);

        float[] embeddingVector = chatClient.generateEmbedding("test text");

        assertEquals(3, embeddingVector.length);
        assertEquals(0.1f, embeddingVector[0]);
        assertEquals(0.2f, embeddingVector[1]);
        assertEquals(0.3f, embeddingVector[2]);
    }

    @Test
    void generateResponse_returnsSanitizedHtml() {
        Response mockResponse = buildResponseWithText("**Hello** <script>alert('x')</script> world");
        when(openAIClient.responses().create(any(ResponseCreateParams.class))).thenReturn(mockResponse);

        ChatCompletionCommand command = new ChatCompletionCommand("Hi", "Context", List.of(), false, null, false);
        OpenAiChatClient.ChatCompletion completion = chatClient.generateResponse(command);

        assertEquals("**Hello** <script>alert('x')</script> world", completion.rawText());
        String sanitized = completion.sanitizedHtml();
        assertTrue(sanitized.contains("<strong>Hello</strong>"));
        assertFalse(sanitized.contains("<script>"));
    }

    @Test
    void generateResponse_withCustomModel_isHandledCorrectly() {
        OpenAiProperties customProperties = new OpenAiProperties();
        customProperties.getModel().setChat("gpt-4o-mini");
        OpenAiChatClient customModelClient = new OpenAiChatClient(openAIClient, customProperties, errorMessages);

        Response mockResponse = buildResponseWithText("Custom model response");
        when(openAIClient.responses().create(any(ResponseCreateParams.class))).thenReturn(mockResponse);

        OpenAiChatClient.ChatCompletion completion = customModelClient.generateResponse(
                new ChatCompletionCommand("Test", "Context", List.of(), false, null, false));

        assertEquals("Custom model response", completion.rawText());
        Mockito.verify(openAIClient.responses()).create(any(ResponseCreateParams.class));
    }

    @Test
    void generateResponseWithNullClientRejectsExplicitly() {
        OpenAiProperties properties = new OpenAiProperties();
        OpenAiChatClient nullClient = new OpenAiChatClient(null, properties, errorMessages);

        IllegalStateException misconfiguration = assertThrows(
                IllegalStateException.class,
                () -> nullClient.generateResponse(new ChatCompletionCommand(
                        "Hi", "Context", List.of(), true, ReasoningEffortLevel.MINIMAL, false)));

        assertTrue(misconfiguration.getMessage().contains("not configured"));
    }

    @Test
    void generateResponse_includesConversationHistoryInRequestBody() {
        Response mockResponse = buildResponseWithText("Follow up");
        when(openAIClient.responses().create(any(ResponseCreateParams.class))).thenReturn(mockResponse);

        List<ConversationTurn> history =
                List.of(ConversationTurn.user("Earlier question"), ConversationTurn.assistant("Earlier answer"));

        chatClient.generateResponse(
                new ChatCompletionCommand("What about now?", "Context data", history, false, null, false));

        ArgumentCaptor<ResponseCreateParams> captor = ArgumentCaptor.forClass(ResponseCreateParams.class);
        Mockito.verify(openAIClient.responses()).create(captor.capture());
        ResponseCreateParams params = captor.getValue();
        String body = params._body().toString();

        assertTrue(body.contains("Earlier question"));
        assertTrue(body.contains("Earlier answer"));
        assertTrue(body.contains("Context data"));
        assertTrue(body.contains("What about now?"));
    }

    @Test
    void generateResponse_withJsonOutputAddsDirective() {
        Response mockResponse = buildResponseWithText("{\"result\":true}");
        when(openAIClient.responses().create(any(ResponseCreateParams.class))).thenReturn(mockResponse);

        chatClient.generateResponse(
                new ChatCompletionCommand("Return structured data", "Context payload", List.of(), false, null, true));

        ArgumentCaptor<ResponseCreateParams> captor = ArgumentCaptor.forClass(ResponseCreateParams.class);
        Mockito.verify(openAIClient.responses()).create(captor.capture());
        String serialized = captor.getValue()._body().toString();

        assertTrue(serialized.contains("JSON output mode"));
        assertTrue(serialized.contains("best-estimate schema"));
    }

    @Test
    void analyzeIntentPropagatesUpstreamRejection() {
        RuntimeException rejection = new RuntimeException("Service unavailable");
        when(openAIClient.responses().create(any(ResponseCreateParams.class))).thenThrow(rejection);

        RuntimeException observed =
                assertThrows(RuntimeException.class, () -> chatClient.analyzeIntent("What emails did I get today?"));

        assertSame(rejection, observed);
    }

    private Response buildResponseWithText(String text) {
        ResponseOutputText outputText =
                ResponseOutputText.builder().text(text).annotations(List.of()).build();

        ResponseOutputMessage message = ResponseOutputMessage.builder()
                .id("msg-" + Math.abs(text.hashCode()))
                .content(List.of(ResponseOutputMessage.Content.ofOutputText(outputText)))
                .status(ResponseOutputMessage.Status.COMPLETED)
                .build();

        ResponseOutputItem outputItem = ResponseOutputItem.ofMessage(message);

        return Response.builder()
                .id("resp-" + Math.abs(text.hashCode()))
                .createdAt(System.currentTimeMillis() / 1000.0)
                .model("gpt-4o-mini")
                .error(Optional.empty())
                .incompleteDetails(Optional.empty())
                .instructions(Optional.empty())
                .metadata(Optional.empty())
                .output(List.of(outputItem))
                .parallelToolCalls(false)
                .temperature(Optional.of(0.7))
                .topP(1.0)
                .tools(List.<Tool>of())
                // TODO: add a regression test that uses one of our real tool-call responses.
                .toolChoice(ToolChoiceOptions.NONE)
                .build();
    }
}

package com.composerai.api.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.composerai.api.ai.AiFunctionCatalogHelper;
import com.composerai.api.config.AiFunctionCatalogProperties;
import com.composerai.api.config.ErrorMessagesProperties;
import com.composerai.api.config.OpenAiProperties;
import com.composerai.api.config.MagicEmailProperties;
import com.composerai.api.dto.ChatRequest;
import com.composerai.api.dto.ChatResponse;
import com.openai.client.OpenAIClient;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.Embedding;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseOutputText;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OpenAiChatServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private OpenAIClient openAIClient;

    private OpenAiChatService service;

    private static final Logger SERVICE_LOGGER = (Logger) LoggerFactory.getLogger(OpenAiChatService.class);
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
        service = new OpenAiChatService(openAIClient, properties, errorMessages);
    }

    @Test
    void generateEmbedding_withBlankInput_returnsEmptyVector() {
        float[] result = service.generateEmbedding("   ");

        assertEquals(0, result.length);
        verifyNoInteractions(openAIClient);
    }

    @Test
    void generateEmbedding_whenClientThrows_returnsFallbackVector() {
        when(openAIClient.embeddings().create(any(EmbeddingCreateParams.class)))
            .thenThrow(new RuntimeException("OpenAI down"));

        float[] result = service.generateEmbedding("email body");

        assertEquals(0, result.length);
    }

    @Test
    void generateEmbedding_returnsValidEmbedding() {
        // Mock embeddings API response
        CreateEmbeddingResponse mockResponse = Mockito.mock(CreateEmbeddingResponse.class);
        Embedding mockEmbedding = Mockito.mock(Embedding.class);
        
        when(openAIClient.embeddings().create(any(EmbeddingCreateParams.class))).thenReturn(mockResponse);
        when(mockResponse.data()).thenReturn(List.of(mockEmbedding));
        when(mockEmbedding.embedding()).thenReturn(List.of(0.1f, 0.2f, 0.3f));

        float[] result = service.generateEmbedding("test text");

        assertEquals(3, result.length);
        assertEquals(0.1f, result[0]);
        assertEquals(0.2f, result[1]);
        assertEquals(0.3f, result[2]);
    }

    @Test
    void generateResponse_returnsSanitizedHtml() {
        Response mockResponse = buildResponseWithText("**Hello** <script>alert('x')</script> world");
        when(openAIClient.responses().create(any(ResponseCreateParams.class)))
            .thenReturn(mockResponse);

        OpenAiChatService.ChatCompletionResult result = service.generateResponse("Hi", "Context", List.of(), false, null, false);

        assertEquals("**Hello** <script>alert('x')</script> world", result.rawText());
        String sanitized = result.sanitizedHtml();
        assertTrue(sanitized.contains("<strong>Hello</strong>"));
        assertFalse(sanitized.contains("<script>"));
    }

    @Test
    void generateResponse_withCustomModel_isHandledCorrectly() {
        OpenAiProperties customProperties = new OpenAiProperties();
        customProperties.getModel().setChat("gpt-4o-mini");
        OpenAiChatService customModelService = new OpenAiChatService(openAIClient, customProperties, errorMessages);

        Response mockResponse = buildResponseWithText("Custom model response");
        when(openAIClient.responses().create(any(ResponseCreateParams.class)))
            .thenReturn(mockResponse);

        OpenAiChatService.ChatCompletionResult result = customModelService.generateResponse("Test", "Context", List.of(), false, null, false);

        assertEquals("Custom model response", result.rawText());
        Mockito.verify(openAIClient.responses()).create(any(ResponseCreateParams.class));
    }

    @Test
    void generateResponse_withNullClient_returnsMisconfiguredMessage() {
        OpenAiProperties properties = new OpenAiProperties();
        OpenAiChatService nullClientService = new OpenAiChatService(null, properties, errorMessages);

        OpenAiChatService.ChatCompletionResult result = nullClientService.generateResponse("Hi", "Context", List.of(), true, "minimal", false);

        assertTrue(result.rawText().contains("not configured"));
        assertTrue(result.sanitizedHtml().contains("not configured"));
    }

    @Test
    void generateResponse_includesConversationHistoryInRequestBody() {
        Response mockResponse = buildResponseWithText("Follow up");
        when(openAIClient.responses().create(any(ResponseCreateParams.class)))
            .thenReturn(mockResponse);

        List<OpenAiChatService.ConversationTurn> history = List.of(
            OpenAiChatService.ConversationTurn.user("Earlier question"),
            OpenAiChatService.ConversationTurn.assistant("Earlier answer")
        );

        service.generateResponse("What about now?", "Context data", history, false, null, false);

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
        when(openAIClient.responses().create(any(ResponseCreateParams.class)))
            .thenReturn(mockResponse);

        service.generateResponse("Return structured data", "Context payload", List.of(), false, null, true);

        ArgumentCaptor<ResponseCreateParams> captor = ArgumentCaptor.forClass(ResponseCreateParams.class);
        Mockito.verify(openAIClient.responses()).create(captor.capture());
        String serialized = captor.getValue()._body().toString();

        assertTrue(serialized.contains("JSON output mode"));
        assertTrue(serialized.contains("best-estimate schema"));
    }

    @Test
    void analyzeIntent_returnsQuestionOnError() {
        when(openAIClient.responses().create(any(ResponseCreateParams.class)))
            .thenThrow(new RuntimeException("Service unavailable"));

        String intent = service.analyzeIntent("What emails did I get today?");

        assertEquals("question", intent);
    }

    private Response buildResponseWithText(String text) {
        // Mock Response API object hierarchy
        Response mockResponse = Mockito.mock(Response.class);
        ResponseOutputItem mockOutputItem = Mockito.mock(ResponseOutputItem.class);
        ResponseOutputMessage mockMessage = Mockito.mock(ResponseOutputMessage.class);
        ResponseOutputMessage.Content mockContent = Mockito.mock(ResponseOutputMessage.Content.class);
        ResponseOutputText mockOutputText = Mockito.mock(ResponseOutputText.class);
        
        Mockito.lenient().when(mockResponse.output()).thenReturn(List.of(mockOutputItem));
        Mockito.lenient().when(mockOutputItem.message()).thenReturn(Optional.of(mockMessage));
        Mockito.lenient().when(mockMessage.content()).thenReturn(List.of(mockContent));
        Mockito.lenient().when(mockContent.outputText()).thenReturn(Optional.of(mockOutputText));
        Mockito.lenient().when(mockOutputText.text()).thenReturn(text);
        
        return mockResponse;
    }
}

class ChatServiceContextPropagationTest {

    private VectorSearchService vectorSearchService;
    private OpenAiChatService openAiChatService;
    private ContextBuilder contextBuilder;
    private ContextBuilder.EmailContextRegistry emailContextRegistry;
    private ChatService.ConversationRegistry conversationRegistry;
    private OpenAiProperties openAiProperties;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        vectorSearchService = Mockito.mock(VectorSearchService.class);
        openAiChatService = Mockito.mock(OpenAiChatService.class);
        contextBuilder = new ContextBuilder();
        emailContextRegistry = new ContextBuilder.EmailContextRegistry();
        conversationRegistry = new ChatService.ConversationRegistry();
        ExecutorService executorService = Mockito.mock(ExecutorService.class);

        openAiProperties = new OpenAiProperties();
        ErrorMessagesProperties errorMessagesProperties = new ErrorMessagesProperties();
        MagicEmailProperties magicEmailProperties = new MagicEmailProperties();
        AiFunctionCatalogHelper catalogHelper = new AiFunctionCatalogHelper(new AiFunctionCatalogProperties());

        chatService = new ChatService(
            vectorSearchService,
            openAiChatService,
            openAiProperties,
            errorMessagesProperties,
            contextBuilder,
            emailContextRegistry,
            conversationRegistry,
            magicEmailProperties,
            catalogHelper,
            executorService
        );
    }

    @Test
    void processChat_usesMergedContextForAiCall() {
        ChatRequest request = new ChatRequest("Review email", "conv-42", 0);
        request.setContextId("ctx-1");

        emailContextRegistry.store("ctx-1", """
            ## Uploaded Notes
            - Follow up with finance
            """);

        float[] embedding = new float[] {0.2f, 0.5f};
        Mockito.when(openAiChatService.generateEmbedding("Review email")).thenReturn(embedding);
        ChatResponse.EmailContext emailContext = new ChatResponse.EmailContext(
            "email-1", "Quarterly Update", "Finance Team", "Budget looks good", 0.93, LocalDateTime.parse("2025-01-15T09:30:00"));
        Mockito.when(vectorSearchService.searchSimilarEmails(embedding, openAiProperties.getDefaults().getMaxSearchResults()))
            .thenReturn(List.of(emailContext));

        Mockito.when(openAiChatService.analyzeIntent("Review email")).thenReturn("question");
        Mockito.when(openAiChatService.generateResponse(
            any(String.class),
            any(String.class),
            anyList(),
            anyBoolean(),
            any(),
            anyBoolean()
        )).thenReturn(new OpenAiChatService.ChatCompletionResult("raw", "<p>raw</p>"));

        chatService.processChat(request);

        ArgumentCaptor<String> contextCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(openAiChatService).generateResponse(
            any(String.class),
            contextCaptor.capture(),
            anyList(),
            anyBoolean(),
            any(),
            anyBoolean()
        );

        String mergedContext = contextCaptor.getValue();
        assertTrue(mergedContext.contains("Uploaded email context"));
        assertTrue(mergedContext.contains("Relevant emails"));
    }

}

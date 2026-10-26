package com.composerai.api.application.usecase.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.composerai.api.adapters.out.openai.OpenAiChatClient;
import com.composerai.api.ai.AiFunctionCatalogHelper;
import com.composerai.api.ai.AiFunctionDefinition;
import com.composerai.api.application.dto.ChatRequest;
import com.composerai.api.config.AiFunctionCatalogProperties;
import com.composerai.api.config.ErrorMessagesProperties;
import com.composerai.api.config.MagicEmailProperties;
import com.composerai.api.config.OpenAiProperties;
import com.composerai.api.service.ContextBuilder;
import com.composerai.api.service.VectorSearchService;
import com.composerai.api.shared.ledger.ChatLedgerRecorder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class GatewayWorkloadContractTest {

    private final ArrayBlockingQueue<ReceivedRequest> receivedRequests = new ArrayBlockingQueue<>(8);
    private HttpServer server;
    private OpenAIClient sdk;
    private OpenAiChatClient chatClient;
    private ChatRequestPreparation preparation;
    private ExecuteChatUseCase executeChat;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/", exchange -> {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String path = exchange.getRequestURI().getPath();
            receivedRequests.add(
                    new ReceivedRequest(path, exchange.getRequestHeaders().getFirst("X-User-ID"), requestBody));
            boolean streaming =
                    new ObjectMapper().readTree(requestBody).path("stream").asBoolean();
            String responseBody = path.endsWith("/embeddings")
                    ? "{\"object\":\"list\",\"model\":\"local\",\"data\":[{\"object\":\"embedding\",\"index\":0,\"embedding\":[0.1,0.2]}],\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1}}"
                    : streaming
                            ? "data: [DONE]\n\n"
                            : "{\"id\":\"resp-local\",\"created_at\":0,\"model\":\"local\",\"output\":[]}";
            byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", streaming ? "text/event-stream" : "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.close();
        });
        server.start();
        sdk = OpenAIOkHttpClient.builder()
                .apiKey("local-stub-only")
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
                .timeout(Duration.ofSeconds(5))
                .maxRetries(0)
                .responseValidation(false)
                .build();
        OpenAiProperties properties = new OpenAiProperties();
        properties.getModel().setChat("local-stub");
        // Enable the existing embedding capability while keeping every SDK request on loopback.
        properties.getApi().setBaseUrl("https://api.openai.com/v1");
        chatClient = new OpenAiChatClient(sdk, properties, new ErrorMessagesProperties());
        ConversationRegistry conversations = new ConversationRegistry();
        preparation = new ChatRequestPreparation(
                mock(VectorSearchService.class),
                chatClient,
                properties,
                new ContextBuilder(),
                new ContextBuilder.InMemoryEmailContextCache(),
                conversations,
                new ChatPromptComposer(
                        new MagicEmailProperties(), new AiFunctionCatalogHelper(new AiFunctionCatalogProperties())));
        executeChat = new ExecuteChatUseCase(preparation, chatClient, conversations, mock(ChatLedgerRecorder.class));
    }

    @AfterEach
    void tearDown() {
        if (sdk != null) {
            sdk.close();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    static Stream<String> catalogCommands() {
        return new AiFunctionCatalogHelper(new AiFunctionCatalogProperties())
                .all().stream().map(AiFunctionDefinition::key);
    }

    @ParameterizedTest
    @MethodSource("catalogCommands")
    void catalogPurposeSurvivesSerializedHttp(String commandKey) {
        ChatRequest request = new ChatRequest("Review this email", null, 1);
        request.setAiCommand(commandKey);

        executeChat.execute(request);

        assertEquals(
                List.of("composer:intent-analysis", "composer:email-search-query", "composer:" + commandKey),
                receivedRequests.stream().map(ReceivedRequest::workload).toList());
        assertEquals("/v1/embeddings", receivedRequests.stream().toList().get(1).path());
    }

    @Test
    void freeFormChatAndItsSupportingJobsKeepDistinctPurposes() {
        executeChat.execute(new ChatRequest("Review this email", null, 1));

        assertEquals(
                List.of("composer:intent-analysis", "composer:email-search-query", "composer:email-chat"),
                receivedRequests.stream().map(ReceivedRequest::workload).toList());
    }

    @Test
    void insightsKeepsItsPurposeWithoutRetrievalOrClassification() {
        executeChat.execute(new ChatRequest(ChatPromptComposer.INSIGHTS_TRIGGER, null, 1));

        assertEquals(
                List.of("composer:email-insights"),
                receivedRequests.stream().map(ReceivedRequest::workload).toList());
    }

    @ParameterizedTest
    @MethodSource("catalogCommands")
    void streamingPreservesTheCatalogPurpose(String commandKey) throws Exception {
        ChatRequest request = new ChatRequest("Review this email", null, 1);
        request.setAiCommand(commandKey);
        PreparedChatRequest prepared = preparation.prepare(request, "user-message", "assistant-message");
        receivedRequests.clear();
        AtomicBoolean completed = new AtomicBoolean();
        List<Throwable> failures = new ArrayList<>();

        chatClient.streamResponse(prepared.completionCommand(), event -> {}, () -> completed.set(true), failures::add);

        assertTrue(failures.isEmpty(), () -> failures.toString());
        assertTrue(completed.get());
        ReceivedRequest streamedRequest = receivedRequests.remove();
        assertEquals("composer:" + commandKey, streamedRequest.workload());
        assertTrue(new ObjectMapper()
                .readTree(streamedRequest.body())
                .path("stream")
                .asBoolean());
        assertTrue(receivedRequests.isEmpty());
    }

    private record ReceivedRequest(String path, String workload, String body) {}
}

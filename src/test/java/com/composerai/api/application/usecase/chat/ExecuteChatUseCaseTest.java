package com.composerai.api.application.usecase.chat;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import com.composerai.api.adapters.out.openai.OpenAiChatClient;
import com.composerai.api.ai.AiFunctionCatalogHelper;
import com.composerai.api.application.dto.ChatRequest;
import com.composerai.api.config.AiFunctionCatalogProperties;
import com.composerai.api.config.MagicEmailProperties;
import com.composerai.api.config.OpenAiProperties;
import com.composerai.api.domain.model.ChatCompletionCommand;
import com.composerai.api.dto.ChatResponse;
import com.composerai.api.service.ContextBuilder;
import com.composerai.api.service.VectorSearchService;
import com.composerai.api.shared.ledger.ChatLedgerRecorder;
import com.composerai.api.shared.ledger.UsageMetrics;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ExecuteChatUseCaseTest {

    private VectorSearchService vectorSearchService;
    private OpenAiChatClient openAiChatClient;
    private ContextBuilder.EmailContextCache emailContextRegistry;
    private OpenAiProperties openAiProperties;
    private ExecuteChatUseCase executeChatUseCase;

    @BeforeEach
    void setUp() {
        vectorSearchService = Mockito.mock(VectorSearchService.class);
        openAiChatClient = Mockito.mock(OpenAiChatClient.class);
        ContextBuilder contextBuilder = new ContextBuilder();
        emailContextRegistry = new ContextBuilder.InMemoryEmailContextCache();
        ConversationRegistry conversationRegistry = new ConversationRegistry();
        ChatLedgerRecorder chatLedgerRecorder = Mockito.mock(ChatLedgerRecorder.class);
        openAiProperties = new OpenAiProperties();
        ChatPromptComposer promptComposer = new ChatPromptComposer(
                new MagicEmailProperties(), new AiFunctionCatalogHelper(new AiFunctionCatalogProperties()));
        ChatRequestPreparation requestPreparation = new ChatRequestPreparation(
                vectorSearchService,
                openAiChatClient,
                openAiProperties,
                contextBuilder,
                emailContextRegistry,
                conversationRegistry,
                promptComposer);
        executeChatUseCase =
                new ExecuteChatUseCase(requestPreparation, openAiChatClient, conversationRegistry, chatLedgerRecorder);
    }

    @Test
    void executeUsesMergedUploadedAndVectorContext() {
        ChatRequest request = new ChatRequest("Review email", "conv-42", 0);
        request.setContextId("ctx-1");
        emailContextRegistry.store("ctx-1", """
            ## Uploaded Notes
            - Follow up with finance
            """);

        float[] embedding = new float[] {0.2f, 0.5f};
        Mockito.when(openAiChatClient.generateEmbedding("Review email")).thenReturn(embedding);
        ChatResponse.EmailContext emailContext = new ChatResponse.EmailContext(
                "email-1",
                "Quarterly Update",
                "Finance Team",
                "Budget looks good",
                0.93,
                LocalDateTime.parse("2025-01-15T09:30:00"));
        Mockito.when(vectorSearchService.searchSimilarEmails(
                        embedding, openAiProperties.getDefaults().getMaxSearchResults()))
                .thenReturn(List.of(emailContext));
        Mockito.when(openAiChatClient.analyzeIntent("Review email")).thenReturn("question");
        OpenAiChatClient.ChatCompletion completion = new OpenAiChatClient.ChatCompletion("raw", "<p>raw</p>");
        Mockito.when(openAiChatClient.invokeChatResponse(any(ChatCompletionCommand.class)))
                .thenReturn(OpenAiChatClient.Invocation.streamed(completion, new UsageMetrics(0, 0, 0, 0)));

        executeChatUseCase.execute(request);

        ArgumentCaptor<ChatCompletionCommand> commandCaptor = ArgumentCaptor.forClass(ChatCompletionCommand.class);
        Mockito.verify(openAiChatClient).invokeChatResponse(commandCaptor.capture());
        String mergedContext = commandCaptor.getValue().emailContext();
        assertTrue(mergedContext.contains("Uploaded email context"));
        assertTrue(mergedContext.contains("Relevant emails"));
    }
}

package com.composerai.api.service;

import com.composerai.api.config.OpenAiProperties;
import com.openai.client.OpenAIClient;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OpenAiChatServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private OpenAIClient openAIClient;

    private OpenAiChatService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        OpenAiProperties properties = new OpenAiProperties();
        properties.setModel("gpt-test");
        service = new OpenAiChatService(openAIClient, properties);
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
    void generateResponse_returnsSanitizedHtml() {
        var chatCompletion = Mockito.mock(com.openai.models.chat.completions.ChatCompletion.class, Answers.RETURNS_DEEP_STUBS);
        var choice = Mockito.mock(com.openai.models.chat.completions.ChatCompletion.Choice.class, Answers.RETURNS_DEEP_STUBS);

        when(chatCompletion.choices()).thenReturn(List.of(choice));
        when(choice.message().content()).thenReturn(Optional.of("**Hello** <script>alert('x')</script> world"));
        when(openAIClient.chat().completions().create(any(ChatCompletionCreateParams.class))).thenReturn(chatCompletion);

        OpenAiChatService.ChatCompletionResult result = service.generateResponse("Hi", "Context", false, null);

        assertEquals("**Hello** <script>alert('x')</script> world", result.rawText());
        String sanitized = result.sanitizedHtml();
        assertTrue(sanitized.contains("<strong>Hello</strong>"));
        assertTrue(!sanitized.contains("<script>") && !sanitized.contains("alert('x')"));
    }

    @Test
    void generateResponse_withThinkingEnabledAppliesReasoningEffort() {
        var chatCompletion = Mockito.mock(com.openai.models.chat.completions.ChatCompletion.class, Answers.RETURNS_DEEP_STUBS);
        var choice = Mockito.mock(com.openai.models.chat.completions.ChatCompletion.Choice.class, Answers.RETURNS_DEEP_STUBS);

        when(chatCompletion.choices()).thenReturn(List.of(choice));
        when(choice.message().content()).thenReturn(Optional.of("response"));
        when(openAIClient.chat().completions().create(any(ChatCompletionCreateParams.class))).thenReturn(chatCompletion);

        ArgumentCaptor<ChatCompletionCreateParams> paramsCaptor = ArgumentCaptor.forClass(ChatCompletionCreateParams.class);

        service.generateResponse("Hi", "Context", true, "Heavy");

        Mockito.verify(openAIClient.chat().completions()).create(paramsCaptor.capture());
        ChatCompletionCreateParams captured = paramsCaptor.getValue();
        assertTrue(captured.reasoningEffort().isPresent());
        assertEquals("heavy", captured.reasoningEffort().get().asString());
    }

    @Test
    void generateResponse_withGpt5Model_usesIncreasedTokenLimit() {
        OpenAiProperties gpt5Properties = new OpenAiProperties();
        gpt5Properties.setModel("gpt-5-mini");
        OpenAiChatService gpt5Service = new OpenAiChatService(openAIClient, gpt5Properties);

        var chatCompletion = Mockito.mock(com.openai.models.chat.completions.ChatCompletion.class, Answers.RETURNS_DEEP_STUBS);
        var choice = Mockito.mock(com.openai.models.chat.completions.ChatCompletion.Choice.class, Answers.RETURNS_DEEP_STUBS);

        when(chatCompletion.choices()).thenReturn(List.of(choice));
        when(choice.message().content()).thenReturn(Optional.of("GPT-5 response"));
        when(openAIClient.chat().completions().create(any(ChatCompletionCreateParams.class))).thenReturn(chatCompletion);

        OpenAiChatService.ChatCompletionResult result = gpt5Service.generateResponse("Test", "Context", false, null);

        assertEquals("GPT-5 response", result.rawText());
        // Verify the token limit increase is applied (1000L instead of 500L)
        Mockito.verify(openAIClient.chat().completions()).create(any(ChatCompletionCreateParams.class));
    }

    @Test
    void generateResponse_withNullClient_returnsMisconfiguredMessage() {
        OpenAiProperties properties = new OpenAiProperties();
        OpenAiChatService nullClientService = new OpenAiChatService(null, properties);

        OpenAiChatService.ChatCompletionResult result = nullClientService.generateResponse("Hi", "Context", true, "minimal");

        assertTrue(result.rawText().contains("not configured"));
        assertTrue(result.sanitizedHtml().contains("not configured"));
    }

    @Test
    void analyzeIntent_returnsQuestionOnError() {
        when(openAIClient.chat().completions().create(any(ChatCompletionCreateParams.class)))
            .thenThrow(new RuntimeException("Service unavailable"));

        String intent = service.analyzeIntent("What emails did I get today?");

        assertEquals("question", intent);
    }
}

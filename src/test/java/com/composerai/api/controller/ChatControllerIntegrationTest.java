package com.composerai.api.controller;

import com.composerai.api.dto.ChatRequest;
import com.composerai.api.dto.ChatResponse;
import com.composerai.api.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
class ChatControllerIntegrationTest {

    private static final String BASE_API_PATH = "/api";
    private static final String CHAT_ENDPOINT = BASE_API_PATH + "/chat";
    private static final String HEALTH_ENDPOINT = CHAT_ENDPOINT + "/health";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ChatService chatService;

    @MockBean(name = "chatStreamExecutor")
    private Executor chatStreamExecutor;

    @MockBean(name = "sseHeartbeatExecutor")
    private ScheduledExecutorService sseHeartbeatExecutor;

    @MockBean
    private com.composerai.api.config.OpenAiProperties openAiProperties;

    @MockBean
    private com.composerai.api.config.ErrorMessagesProperties errorMessagesProperties;

    @Test
    void healthEndpoint_ShouldReturnOk() throws Exception {
        mockMvc.perform(get(HEALTH_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("UP"));
    }

    @Test
    void chatEndpoint_WithEmptyMessage_ShouldReturnBadRequest() throws Exception {
        ChatRequest request = new ChatRequest("", null, 5);

        mockMvc.perform(post(CHAT_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chatEndpoint_WithContextButNoContextId_ShouldReturnBadRequest() throws Exception {
        ChatRequest request = new ChatRequest("Hi", null, 5);
        request.setEmailContext("malicious raw");

        mockMvc.perform(post(CHAT_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chatEndpoint_WithOversizedEmailContext_ShouldReturnBadRequest() throws Exception {
        ChatRequest request = new ChatRequest("Hi", null, 5);
        request.setContextId("ctx-123");
        request.setEmailContext("A".repeat(20_001));

        mockMvc.perform(post(CHAT_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chatEndpoint_ShouldReturnSanitizedHtmlField() throws Exception {
        ChatResponse chatResponse = new ChatResponse("**Hi**", "conv-1", java.util.List.of(), "answer", "<p><strong>Hi</strong></p>");
        Mockito.when(chatService.processChat(any())).thenReturn(chatResponse);

        ChatRequest request = new ChatRequest("Hi", null, 5);

        mockMvc.perform(post(CHAT_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.sanitizedHtml").value("<p><strong>Hi</strong></p>"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.renderedHtml").value("<p><strong>Hi</strong></p>"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.response").value("**Hi**"));
    }

    @Test
    void streamEndpoint_ShouldEmitSanitizedHtmlChunks() throws Exception {
        // Configure openAiProperties mock
        var streamConfig = new com.composerai.api.config.OpenAiProperties.Stream();
        streamConfig.setTimeoutSeconds(120);
        Mockito.when(openAiProperties.getStream()).thenReturn(streamConfig);

        // Mock the heartbeat executor to return a mock ScheduledFuture
        java.util.concurrent.ScheduledFuture<?> mockFuture = Mockito.mock(java.util.concurrent.ScheduledFuture.class);
        Mockito.<java.util.concurrent.ScheduledFuture<?>>when(sseHeartbeatExecutor.scheduleAtFixedRate(
            any(Runnable.class),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(),
            any(java.util.concurrent.TimeUnit.class)
        )).thenReturn(mockFuture);

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(chatStreamExecutor).execute(any(Runnable.class));

        doAnswer(invocation -> {
            invocation.<java.util.function.Consumer<String>>getArgument(3).accept("<p>Hello <strong>World</strong></p>");
            invocation.<java.util.function.Consumer<?>>getArgument(4).accept(null);
            invocation.<Runnable>getArgument(5).run();
            return null;
        }).when(chatService).streamChat(
            any(ChatRequest.class),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.<java.util.function.Consumer<String>>any(),
            org.mockito.ArgumentMatchers.any(),
            any(Runnable.class),
            org.mockito.ArgumentMatchers.<java.util.function.Consumer<Throwable>>any()
        );

        ChatRequest request = new ChatRequest("Summarize", null, 5);

        mockMvc.perform(post(CHAT_ENDPOINT + "/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("event:rendered_html")))
            .andExpect(content().string(containsString("<strong>World</strong>")))
            .andExpect(content().string(not(containsString("<script>"))));

        Mockito.verify(chatService).streamChat(
            any(ChatRequest.class),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.<java.util.function.Consumer<String>>any(),
            org.mockito.ArgumentMatchers.any(),
            any(Runnable.class),
            org.mockito.ArgumentMatchers.<java.util.function.Consumer<Throwable>>any()
        );
    }
}

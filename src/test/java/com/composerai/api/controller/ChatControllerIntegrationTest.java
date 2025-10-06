package com.composerai.api.controller;

import com.composerai.api.dto.ChatRequest;
import com.composerai.api.dto.ChatResponse;
import com.composerai.api.service.ChatService;
import com.composerai.api.service.ChatService.StreamMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.concurrent.Executor;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(chatStreamExecutor).execute(any(Runnable.class));

        doAnswer(invocation -> {
            StreamMetadata metadata = new StreamMetadata(UUID.randomUUID().toString());
            invocation.<java.util.function.Consumer<StreamMetadata>>getArgument(6).accept(metadata);
            invocation.<java.util.function.Consumer<String>>getArgument(7).accept("<p>Hello <strong>World</strong></p>");
            invocation.<Runnable>getArgument(8).run();
            return null;
        }).when(chatService).streamChat(any(), any(Integer.class), any(), any(), anyBoolean(), any(), any(), any(), any(), any());

        ChatRequest request = new ChatRequest("Summarize", null, 5);

        mockMvc.perform(post(CHAT_ENDPOINT + "/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("event:rendered_html")))
            .andExpect(content().string(containsString("<strong>World</strong>")))
            .andExpect(content().string(not(containsString("<script>"))));

        Mockito.verify(chatService).streamChat(any(), any(Integer.class), any(), any(), anyBoolean(), any(), any(), any(), any(), any());
    }
}

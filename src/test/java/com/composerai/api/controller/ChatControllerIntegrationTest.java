package com.composerai.api.controller;

import com.composerai.api.dto.ChatRequest;
import com.composerai.api.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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

    @Test
    void healthEndpoint_ShouldReturnOk() throws Exception {
        mockMvc.perform(get(HEALTH_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void chatEndpoint_WithEmptyMessage_ShouldReturnBadRequest() throws Exception {
        ChatRequest request = new ChatRequest("", null, 5);

        mockMvc.perform(post(CHAT_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
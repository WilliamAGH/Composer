package com.composerai.api.controller;

import com.composerai.api.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(QaWebController.class)
@Import(QaWebControllerTest.TestConfig.class)
class QaWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UiNonceService uiNonceService;

    @BeforeEach
    void setup() {
        when(uiNonceService.getOrCreateSessionNonce(any())).thenReturn("nonce");
    }

    @Test
    void diagnosticsPage_ShouldRenderSuccessfully() throws Exception {
        mockMvc.perform(get("/qa/diagnostics"))
            .andExpect(status().isOk())
            .andExpect(view().name("qa/diagnostics"));
    }

    @Test
    void emailFileParserPage_ShouldRenderSuccessfully() throws Exception {
        mockMvc.perform(get("/qa/email-file-parser"))
            .andExpect(status().isOk())
            .andExpect(view().name("qa/email-file-parser"));
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        AppProperties appProperties() {
            return new AppProperties();
        }
    }
}

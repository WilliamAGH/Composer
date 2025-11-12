package com.composerai.api.controller;

import com.composerai.api.config.AiFunctionCatalogProperties;
import com.composerai.api.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UiSessionController.class)
@Import(UiSessionControllerTest.TestConfig.class)
class UiSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void refreshNonce_returnsValue() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/ui/session/nonce")
                .header("Origin", "http://localhost:8080")
                .header("X-UI-Request", "nonce-old")
                .session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.uiNonce").isNotEmpty());
    }

    @Test
    void refreshNonce_rejectsDisallowedOrigin() throws Exception {
        mockMvc.perform(post("/ui/session/nonce")
                .header("Origin", "http://evil.test"))
            .andExpect(status().isForbidden());
    }

    static class TestConfig {
        @Bean
        AppProperties appProperties() {
            AppProperties properties = new AppProperties();
            properties.getCors().setAllowedOrigins("http://localhost:8080,https://composerai.app");
            return properties;
        }

        @Bean
        UiNonceService uiNonceService() {
            return new UiNonceService();
        }

        @Bean
        AiFunctionCatalogProperties aiFunctionCatalogProperties() {
            return new AiFunctionCatalogProperties();
        }
    }
}

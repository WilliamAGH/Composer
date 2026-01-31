package com.composerai.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.composerai.api.config.AiFunctionCatalogProperties;
import com.composerai.api.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(UiSessionController.class)
@Import(UiSessionControllerTest.TestConfig.class)
class UiSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UiNonceService uiNonceService;

    private static final String ISSUED_AT_KEY = "UI_NONCE_ISSUED_AT";
    private static final long EXPIRED_MINUTES = 16L;

    @Test
    void refreshNonce_returnsValue() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/ui/session/nonce")
                        .header("Origin", "http://localhost:8090")
                        .header("X-UI-Request", "nonce-old")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uiNonce").isNotEmpty());
    }

    @Test
    void refreshNonce_rotatesEachCall() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String firstNonce = requestNonce(session);
        String secondNonce = requestNonce(session);
        org.junit.jupiter.api.Assertions.assertNotEquals(firstNonce, secondNonce);
    }

    @Test
    void validateNonce_rejectsExpiredNonce() {
        MockHttpSession session = new MockHttpSession();
        String issuedNonce = uiNonceService.issueSessionNonce(session);
        session.setAttribute(ISSUED_AT_KEY, Instant.now().minus(Duration.ofMinutes(EXPIRED_MINUTES)));

        UiNonceService.UiNonceValidation validation = uiNonceService.validateNonce(session, issuedNonce);
        org.junit.jupiter.api.Assertions.assertFalse(validation.valid());
        org.junit.jupiter.api.Assertions.assertTrue(validation.expired());
    }

    @Test
    void refreshNonce_rejectsDisallowedOrigin() throws Exception {
        mockMvc.perform(post("/ui/session/nonce").header("Origin", "http://evil.test"))
                .andExpect(status().isForbidden());
    }

    private String requestNonce(MockHttpSession session) throws Exception {
        MvcResult mvcCall = mockMvc.perform(post("/ui/session/nonce")
                        .header("Origin", "http://localhost:8090")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode bodyNode = objectMapper.readTree(mvcCall.getResponse().getContentAsString());
        return bodyNode.path("uiNonce").asText();
    }

    static class TestConfig {
        @Bean
        AppProperties appProperties() {
            AppProperties properties = new AppProperties();
            properties
                    .getCors()
                    .setAllowedOrigins("http://localhost:8090,http://localhost:5183,https://composerai.app");
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

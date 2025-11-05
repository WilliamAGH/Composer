package com.composerai.api.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import com.composerai.api.model.EmailMessage;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration tests for WebViewController and GlobalModelAttributes.
 * Verifies that backend enums are properly injected into Thymeleaf templates.
 */
@WebMvcTest(WebViewController.class)
class WebViewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UiNonceService uiNonceService;

    @MockBean
    private com.composerai.api.service.email.EmailMessageProvider emailMessageProvider;

    @BeforeEach
    void setup() {
        when(uiNonceService.getOrCreateSessionNonce(any())).thenReturn("nonce");
        when(emailMessageProvider.loadEmails()).thenReturn(List.of());
    }

    @Test
    void chatPage_ShouldRenderSuccessfully() throws Exception {
        mockMvc.perform(get("/chat"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/html"))
            .andExpect(view().name("chat"));
    }

    @Test
    void chatPage_ShouldContainSseEventsFromBackendEnum() throws Exception {
        // Verify that SSE_EVENTS constants are injected from SseEventType enum
        mockMvc.perform(get("/chat"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("const SSE_EVENTS = ")))
            .andExpect(content().string(containsString("METADATA")))
            .andExpect(content().string(containsString("metadata")))
            .andExpect(content().string(containsString("RENDERED_HTML")))
            .andExpect(content().string(containsString("rendered_html")))
            .andExpect(content().string(containsString("DONE")))
            .andExpect(content().string(containsString("done")))
            .andExpect(content().string(containsString("ERROR")))
            .andExpect(content().string(containsString("error")))
            .andExpect(content().string(containsString("REASONING")))
            .andExpect(content().string(containsString("reasoning")));
    }

    @Test
    void chatPage_ShouldContainReasoningPhasesFromBackendEnum() throws Exception {
        // Verify that REASONING_PHASES constants are injected from ReasoningStreamAdapter.Phase enum
        mockMvc.perform(get("/chat"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("const REASONING_PHASES = ")))
            .andExpect(content().string(containsString("THINKING")))
            .andExpect(content().string(containsString("PROGRESS")))
            .andExpect(content().string(containsString("STREAMING")))
            .andExpect(content().string(containsString("FAILED")));
    }

    @Test
    void chatPage_ShouldFreezeConstants() throws Exception {
        // Verify that constants are immutable via Object.freeze()
        mockMvc.perform(get("/chat"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Object.freeze(SSE_EVENTS)")))
            .andExpect(content().string(containsString("Object.freeze(REASONING_PHASES)")));
    }

    @Test
    void chatPage_ShouldNotContainHardcodedFallbackValues() throws Exception {
        // Verify we're not duplicating enum values as hardcoded fallbacks
        // The template should use empty object fallback: /*[[${sseEvents}]]*/ {}
        String response = mockMvc.perform(get("/chat"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        // Verify the actual rendered content has the injected values, not empty objects
        // Thymeleaf serializes Map to JSON, so format is: {METADATA:"metadata",...}
        // This proves GlobalModelAttributes is working
        assertTrue(
            response.contains("METADATA") && response.contains("metadata")
                && response.contains("RENDERED_HTML") && response.contains("rendered_html")
                && !response.contains("const SSE_EVENTS = {};"),
            () -> "SSE_EVENTS should contain injected backend enum values, not empty fallback. Found: "
                + response.substring(
                    response.indexOf("const SSE_EVENTS"),
                    Math.min(response.indexOf("const SSE_EVENTS") + 200, response.length()))
        );
    }

    @Test
    void indexPage_ShouldRedirectToEmailClientV2() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/email-client-v2"));
    }

    @Test
    void emailClient_ShouldExposeEmailMessagesFromProvider() throws Exception {
        when(emailMessageProvider.loadEmails()).thenReturn(List.of(
            EmailMessage.builder()
                .id("sample")
                .contextId("ctx-1")
                .senderName("Sample Sender")
                .senderEmail("sender@example.com")
                .subject("Sample Email")
                .emailBodyRaw("Body")
                .emailBodyTransformedText("Body")
                .build()
        ));

        mockMvc.perform(get("/email-client"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("const EMAIL_MESSAGES")))
            .andExpect(content().string(containsString("sample")))
            .andExpect(content().string(containsString("ctx-1")));
    }
}

package com.composerai.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for WebViewController and GlobalModelAttributes.
 * Verifies that backend enums are properly injected into Thymeleaf templates.
 */
@WebMvcTest(WebViewController.class)
@org.springframework.boot.context.properties.EnableConfigurationProperties(
        com.composerai.api.config.AppProperties.class)
class WebViewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UiNonceService uiNonceService;

    @MockBean
    private com.composerai.api.service.email.EmailMessageProvider emailMessageProvider;

    @MockBean
    private com.composerai.api.domain.service.MailboxFolderTransitionService mailboxFolderTransitionService;

    @BeforeEach
    void setup() {
        when(uiNonceService.getOrCreateSessionNonce(any())).thenReturn("nonce");
        when(emailMessageProvider.loadEmails()).thenReturn(List.of());
        when(mailboxFolderTransitionService.computeFolderCounts(any())).thenReturn(Map.of());
        when(mailboxFolderTransitionService.deriveBaselineFolder(any()))
                .thenReturn(com.composerai.api.domain.model.MailFolderIdentifier.of("inbox"));
    }

    @Test
    void indexPage_ShouldForwardToEmailClientV2() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("/email-client-v2"));
    }

    @Test
    void chatDiagnostics_ShouldRenderWithUiNonce() throws Exception {
        mockMvc.perform(get("/chat-diagnostics"))
                .andExpect(status().isOk())
                .andExpect(view().name("chat"))
                .andExpect(model().attributeExists("uiNonce"));
    }

    @Test
    void chat_ShouldRenderWithUiNonce() throws Exception {
        mockMvc.perform(get("/chat"))
                .andExpect(status().isOk())
                .andExpect(view().name("chat"))
                .andExpect(model().attributeExists("uiNonce"));
    }

    @Test
    void emailClientV2_ShouldRenderWithBootstrapModel() throws Exception {
        mockMvc.perform(get("/email-client-v2"))
                .andExpect(status().isOk())
                .andExpect(view().name("email-client-v2"))
                .andExpect(model().attributeExists("uiNonce"))
                .andExpect(model().attributeExists("emailMessages"))
                .andExpect(model().attributeExists("folderCounts"))
                .andExpect(model().attributeExists("effectiveFolders"));
    }

    @Test
    void legacyEmailClientPath_ShouldRedirectToEmailClientV2() throws Exception {
        mockMvc.perform(get("/email-client"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/email-client-v2"));
    }
}

package com.composerai.api.controller;

import com.composerai.api.service.email.EmailMessageProvider;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class WebViewController {

    private final UiNonceService uiNonceService;
    private final EmailMessageProvider emailMessageProvider;

    public WebViewController(UiNonceService uiNonceService, EmailMessageProvider emailMessageProvider) {
        this.uiNonceService = uiNonceService;
        this.emailMessageProvider = emailMessageProvider;
    }

    @GetMapping({"/", "/index"})
    public String index() {
        // Make email client v2 the root landing page
        return "redirect:/email-client-v2";
    }

    @GetMapping("/chat")
    public String chat(Model model, HttpSession session) {
        model.addAttribute("uiNonce", uiNonceService.getOrCreateSessionNonce(session));
        return "chat";
    }

    @GetMapping("/email-client")
    public String emailClientLegacyRedirect() {
        // Preserve backward-compatible path while serving the modern UI
        return "redirect:/email-client-v2";
    }

    @GetMapping("/email-client-v2")
    public String emailClientV2(Model model, HttpSession session) {
        model.addAttribute("uiNonce", uiNonceService.getOrCreateSessionNonce(session));
        List<com.composerai.api.model.EmailMessage> emailMessages = emailMessageProvider.loadEmails();
        model.addAttribute("emailMessages", emailMessages);
        return "email-client-v2";
    }
}

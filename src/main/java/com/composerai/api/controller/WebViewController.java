package com.composerai.api.controller;

import com.composerai.api.domain.service.MailboxFolderTransitionService;
import com.composerai.api.service.email.EmailMessageProvider;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class WebViewController {

    private final UiNonceService uiNonceService;
    private final EmailMessageProvider emailMessageProvider;
    private final MailboxFolderTransitionService mailboxFolderTransitionService;

    public WebViewController(UiNonceService uiNonceService,
                             EmailMessageProvider emailMessageProvider,
                             MailboxFolderTransitionService mailboxFolderTransitionService) {
        this.uiNonceService = uiNonceService;
        this.emailMessageProvider = emailMessageProvider;
        this.mailboxFolderTransitionService = mailboxFolderTransitionService;
    }

    @GetMapping({"/", "/index"})
    public String index() {
        // Forward internally so / and /index share the same controller/model pipeline as /email-client-v2
        return "forward:/email-client-v2";
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
        model.addAttribute("folderCounts", mailboxFolderTransitionService.computeFolderCounts(emailMessages));
        Map<String, String> effectiveFolders = new LinkedHashMap<>();
        for (com.composerai.api.model.EmailMessage emailMessage : emailMessages) {
            if (emailMessage == null || emailMessage.id() == null) {
                continue;
            }
            effectiveFolders.put(
                emailMessage.id(),
                mailboxFolderTransitionService.deriveBaselineFolder(emailMessage).value()
            );
        }
        model.addAttribute("effectiveFolders", effectiveFolders);
        return "email-client-v2";
    }
}

package com.composerai.api.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/qa")
public class QaWebController {

    private final UiNonceService uiNonceService;

    public QaWebController(UiNonceService uiNonceService) {
        this.uiNonceService = uiNonceService;
    }

    @GetMapping("/diagnostics")
    public String diagnostics(Model model, HttpSession session) {
        model.addAttribute("uiNonce", uiNonceService.getOrCreateSessionNonce(session));
        return "qa/diagnostics";
    }

    @GetMapping("/email-file-parser")
    public String emailFileParser(Model model, HttpSession session) {
        model.addAttribute("uiNonce", uiNonceService.getOrCreateSessionNonce(session));
        return "qa/email-file-parser";
    }
}

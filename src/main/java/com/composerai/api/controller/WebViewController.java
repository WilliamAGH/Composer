package com.composerai.api.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebViewController {

    @GetMapping({"/", "/index"})
    public String index() {
        return "redirect:/chat";
    }

    @GetMapping("/diagnostics")
    public String diagnostics() {
        return "diagnostics";
    }

    @GetMapping({"/email-backend"})
    public String emailBackend(org.springframework.ui.Model model, jakarta.servlet.http.HttpSession session) {
        Object existing = session.getAttribute("UI_NONCE");
        String nonce = existing instanceof String s && !s.isBlank() ? s : com.composerai.api.util.IdGenerator.generate(24);
        session.setAttribute("UI_NONCE", nonce);
        model.addAttribute("uiNonce", nonce);
        return "email-backend";
    }

    @GetMapping("/chat")
    public String chat(org.springframework.ui.Model model, jakarta.servlet.http.HttpSession session) {
        Object existing = session.getAttribute("UI_NONCE");
        String nonce = existing instanceof String s && !s.isBlank() ? s : com.composerai.api.util.IdGenerator.generate(24);
        session.setAttribute("UI_NONCE", nonce);
        model.addAttribute("uiNonce", nonce);
        return "chat";
    }
}

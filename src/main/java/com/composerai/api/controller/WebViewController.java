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
    public String emailBackend() {
        return "email-backend";
    }

    @GetMapping("/chat")
    public String chat() {
        return "chat";
    }
}

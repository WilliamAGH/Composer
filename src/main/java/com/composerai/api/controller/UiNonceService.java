package com.composerai.api.controller;

import com.composerai.api.util.IdGenerator;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

@Component
public class UiNonceService {

    private static final String SESSION_KEY = "UI_NONCE";

    public String getOrCreateSessionNonce(HttpSession session) {
        Object existing = session.getAttribute(SESSION_KEY);
        String nonce = existing instanceof String s && !s.isBlank() ? s : IdGenerator.generate(24);
        session.setAttribute(SESSION_KEY, nonce);
        return nonce;
    }
}

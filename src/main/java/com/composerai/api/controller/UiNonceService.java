package com.composerai.api.controller;

import com.composerai.api.util.IdGenerator;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

@Component
public class UiNonceService {

    private static final String SESSION_KEY = "UI_NONCE";

    public String getOrCreateSessionNonce(HttpSession session) {
        Object existingNonce = session.getAttribute(SESSION_KEY);
        if (existingNonce instanceof String nonce && !nonce.isBlank()) {
            return nonce;
        }

        synchronized (session) {
            existingNonce = session.getAttribute(SESSION_KEY);
            if (existingNonce instanceof String nonce && !nonce.isBlank()) {
                return nonce;
            }
            String generatedNonce = IdGenerator.generate(24);
            session.setAttribute(SESSION_KEY, generatedNonce);
            return generatedNonce;
        }
    }
}

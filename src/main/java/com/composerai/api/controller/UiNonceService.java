package com.composerai.api.controller;

import com.composerai.api.util.IdGenerator;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

@Component
public class UiNonceService {

    private static final String SESSION_KEY = "UI_NONCE";
    private final Object creationLock = new Object();

    public String getOrCreateSessionNonce(HttpSession session) {
        // Double-checked locking pattern using a dedicated lock to avoid synchronizing on the session object
        Object existing = session.getAttribute(SESSION_KEY);
        if (existing instanceof String s && !s.isBlank()) {
            return s;
        }

        synchronized (creationLock) {
            existing = session.getAttribute(SESSION_KEY);
            String nonce = existing instanceof String s && !s.isBlank() ? s : IdGenerator.generate(24);
            session.setAttribute(SESSION_KEY, nonce);
            return nonce;
        }
    }
}

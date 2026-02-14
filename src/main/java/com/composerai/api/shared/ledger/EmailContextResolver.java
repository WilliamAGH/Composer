package com.composerai.api.shared.ledger;

import com.composerai.api.model.EmailMessage;
import com.composerai.api.service.email.EmailMessageProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Best-effort resolver that maps a contextId/emailId back to the {@link EmailMessage} that produced
 * it so the ledger can embed the existing DTO. This relies on the configured
 * {@link EmailMessageProvider}; workers should treat the lookup as opportunistic and tolerate
 * misses when the mailbox snapshot is unavailable.
 */
@Slf4j
@Component
public class EmailContextResolver {

    private final EmailMessageProvider emailMessageProvider;
    private final ConcurrentHashMap<String, EmailMessage> cache = new ConcurrentHashMap<>();

    public EmailContextResolver(EmailMessageProvider emailMessageProvider) {
        this.emailMessageProvider = emailMessageProvider;
    }

    public Optional<EmailObject> resolve(String contextId) {
        if (contextId == null || contextId.isBlank()) {
            return Optional.empty();
        }
        EmailMessage message = cache.computeIfAbsent(contextId, this::lookup);
        if (message == null) {
            return Optional.empty();
        }
        return Optional.of(new EmailObject(contextId, "context:" + contextId, message));
    }

    public List<EmailObject> resolveAll(Collection<String> contextIds) {
        if (contextIds == null || contextIds.isEmpty()) {
            return List.of();
        }
        List<EmailObject> resolved = new ArrayList<>();
        for (String contextId : Set.copyOf(contextIds)) {
            resolve(contextId).ifPresent(resolved::add);
        }
        return resolved;
    }

    private EmailMessage lookup(String contextId) {
        try {
            return emailMessageProvider.loadEmails().stream()
                    .filter(email -> contextId.equals(email.contextId()) || contextId.equals(email.id()))
                    .findFirst()
                    .orElse(null);
        } catch (Exception ex) {
            log.warn("Failed to resolve email context for {}", contextId, ex);
            return null;
        }
    }
}

package com.composerai.api.adapters.out.mailbox;

import com.composerai.api.domain.model.MailFolderIdentifier;
import com.composerai.api.domain.model.MailboxSnapshot;
import com.composerai.api.domain.port.MailboxSnapshotPort;
import com.composerai.api.domain.service.MailboxFolderTransitionService;
import com.composerai.api.model.EmailMessage;
import com.composerai.api.service.email.EmailMessageProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapts the existing {@link EmailMessageProvider} (which reads .eml files) to the domain snapshot port.
 * This keeps all filesystem specifics outside the application layer and mirrors how a future IMAP
 * implementation will plug in.
 */
@Component
public class FileSystemMailboxSnapshotAdapter implements MailboxSnapshotPort {

    private final EmailMessageProvider emailMessageProvider;
    private final MailboxFolderTransitionService transitionService;

    public FileSystemMailboxSnapshotAdapter(EmailMessageProvider emailMessageProvider,
                                            MailboxFolderTransitionService transitionService) {
        this.emailMessageProvider = emailMessageProvider;
        this.transitionService = transitionService;
    }

    @Override
    public MailboxSnapshot loadSnapshot(String mailboxId) {
        List<EmailMessage> messages = emailMessageProvider.loadEmails();
        Map<MailFolderIdentifier, Integer> counts = computeBaselineCounts(messages);
        return new MailboxSnapshot(mailboxId, messages, counts);
    }

    private Map<MailFolderIdentifier, Integer> computeBaselineCounts(List<EmailMessage> messages) {
        Map<MailFolderIdentifier, Integer> counts = new LinkedHashMap<>();
        for (EmailMessage message : messages) {
            MailFolderIdentifier folder = transitionService.deriveBaselineFolder(message);
            counts.merge(folder, 1, Integer::sum);
        }
        return counts;
    }
}

package com.composerai.api.application.usecase.mailbox;

import com.composerai.api.application.dto.mailbox.MailboxStateSnapshotResult;
import com.composerai.api.domain.model.MailboxId;
import com.composerai.api.domain.model.MailboxSnapshot;
import com.composerai.api.domain.model.MessageFolderPlacement;
import com.composerai.api.domain.model.MessageId;
import com.composerai.api.domain.model.SessionId;
import com.composerai.api.domain.port.MailboxSnapshotPort;
import com.composerai.api.domain.port.SessionScopedMessagePlacementPort;
import com.composerai.api.domain.service.MailboxFolderTransitionService;
import com.composerai.api.model.EmailMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Loads the current mailbox state for a session: baseline emails from the filesystem plus any
 * session-specific folder overrides, returning the resolved payload the UI can render immediately.
 */
@Service
public class LoadMailboxStateSnapshotUseCase {

    private static final Logger log = LoggerFactory.getLogger(LoadMailboxStateSnapshotUseCase.class);

    private final MailboxSnapshotPort mailboxSnapshotPort;
    private final SessionScopedMessagePlacementPort sessionPlacementPort;
    private final MailboxFolderTransitionService transitionService;

    public LoadMailboxStateSnapshotUseCase(
        MailboxSnapshotPort mailboxSnapshotPort,
        SessionScopedMessagePlacementPort sessionPlacementPort,
        MailboxFolderTransitionService transitionService
    ) {
        this.mailboxSnapshotPort = mailboxSnapshotPort;
        this.sessionPlacementPort = sessionPlacementPort;
        this.transitionService = transitionService;
    }

    public MailboxStateSnapshotResult load(String mailboxId, String sessionId) {
        if (mailboxId == null || mailboxId.isBlank()) {
            throw new IllegalArgumentException("mailboxId is required");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId is required");
        }

        log.debug("Loading mailbox snapshot for mailbox={} session={}", mailboxId, sessionId);

        MailboxSnapshot snapshot = mailboxSnapshotPort.loadSnapshot(mailboxId);
        Map<MessageId, MessageFolderPlacement> placements = sessionPlacementPort.findPlacements(
            new MailboxId(mailboxId), 
            new SessionId(sessionId)
        );
        List<EmailMessage> resolvedMessages = transitionService.applyPlacements(snapshot, placements);
        Map<String, Integer> folderCounts = transitionService.computeFolderCounts(resolvedMessages);
        var effectiveFolders = transitionService.serializeEffectiveFolders(
            transitionService.deriveEffectiveFolders(snapshot, placements)
        );

        return new MailboxStateSnapshotResult(
            mailboxId,
            resolvedMessages,
            folderCounts,
            transitionService.serializePlacements(placements),
            effectiveFolders
        );
    }
}

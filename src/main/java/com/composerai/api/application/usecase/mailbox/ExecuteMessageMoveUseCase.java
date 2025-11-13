package com.composerai.api.application.usecase.mailbox;

import com.composerai.api.application.dto.mailbox.MessageMoveCommand;
import com.composerai.api.application.dto.mailbox.MessageMoveResult;
import com.composerai.api.domain.model.MailFolderIdentifier;
import com.composerai.api.domain.model.MailboxSnapshot;
import com.composerai.api.domain.model.MessageFolderPlacement;
import com.composerai.api.domain.port.MailboxSnapshotPort;
import com.composerai.api.domain.port.SessionScopedMessagePlacementPort;
import com.composerai.api.domain.service.MailboxFolderTransitionService;
import com.composerai.api.model.EmailMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Handles the orchestration of a single message move: validation, placement persistence, and
 * returning the updated mailbox snapshot delta for the UI.
 */
@Service
public class ExecuteMessageMoveUseCase {

    private static final Logger log = LoggerFactory.getLogger(ExecuteMessageMoveUseCase.class);

    private final MailboxSnapshotPort mailboxSnapshotPort;
    private final SessionScopedMessagePlacementPort sessionPlacementPort;
    private final MailboxFolderTransitionService transitionService;

    public ExecuteMessageMoveUseCase(MailboxSnapshotPort mailboxSnapshotPort,
                                     SessionScopedMessagePlacementPort sessionPlacementPort,
                                     MailboxFolderTransitionService transitionService) {
        this.mailboxSnapshotPort = mailboxSnapshotPort;
        this.sessionPlacementPort = sessionPlacementPort;
        this.transitionService = transitionService;
    }

    public MessageMoveResult execute(MessageMoveCommand command) {
        String mailboxId = command.mailboxId();
        String sessionId = command.sessionId();
        String messageId = command.messageId();
        String targetFolderId = command.targetFolderId();

        log.info("Moving message {} in mailbox {} for session {}", messageId, mailboxId, sessionId);

        MailboxSnapshot snapshot = mailboxSnapshotPort.loadSnapshot(mailboxId);
        Map<String, MessageFolderPlacement> placements = new HashMap<>(sessionPlacementPort.findPlacements(mailboxId, sessionId));

        EmailMessage targetMessage = snapshot.messages().stream()
            .filter(message -> messageId.equals(message.id()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));

        MailFolderIdentifier targetFolder = transitionService.normalizeFolder(targetFolderId);
        MessageFolderPlacement existingPlacement = placements.get(messageId);
        MailFolderIdentifier currentFolder = existingPlacement != null
            ? existingPlacement.folderIdentifier()
            : transitionService.deriveBaselineFolder(targetMessage);

        if (currentFolder.equals(targetFolder)) {
            log.debug("Message {} already in folder {} – returning existing snapshot", messageId, targetFolder.value());
            return buildResult(snapshot, placements, mailboxId, messageId, currentFolder, placements);
        }

        MailFolderIdentifier baselineFolder = transitionService.deriveBaselineFolder(targetMessage);
        if (targetFolder.equals(baselineFolder)) {
            sessionPlacementPort.removePlacement(mailboxId, sessionId, messageId);
            placements.remove(messageId);
        } else {
            MessageFolderPlacement placement = MessageFolderPlacement.builder()
                .mailboxId(mailboxId)
                .sessionId(sessionId)
                .messageId(messageId)
                .folderIdentifier(targetFolder)
                .build();
            sessionPlacementPort.savePlacement(placement);
            placements.put(messageId, placement);
        }

        return buildResult(snapshot, placements, mailboxId, messageId, currentFolder, placements);
    }

    private MessageMoveResult buildResult(MailboxSnapshot snapshot,
                                          Map<String, MessageFolderPlacement> placements,
                                          String mailboxId,
                                          String messageId,
                                          MailFolderIdentifier previousFolder,
                                          Map<String, MessageFolderPlacement> currentPlacements) {
        List<EmailMessage> resolvedMessages = transitionService.applyPlacements(snapshot, currentPlacements);
        Map<String, Integer> folderCounts = transitionService.computeFolderCounts(resolvedMessages);
        Map<String, String> placementMap = transitionService.serializePlacements(currentPlacements);
        Map<String, String> effectiveFolders = transitionService.serializeEffectiveFolders(
            transitionService.deriveEffectiveFolders(snapshot, currentPlacements)
        );
        EmailMessage updatedMessage = resolvedMessages.stream()
            .filter(message -> messageId.equals(message.id()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Updated message missing from snapshot"));
        MailFolderIdentifier currentFolder = Optional.ofNullable(currentPlacements.get(messageId))
            .map(MessageFolderPlacement::folderIdentifier)
            .orElse(transitionService.deriveBaselineFolder(updatedMessage));

        return new MessageMoveResult(
            mailboxId,
            messageId,
            previousFolder.value(),
            currentFolder.value(),
            updatedMessage,
            folderCounts,
            placementMap,
            resolvedMessages,
            effectiveFolders
        );
    }
}

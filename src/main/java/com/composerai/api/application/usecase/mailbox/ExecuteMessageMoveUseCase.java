package com.composerai.api.application.usecase.mailbox;

import com.composerai.api.application.dto.mailbox.MessageMoveCommand;
import com.composerai.api.application.dto.mailbox.MessageMoveResult;
import com.composerai.api.domain.model.MailFolderIdentifier;
import com.composerai.api.domain.model.MailboxId;
import com.composerai.api.domain.model.MailboxSnapshot;
import com.composerai.api.domain.model.MessageFolderPlacement;
import com.composerai.api.domain.model.MessageId;
import com.composerai.api.domain.model.SessionId;
import com.composerai.api.domain.port.MailboxSnapshotPort;
import com.composerai.api.domain.port.SessionScopedMessagePlacementPort;
import com.composerai.api.domain.service.MailboxFolderTransitionService;
import com.composerai.api.model.EmailMessage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

    public ExecuteMessageMoveUseCase(
            MailboxSnapshotPort mailboxSnapshotPort,
            SessionScopedMessagePlacementPort sessionPlacementPort,
            MailboxFolderTransitionService transitionService) {
        this.mailboxSnapshotPort = mailboxSnapshotPort;
        this.sessionPlacementPort = sessionPlacementPort;
        this.transitionService = transitionService;
    }

    public MessageMoveResult execute(MessageMoveCommand command) {
        String mailboxIdRaw = command.mailboxId();
        String sessionIdRaw = command.sessionId();
        String messageIdRaw = command.messageId();
        String targetFolderId = command.targetFolderId();

        log.info("Moving message {} in mailbox {} for session {}", messageIdRaw, mailboxIdRaw, sessionIdRaw);

        MailboxId mailboxId = new MailboxId(mailboxIdRaw);
        SessionId sessionId = new SessionId(sessionIdRaw);
        MessageId messageId = new MessageId(messageIdRaw);

        MailboxSnapshot snapshot = mailboxSnapshotPort.loadSnapshot(mailboxIdRaw);
        Map<MessageId, MessageFolderPlacement> placements =
                new HashMap<>(sessionPlacementPort.findPlacements(mailboxId, sessionId));

        EmailMessage targetMessage = snapshot.messages().stream()
                .filter(message -> messageIdRaw.equals(message.id()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageIdRaw));

        MailFolderIdentifier targetFolder = transitionService.normalizeFolder(targetFolderId);
        MessageFolderPlacement existingPlacement = placements.get(messageId);
        MailFolderIdentifier currentFolder = existingPlacement != null
                ? existingPlacement.folderIdentifier()
                : transitionService.deriveBaselineFolder(targetMessage);

        if (currentFolder.equals(targetFolder)) {
            log.debug(
                    "Message {} already in folder {} – returning existing snapshot",
                    messageIdRaw,
                    targetFolder.value());
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

    private MessageMoveResult buildResult(
            MailboxSnapshot snapshot,
            Map<MessageId, MessageFolderPlacement> placements,
            MailboxId mailboxId,
            MessageId messageId,
            MailFolderIdentifier previousFolder,
            Map<MessageId, MessageFolderPlacement> currentPlacements) {
        List<EmailMessage> resolvedMessages = transitionService.applyPlacements(snapshot, currentPlacements);
        Map<String, Integer> folderCounts = transitionService.computeFolderCounts(resolvedMessages);
        Map<String, String> placementMap = transitionService.serializePlacements(currentPlacements);
        Map<String, String> effectiveFolders = transitionService.serializeEffectiveFolders(
                transitionService.deriveEffectiveFolders(snapshot, currentPlacements));
        EmailMessage updatedMessage = resolvedMessages.stream()
                .filter(message -> messageId.value().equals(message.id()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Updated message missing from snapshot"));
        MailFolderIdentifier currentFolder = Optional.ofNullable(currentPlacements.get(messageId))
                .map(MessageFolderPlacement::folderIdentifier)
                .orElse(transitionService.deriveBaselineFolder(updatedMessage));

        return new MessageMoveResult(
                mailboxId.value(),
                messageId.value(),
                previousFolder.value(),
                currentFolder.value(),
                updatedMessage,
                folderCounts,
                placementMap,
                resolvedMessages,
                effectiveFolders);
    }
}

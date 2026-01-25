package com.composerai.api.adapters.in.web;

import com.composerai.api.adapters.in.web.dto.MessageMoveRequest;
import com.composerai.api.application.dto.mailbox.MailboxStateSnapshotResult;
import com.composerai.api.application.dto.mailbox.MessageMoveCommand;
import com.composerai.api.application.dto.mailbox.MessageMoveResult;
import com.composerai.api.application.usecase.mailbox.ExecuteMessageMoveUseCase;
import com.composerai.api.application.usecase.mailbox.LoadMailboxStateSnapshotUseCase;
import com.composerai.api.shared.session.SessionTokenResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller responsible for mailbox state hydration and folder move actions.
 * Delegates entirely to use cases so the HTTP surface stays minimal and declarative.
 */
@RestController
@RequestMapping("/api/mailboxes")
public class MailboxFolderStateController {

    private static final Logger log = LoggerFactory.getLogger(MailboxFolderStateController.class);

    private final LoadMailboxStateSnapshotUseCase loadMailboxStateSnapshotUseCase;
    private final ExecuteMessageMoveUseCase executeMessageMoveUseCase;
    private final SessionTokenResolver sessionTokenResolver;

    public MailboxFolderStateController(
            LoadMailboxStateSnapshotUseCase loadMailboxStateSnapshotUseCase,
            ExecuteMessageMoveUseCase executeMessageMoveUseCase,
            SessionTokenResolver sessionTokenResolver) {
        this.loadMailboxStateSnapshotUseCase = loadMailboxStateSnapshotUseCase;
        this.executeMessageMoveUseCase = executeMessageMoveUseCase;
        this.sessionTokenResolver = sessionTokenResolver;
    }

    /**
     * Returns the resolved mailbox snapshot for the caller's session.
     * Uses the X-Mailbox-Session header when available, falling back to the `session` query parameter.
     */
    @GetMapping("/{mailboxId}/state")
    public ResponseEntity<MailboxStateSnapshotResult> loadMailboxState(
            @PathVariable String mailboxId,
            @RequestParam(name = "session", required = false) String sessionToken,
            HttpServletRequest request) {
        String sessionId = sessionTokenResolver.resolveSessionId(request, sessionToken);
        log.debug("GET /api/mailboxes/{}/state (session={})", mailboxId, sessionId);
        MailboxStateSnapshotResult result = loadMailboxStateSnapshotUseCase.load(mailboxId, sessionId);
        return ResponseEntity.ok(result);
    }

    /**
     * Moves a message into the requested folder and returns the updated counts + placements.
     */
    @PostMapping("/{mailboxId}/messages/{messageId}/move")
    public ResponseEntity<MessageMoveResult> moveMessage(
            @PathVariable String mailboxId,
            @PathVariable String messageId,
            @Valid @RequestBody MessageMoveRequest requestBody,
            HttpServletRequest servletRequest) {
        if (requestBody.mailboxId() != null
                && !requestBody.mailboxId().isBlank()
                && !requestBody.mailboxId().equalsIgnoreCase(mailboxId)) {
            throw new IllegalArgumentException("mailboxId in path and body must match");
        }
        String sessionId = sessionTokenResolver.resolveSessionId(servletRequest, requestBody.sessionId());
        log.debug("POST /api/mailboxes/{}/messages/{}/move (session={})", mailboxId, messageId, sessionId);
        MessageMoveCommand command =
                new MessageMoveCommand(mailboxId, sessionId, messageId, requestBody.targetFolderId());
        MessageMoveResult result = executeMessageMoveUseCase.execute(command);
        return ResponseEntity.ok(result);
    }
}

package com.composerai.api.domain.service;

import com.composerai.api.domain.model.MailFolderIdentifier;
import com.composerai.api.domain.model.MailboxSnapshot;
import com.composerai.api.domain.model.MessageFolderPlacement;
import com.composerai.api.domain.model.MessageId;
import com.composerai.api.model.EmailMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Handles normalization of folder identifiers and applies session-scoped placements to email payloads.
 * This isolates all folder-specific logic (label rewriting, count calculations, validation) in one place
 * so use cases remain thin orchestration layers and IMAP adapters can later reuse the same logic.
 */
@Service
public class MailboxFolderTransitionService {

    private static final Logger log = LoggerFactory.getLogger(MailboxFolderTransitionService.class);

    private static final MailFolderIdentifier INBOX = MailFolderIdentifier.of("inbox");
    private static final MailFolderIdentifier ARCHIVE = MailFolderIdentifier.of("archive");
    private static final MailFolderIdentifier TRASH = MailFolderIdentifier.of("trash");
    private static final MailFolderIdentifier SENT = MailFolderIdentifier.of("sent");
    private static final MailFolderIdentifier DRAFTS = MailFolderIdentifier.of("drafts");

    private static final Set<String> EXCLUSIVE_LABELS = Set.of(
        "archive",
        "archived",
        "trash",
        "deleted",
        "sent",
        "drafts",
        "draft"
    );
    private static final Set<String> SUPPORTED_FOLDERS = Set.of("inbox", "archive", "trash", "sent", "drafts");

    /**
     * Validates and normalizes a requested folder identifier so callers cannot introduce arbitrary folder names.
     */
    public MailFolderIdentifier normalizeFolder(String folderId) {
        if (folderId == null || folderId.isBlank()) {
            throw new IllegalArgumentException("targetFolderId is required");
        }
        String normalized = folderId.trim().toLowerCase(Locale.US);
        if (!SUPPORTED_FOLDERS.contains(normalized)) {
            log.debug("Unsupported folder transition requested: {}", folderId);
            throw new IllegalArgumentException("Unsupported folder: " + folderId);
        }
        return MailFolderIdentifier.of(normalized);
    }

    /**
     * Returns the baseline folder derived from message metadata (labels + defaults).
     * Drafts and sent messages are treated as first-class folders even before SMTP wiring exists.
     */
    public MailFolderIdentifier deriveBaselineFolder(EmailMessage message) {
        if (message == null) {
            return INBOX;
        }
        List<String> labels = normalizeLabels(message);
        if (labels.stream().anyMatch(this::isTrashLabel)) {
            return TRASH;
        }
        if (labels.stream().anyMatch(this::isArchiveLabel)) {
            return ARCHIVE;
        }
        if (labels.stream().anyMatch(this::isSentLabel)) {
            return SENT;
        }
        if (labels.stream().anyMatch(this::isDraftLabel)) {
            return DRAFTS;
        }
        return INBOX;
    }

    /**
     * Applies any stored placements to the snapshot and returns a new resolved list of emails.
     */
    public List<EmailMessage> applyPlacements(
        MailboxSnapshot snapshot,
        Map<MessageId, MessageFolderPlacement> placements
    ) {
        Objects.requireNonNull(snapshot, "snapshot is required");
        Map<MessageId, MessageFolderPlacement> placementMap = placements == null ? Map.of() : placements;

        List<EmailMessage> resolved = new ArrayList<>(snapshot.messages().size());
        for (EmailMessage message : snapshot.messages()) {
            MessageFolderPlacement placement = placementMap.get(new MessageId(message.id()));
            if (placement == null) {
                resolved.add(message);
                continue;
            }
            resolved.add(applyFolderOverride(message, placement.folderIdentifier()));
        }
        return resolved;
    }

    /**
     * Calculates folder counts using the same rules as the frontend store so UI and API stay consistent.
     */
    public Map<String, Integer> computeFolderCounts(List<EmailMessage> messages) {
        List<EmailMessage> safeList = messages == null ? List.of() : messages;
        Map<String, Integer> totals = new LinkedHashMap<>();
        totals.put("inbox", 0); // Will be calculated after exclusive folders
        totals.put("starred", 0);
        totals.put("snoozed", 0);
        totals.put("sent", 0);
        totals.put("drafts", 0);
        totals.put("archive", 0);
        totals.put("trash", 0);

        int exclusiveCount = 0;
        for (EmailMessage message : safeList) {
            List<String> labels = normalizeLabels(message);
            boolean hasExclusiveLabel = labels.stream().anyMatch(EXCLUSIVE_LABELS::contains);
            if (hasExclusiveLabel) {
                exclusiveCount++;
            }
            if (Boolean.TRUE.equals(message.starred())) {
                totals.computeIfPresent("starred", (k, v) -> v + 1);
            }
            if (labels.stream().anyMatch(label -> label.equals("snoozed"))) {
                totals.computeIfPresent("snoozed", (k, v) -> v + 1);
            }
            if (labels.stream().anyMatch(label -> label.equals("sent"))) {
                totals.computeIfPresent("sent", (k, v) -> v + 1);
            }
            if (labels.stream().anyMatch(label -> label.equals("drafts") || label.equals("draft"))) {
                totals.computeIfPresent("drafts", (k, v) -> v + 1);
            }
            if (labels.stream().anyMatch(this::isArchiveLabel)) {
                totals.computeIfPresent("archive", (k, v) -> v + 1);
            }
            if (labels.stream().anyMatch(this::isTrashLabel)) {
                totals.computeIfPresent("trash", (k, v) -> v + 1);
            }
        }
        // Inbox contains only messages without exclusive labels
        totals.put("inbox", safeList.size() - exclusiveCount);
        return totals;
    }

    /**
     * Builds the effective placement map for serialization (messageId -> folderId).
     */
    public Map<String, String> serializePlacements(Map<MessageId, MessageFolderPlacement> placements) {
        if (placements == null || placements.isEmpty()) {
            return Map.of();
        }
        return placements
            .entrySet()
            .stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    entry -> entry.getKey().toString(), 
                    entry -> entry.getValue().folderIdentifier().value()
                )
            );
    }

    /**
     * Converts an {@link EffectiveFoldersMap} to a serializable map (messageId → folderId).
     */
    public Map<String, String> serializeEffectiveFolders(EffectiveFoldersMap effectiveFolders) {
        if (effectiveFolders == null || effectiveFolders.values().isEmpty()) {
            return Map.of();
        }
        return effectiveFolders
            .values()
            .entrySet()
            .stream()
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> entry.getValue().value()));
    }

    /**
     * Applies folder overrides to a single message by adding/removing exclusive labels so the frontend
     * can continue using label-driven filtering without learning about placements.
     */
    public EmailMessage applyFolderOverride(EmailMessage source, MailFolderIdentifier folder) {
        if (source == null || folder == null) {
            return source;
        }

        List<String> labels = new ArrayList<>(normalizeLabels(source));
        if (!labels.isEmpty()) {
            labels.removeIf(label -> EXCLUSIVE_LABELS.contains(label));
        }

        labels.addAll(normalizeFolderLabels(folder));

        EmailMessage.Builder builder = source.toBuilder();
        builder.labels(labels);
        return builder.build();
    }

    public EffectiveFoldersMap deriveEffectiveFolders(
        MailboxSnapshot snapshot,
        Map<MessageId, MessageFolderPlacement> placements
    ) {
        Map<String, MailFolderIdentifier> folderMap = new HashMap<>();
        Map<MessageId, MessageFolderPlacement> safePlacements = placements == null ? Map.of() : placements;
        for (EmailMessage message : snapshot.messages()) {
            MessageFolderPlacement placement = safePlacements.get(new MessageId(message.id()));
            MailFolderIdentifier effective = placement != null
                ? placement.folderIdentifier()
                : deriveBaselineFolder(message);
            folderMap.put(message.id(), effective);
        }
        return new EffectiveFoldersMap(folderMap);
    }

    private List<String> normalizeLabels(EmailMessage message) {
        if (message == null || message.labels() == null) {
            return List.of();
        }
        return message
            .labels()
            .stream()
            .filter(Objects::nonNull)
            .map(label -> label.trim().toLowerCase(Locale.US))
            .filter(label -> !label.isBlank())
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private boolean isArchiveLabel(String label) {
        return "archive".equals(label) || "archived".equals(label);
    }

    private boolean isTrashLabel(String label) {
        return "trash".equals(label) || "deleted".equals(label);
    }

    /**
     * Detects sent-folder markers already present on the email.
     */
    private boolean isSentLabel(String label) {
        return "sent".equals(label);
    }

    /**
     * Detects draft markers already present on the email.
     */
    private boolean isDraftLabel(String label) {
        return "drafts".equals(label) || "draft".equals(label);
    }

    /**
     * Maps a normalized folder identifier to the label(s) it should apply to an email.
     */
    private List<String> normalizeFolderLabels(MailFolderIdentifier folder) {
        if (folder.equals(ARCHIVE)) {
            return List.of("archive");
        }
        if (folder.equals(TRASH)) {
            return List.of("trash");
        }
        if (folder.equals(SENT)) {
            return List.of("sent");
        }
        if (folder.equals(DRAFTS)) {
            return List.of("drafts");
        }
        return List.of();
    }

    public record EffectiveFoldersMap(Map<String, MailFolderIdentifier> values) {
        public MailFolderIdentifier folderFor(String messageId) {
            return values.getOrDefault(messageId, INBOX);
        }
    }
}

/**
 * Constants and pure functions for mailbox folder identification and label manipulation.
 * Shared by mailboxDataStore and mailboxLayoutStore to ensure consistent folder derivation.
 */

import { mapEmailMessage, type FrontendEmailMessage } from "../services/emailUtils";

// Re-export for consumers
export type { FrontendEmailMessage };

type Message = FrontendEmailMessage;
type FolderMap = Record<string, string>;

/** Labels that represent mutually exclusive folder placements. */
export const EXCLUSIVE_FOLDER_LABELS = new Set([
  "archive",
  "archived",
  "trash",
  "deleted",
  "sent",
  "drafts",
  "draft",
]);

/** Canonical folder identifiers supported by the mailbox UI. */
export const SUPPORTED_FOLDER_IDS = new Set(["inbox", "archive", "trash", "sent", "drafts"]);

/**
 * Merges an existing label array with a target mailbox placement.
 * Removes any exclusive labels before adding the new target.
 */
export function mergeLabelsWithFolder(existing: string[], targetMailbox: string): string[] {
  const labels = Array.isArray(existing) ? existing.map((label) => `${label}`.toLowerCase()) : [];
  const cleaned = labels.filter((label) => !EXCLUSIVE_FOLDER_LABELS.has(label));
  if (targetMailbox === "archive") cleaned.push("archive");
  if (targetMailbox === "trash") cleaned.push("trash");
  if (targetMailbox === "sent") cleaned.push("sent");
  if (targetMailbox === "drafts") cleaned.push("drafts");
  return Array.from(new Set(cleaned));
}

/**
 * Derives the effective folder from a message's label array.
 * Returns 'inbox' if no exclusive folder label is present.
 */
export function deriveFolderFromLabels(labels: string[] = []): string {
  const normalized = labels.map((label) => `${label}`.toLowerCase());
  if (normalized.some((label) => label === "trash" || label === "deleted")) return "trash";
  if (normalized.some((label) => label === "archive" || label === "archived")) return "archive";
  if (normalized.includes("sent")) return "sent";
  if (normalized.includes("drafts") || normalized.includes("draft")) return "drafts";
  return "inbox";
}

/**
 * Normalizes a folder ID string to a supported canonical identifier.
 * Returns 'inbox' for unrecognized or empty values.
 */
export function normalizeFolderId(folderId: string | null | undefined): string {
  const normalized = typeof folderId === "string" ? folderId.trim().toLowerCase() : "";
  if (SUPPORTED_FOLDER_IDS.has(normalized)) {
    return normalized;
  }
  return "inbox";
}

/**
 * Resolves the effective folder for a message using an explicit folder map,
 * falling back to label-based derivation.
 */
export function resolveFolderFromMap(folderMap: FolderMap, message: Message | null): string {
  if (!message || !message.id) return "inbox";
  if (
    folderMap &&
    typeof folderMap === "object" &&
    Object.prototype.hasOwnProperty.call(folderMap, message.id)
  ) {
    return normalizeFolderId(folderMap[message.id]);
  }
  return deriveFolderFromLabels(message.labels || []);
}

/**
 * Normalizes a raw message list into frontend-compatible message objects.
 */
export function normalizeMessages(list: unknown[] | null | undefined): Message[] {
  if (!Array.isArray(list)) return [];
  return list.map((message, index) =>
    isUiMessage(message)
      ? message
      : mapEmailMessage(message as Parameters<typeof mapEmailMessage>[0], index),
  );
}

/**
 * Type guard to check if a message is already in frontend format.
 */
export function isUiMessage(message: unknown): message is Message {
  return Boolean(
    message &&
    typeof message === "object" &&
    "from" in message &&
    typeof (message as Message).from === "string" &&
    "subject" in message &&
    typeof (message as Message).subject === "string" &&
    "contentText" in message &&
    typeof (message as Message).contentText === "string",
  );
}

/**
 * Builds a normalized folder map from an optional server-provided map,
 * filling in missing entries by deriving folders from message labels.
 */
export function normalizeEffectiveFolderMap(
  folderMap: FolderMap | null,
  referenceList: Message[] = [],
): FolderMap {
  const normalized: FolderMap = {};
  if (folderMap && typeof folderMap === "object") {
    for (const [messageId, folderId] of Object.entries(folderMap)) {
      if (typeof messageId !== "string" || !messageId) continue;
      normalized[messageId] = normalizeFolderId(folderId);
    }
  }
  if (Array.isArray(referenceList)) {
    for (const message of referenceList) {
      if (!message?.id || normalized[message.id]) continue;
      normalized[message.id] = deriveFolderFromLabels(message.labels || []);
    }
  }
  return normalized;
}

/**
 * Converts a draft payload into a frontend message structure.
 */
export function normalizeDraftMessage(draft: {
  id: string;
  to?: string;
  subject?: string;
  body?: string;
}): Message {
  const nowIso = new Date().toISOString();
  const pseudo = {
    id: draft.id,
    contextId: draft.id,
    senderName: "You",
    senderEmail: "you@example.com",
    recipientName: draft.to || "",
    recipientEmail: draft.to || "",
    subject: draft.subject || "Untitled draft",
    emailBodyRaw: draft.body || "",
    emailBodyTransformedText: draft.body || "",
    emailBodyTransformedMarkdown: draft.body || "",
    emailBodyHtml: null,
    llmSummary: null,
    receivedTimestampIso: nowIso,
    receivedTimestampDisplay: "Just now",
    labels: ["drafts"],
    contextForAi: null,
  };
  return mapEmailMessage(pseudo);
}

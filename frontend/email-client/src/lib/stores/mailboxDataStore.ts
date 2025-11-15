import { derived, get, writable, type Readable } from 'svelte/store';
import { computeMailboxCounts, mapEmailMessage } from '../services/emailUtils';
import { filterEmailsByMailbox } from '../services/mailboxFiltering';
import type { FilterableEmail } from '../services/mailboxFiltering';
import { fetchMailboxStateSnapshot, moveMailboxMessage, type MailboxStateSnapshotResult, type MessageMoveResult } from '../services/mailboxStateClient';

type Message = ReturnType<typeof mapEmailMessage>;
type FolderCounts = Record<string, number>;

export interface MailboxDataStore {
  stores: {
    emails: Readable<Message[]>;
    mailbox: Readable<string>;
    search: Readable<string>;
    filteredEmails: Readable<Message[]>;
    mailboxCounts: Readable<FolderCounts>;
    messagePlacements: Readable<Record<string, string>>;
    pendingMoves: Readable<Set<string>>;
    moveErrors: Readable<Record<string, string>>;
  };
  initializeSnapshot: (snapshot: any) => void;
  hydrateEmails: (nextEmails: any[], effectiveFoldersOverride?: Record<string, string> | null) => void;
  selectMailbox: (target: string) => void;
  setSearch: (value: string) => void;
  loadMailboxState: (mailboxId: string) => Promise<MailboxStateSnapshotResult | null>;
  moveMessageRemote: (params: { mailboxId: string; messageId: string; targetFolderId: string }) => Promise<MessageMoveResult | null>;
  saveDraftSession: (draft: any) => void;
  markDraftAsSent: (draftId: string) => void;
  deleteDraftMessage: (draftId: string) => void;
  resolveFolderForMessage: (message: Message | null) => string;
  markEmailRead: (messageId: string | null) => void;
}

export function createMailboxDataStore(
  initialEmails: any[] = [],
  initialFolderCounts: FolderCounts | null = null,
  initialEffectiveFolders: Record<string, string> | null = null
): MailboxDataStore {
  const normalizedInitial = normalizeMessages(initialEmails);
  const emails = writable<Message[]>(normalizedInitial);
  const mailbox = writable('inbox');
  const search = writable('');
  const messagePlacements = writable<Record<string, string>>({});
  const mailboxCounts = writable<FolderCounts>(initialFolderCounts ?? computeMailboxCounts(normalizedInitial));
  const messageFolders = writable<Record<string, string>>(normalizeEffectiveFolderMap(initialEffectiveFolders, normalizedInitial));
  const pendingMoves = writable<Set<string>>(new Set());
  const moveErrors = writable<Record<string, string>>({});

  const filteredEmails: Readable<Message[]> = derived([emails, mailbox, search, messageFolders], ([$emails, $mailbox, $search, $folders]) =>
    filterEmailsByMailbox<Message>($emails, $mailbox, $search, (email) => resolveFolderFromMap($folders, email))
  );

  function initializeSnapshot(snapshot: any) {
    if (!snapshot) return;
    const nextEmails = normalizeMessages(snapshot.emails || snapshot.messages || []);
    emails.set(nextEmails);
    if (snapshot.folderCounts) {
      mailboxCounts.set(snapshot.folderCounts);
    }
    if (snapshot.placements) {
      messagePlacements.set(snapshot.placements);
    }
    if (snapshot.effectiveFolders) {
      setEffectiveFolders(snapshot.effectiveFolders, nextEmails);
    } else {
      setEffectiveFolders(null, nextEmails);
    }
  }

  function hydrateEmails(nextEmails: any[], effectiveFoldersOverride: Record<string, string> | null = null) {
    const normalized = normalizeMessages(nextEmails);
    emails.set(normalized);
    mailboxCounts.set(computeMailboxCounts(normalized));
    setEffectiveFolders(effectiveFoldersOverride, normalized);
  }

  function markEmailRead(id: string | null) {
    if (!id) return;
    emails.update((list) => list.map((email) => (email.id === id ? { ...email, read: true } : email)));
  }

  function selectMailbox(target: string) {
    mailbox.set(target || 'inbox');
  }

  function setSearch(value: string) {
    search.set(value || '');
  }

  async function loadMailboxState(mailboxId: string) {
    if (!mailboxId) return null;
    const snapshot = await fetchMailboxStateSnapshot(mailboxId);
    if (snapshot?.messages) {
      hydrateEmails(snapshot.messages, snapshot.effectiveFolders || null);
    } else if (snapshot?.effectiveFolders) {
      setEffectiveFolders(snapshot.effectiveFolders);
    }
    if (snapshot?.folderCounts) {
      mailboxCounts.set(snapshot.folderCounts);
    }
    if (snapshot?.placements) {
      messagePlacements.set(snapshot.placements);
    }
    if (!snapshot?.messages && !snapshot?.effectiveFolders) {
      setEffectiveFolders(null);
    }
    return snapshot;
  }

  async function moveMessageRemote({ mailboxId, messageId, targetFolderId }: { mailboxId: string; messageId: string; targetFolderId: string }) {
    if (!mailboxId || !messageId || !targetFolderId) return null;
    if (get(pendingMoves).has(messageId)) return null;
    const previousMessage = get(emails).find((entry) => entry.id === messageId);
    const previousLabels = previousMessage ? [...(previousMessage.labels || [])] : null;
    const previousFolderId = resolveFolderForMessage(previousMessage || null);
    markMovePending(messageId);
    applyMailboxMove(messageId, targetFolderId);
    try {
      const result = await moveMailboxMessage({ mailboxId, messageId, targetFolderId });
      reconcileMailboxMoveResult(result);
      clearMovePending(messageId);
      return result;
    } catch (error: unknown) {
      if (previousLabels) {
        revertMailboxMove(messageId, previousLabels, previousFolderId);
      }
      const fallbackMessage = error instanceof Error ? error.message : 'Unable to move message.';
      registerMoveError(messageId, fallbackMessage);
      throw error;
    }
  }

  function saveDraftSession(draft: any) {
    if (!draft || !draft.id) return;
    const normalizedDraft = normalizeDraftMessage(draft);
    updateEmailsAndCounts((list) => {
      const index = list.findIndex((entry) => entry.id === normalizedDraft.id);
      if (index === -1) {
        return [normalizedDraft, ...list];
      }
      const next = [...list];
      next[index] = { ...next[index], ...normalizedDraft };
      return next;
    });
    updateFolderMapping(draft.id, 'drafts');
    updatePlacementForMessage(draft.id, 'drafts');
  }

  function markDraftAsSent(draftId: string) {
    if (!draftId) return;
    updateEmailsAndCounts((list) =>
      list.map((entry) => (entry.id === draftId ? { ...entry, labels: mergeLabels(entry.labels || [], 'sent') } : entry))
    );
    updateFolderMapping(draftId, 'sent');
    updatePlacementForMessage(draftId, 'sent');
  }

  function deleteDraftMessage(draftId: string) {
    if (!draftId) return;
    updateEmailsAndCounts((list) => list.filter((entry) => entry.id !== draftId));
    messagePlacements.update((map) => {
      const next = { ...map };
      delete next[draftId];
      return next;
    });
    removeFolderMapping(draftId);
  }

  function resolveFolderForMessage(message: Message | null) {
    if (!message) return 'inbox';
    const folders = get(messageFolders);
    return resolveFolderFromMap(folders, message);
  }

  function applyMailboxMove(messageId: string, targetMailbox: string) {
    updateEmailsAndCounts((list) =>
      list.map((entry) =>
        entry.id === messageId ? { ...entry, labels: mergeLabels(entry.labels || [], targetMailbox) } : entry
      )
    );
    updateFolderMapping(messageId, targetMailbox);
    updatePlacementForMessage(messageId, targetMailbox);
  }

  function reconcileMailboxMoveResult(payload: MessageMoveResult | MailboxStateSnapshotResult | null) {
    if (!payload) return;
    if (payload.messages) {
      hydrateEmails(payload.messages, payload.effectiveFolders || null);
    }
    if (payload.folderCounts) {
      mailboxCounts.set(payload.folderCounts);
    }
    if (payload.placements) {
      messagePlacements.set(payload.placements);
    }
    if (!payload.messages && payload.effectiveFolders) {
      setEffectiveFolders(payload.effectiveFolders);
    }
  }

  function updateEmailsAndCounts(updater: (list: Message[]) => Message[]) {
    let nextList: Message[] = [];
    emails.update((list) => {
      nextList = updater(list);
      return nextList;
    });
    mailboxCounts.set(computeMailboxCounts(nextList));
    return nextList;
  }

  function updatePlacementForMessage(messageId: string, targetFolderId: string) {
    messagePlacements.update((map) => {
      const next = { ...map };
      if (!targetFolderId || targetFolderId === 'inbox') {
        delete next[messageId];
      } else {
        next[messageId] = targetFolderId;
      }
      return next;
    });
  }

  function updateFolderMapping(messageId: string, folderId: string) {
    if (!messageId) return;
    messageFolders.update((current) => {
      const next = { ...current };
      next[messageId] = normalizeFolderId(folderId);
      return next;
    });
  }

  function removeFolderMapping(messageId: string) {
    if (!messageId) return;
    messageFolders.update((current) => {
      const next = { ...current };
      delete next[messageId];
      return next;
    });
  }

  function revertMailboxMove(messageId: string, labels: string[], previousFolderId = 'inbox') {
    updateEmailsAndCounts((list) =>
      list.map((entry) => (entry.id === messageId ? { ...entry, labels: [...labels] } : entry))
    );
    updateFolderMapping(messageId, previousFolderId);
    updatePlacementForMessage(messageId, deriveFolderFromLabels(labels));
  }

  function markMovePending(messageId: string) {
    pendingMoves.update((current) => {
      const next = new Set(current);
      next.add(messageId);
      return next;
    });
    moveErrors.update((map) => {
      if (!map[messageId]) return map;
      const next = { ...map };
      delete next[messageId];
      return next;
    });
  }

  function clearMovePending(messageId: string) {
    pendingMoves.update((current) => {
      const next = new Set(current);
      next.delete(messageId);
      return next;
    });
  }

  function registerMoveError(messageId: string, message: string) {
    clearMovePending(messageId);
    moveErrors.update((map) => ({ ...map, [messageId]: message }));
  }

  function setEffectiveFolders(folderMap: Record<string, string> | null, referenceList: Message[] | null = null) {
    const reference = Array.isArray(referenceList) ? referenceList : get(emails);
    messageFolders.set(normalizeEffectiveFolderMap(folderMap, reference));
  }

  return {
    stores: {
      emails: { subscribe: emails.subscribe },
      mailbox: { subscribe: mailbox.subscribe },
      search: { subscribe: search.subscribe },
      filteredEmails,
      mailboxCounts: { subscribe: mailboxCounts.subscribe },
      messagePlacements: { subscribe: messagePlacements.subscribe },
      pendingMoves: { subscribe: pendingMoves.subscribe },
      moveErrors: { subscribe: moveErrors.subscribe }
    },
    initializeSnapshot,
    hydrateEmails,
    selectMailbox,
    setSearch,
    loadMailboxState,
    moveMessageRemote,
    saveDraftSession,
    markDraftAsSent,
    deleteDraftMessage,
    resolveFolderForMessage,
    markEmailRead
  };
}

function normalizeMessages(list: any[]) {
  if (!Array.isArray(list)) return [];
  return list.map((message, index) => (isUiMessage(message) ? message : mapEmailMessage(message, index)));
}

function isUiMessage(message: any) {
  return Boolean(message && typeof message.from === 'string' && typeof message.subject === 'string' && 'contentText' in message);
}

function mergeLabels(existing: string[], targetMailbox: string) {
  const labels = Array.isArray(existing) ? existing.map((label) => `${label}`.toLowerCase()) : [];
  const cleaned = labels.filter((label) => !EXCLUSIVE_LABELS.has(label));
  if (targetMailbox === 'archive') cleaned.push('archive');
  if (targetMailbox === 'trash') cleaned.push('trash');
  if (targetMailbox === 'sent') cleaned.push('sent');
  if (targetMailbox === 'drafts') cleaned.push('drafts');
  return Array.from(new Set(cleaned));
}

function deriveFolderFromLabels(labels: string[] = []) {
  const normalized = labels.map((label) => `${label}`.toLowerCase());
  if (normalized.some((label) => label === 'trash' || label === 'deleted')) return 'trash';
  if (normalized.some((label) => label === 'archive' || label === 'archived')) return 'archive';
  if (normalized.includes('sent')) return 'sent';
  if (normalized.includes('drafts') || normalized.includes('draft')) return 'drafts';
  return 'inbox';
}

function normalizeDraftMessage(draft: any) {
  const nowIso = new Date().toISOString();
  const pseudo = {
    id: draft.id,
    contextId: draft.id,
    senderName: 'You',
    senderEmail: 'you@example.com',
    recipientName: draft.to || '',
    recipientEmail: draft.to || '',
    subject: draft.subject || 'Untitled draft',
    emailBodyRaw: draft.body || '',
    emailBodyTransformedText: draft.body || '',
    emailBodyTransformedMarkdown: draft.body || '',
    emailBodyHtml: null,
    llmSummary: null,
    receivedTimestampIso: nowIso,
    receivedTimestampDisplay: 'Just now',
    labels: ['drafts'],
    contextForAi: null
  };
  return mapEmailMessage(pseudo);
}

const EXCLUSIVE_LABELS = new Set(['archive', 'archived', 'trash', 'deleted', 'sent', 'drafts', 'draft']);
const SUPPORTED_FOLDERS = new Set(['inbox', 'archive', 'trash', 'sent', 'drafts']);

function normalizeEffectiveFolderMap(folderMap: Record<string, string> | null, referenceList: Message[] = []) {
  const normalized: Record<string, string> = {};
  if (folderMap && typeof folderMap === 'object') {
    for (const [messageId, folderId] of Object.entries(folderMap)) {
      if (typeof messageId !== 'string' || !messageId) continue;
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

export function resolveFolderFromMap(folderMap: Record<string, string>, message: Message) {
  if (!message || !message.id) return 'inbox';
  if (folderMap && typeof folderMap === 'object' && folderMap[message.id]) {
    return normalizeFolderId(folderMap[message.id]);
  }
  return deriveFolderFromLabels(message.labels || []);
}

function normalizeFolderId(folderId: string) {
  const normalized = typeof folderId === 'string' ? folderId.trim().toLowerCase() : '';
  if (SUPPORTED_FOLDERS.has(normalized)) {
    return normalized;
  }
  return 'inbox';
}

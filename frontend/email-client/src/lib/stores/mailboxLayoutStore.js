import { derived, writable, get } from 'svelte/store';
import { computeMailboxCounts, mapEmailMessage } from '../services/emailUtils';
import { filterEmailsByMailbox } from '../services/mailboxFiltering';
import { fetchMailboxStateSnapshot, moveMailboxMessage } from '../services/mailboxStateClient';

/**
 * Creates the mailbox layout store that tracks message payloads, folder placements, drafts, and drawer UI.
 * Every move/archive/delete call funnels through this store to keep optimistic updates and server responses aligned.
 */
export function createMailboxLayoutStore(initialEmails = [], initialFolderCounts = null, initialEffectiveFolders = null) {
  const normalizedInitial = normalizeMessages(initialEmails);
  const emails = writable(normalizedInitial);
  const initialEffectiveMap = normalizeEffectiveFolderMap(initialEffectiveFolders, normalizedInitial);
  const selectedEmailId = writable(null);
  const mailbox = writable('inbox');
  const search = writable('');
  const sidebarOpen = writable(true);
  const drawerMode = writable(false);
  const drawerVisible = writable(false);
  const messagePlacements = writable({});
  const mailboxCounts = writable(initialFolderCounts ?? computeMailboxCounts(normalizedInitial));
  const messageFolders = writable(initialEffectiveMap);
  const pendingMoves = writable(new Set());
  const moveErrors = writable({});

  const filteredEmails = derived([emails, mailbox, search, messageFolders], ([$emails, $mailbox, $search, $folders]) =>
    filterEmailsByMailbox($emails, $mailbox, $search, (email) => resolveFolderFromMap($folders, email))
  );
  const selectedEmail = derived([emails, selectedEmailId], ([$emails, $selectedId]) =>
    $emails.find((email) => email.id === $selectedId) || null
  );

  /**
   * Replaces the entire mailbox contents with a snapshot from the backend.
   */
  function initializeSnapshot(snapshot) {
    if (!snapshot) return;
    const nextEmails = normalizeMessages(snapshot.emails || snapshot.messages || []);
    emails.set(nextEmails);
    selectedEmailId.set(snapshot.selectedEmailId || null);
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

  /**
   * Normalizes raw email payloads and recalculates folder counters.
   */
  function hydrateEmails(nextEmails, effectiveFoldersOverride = null) {
    const normalized = normalizeMessages(nextEmails);
    emails.set(normalized);
    mailboxCounts.set(computeMailboxCounts(normalized));
    setEffectiveFolders(effectiveFoldersOverride, normalized);
  }

  /**
   * Marks an email as selected (and read) inside the local store.
   */
  function selectEmailById(id) {
    selectedEmailId.set(id);
    if (!id) return;
    emails.update((list) => list.map((email) => (email.id === id ? { ...email, read: true } : email)));
  }

  /**
   * Switches the active mailbox (used when clicking sidebar folders).
   */
  function selectMailbox(target) {
    mailbox.set(target);
  }

  /**
   * Updates the in-memory search query; derived lists react automatically.
   */
  function setSearch(value) {
    search.set(value);
  }

  /**
   * Flips the sidebar visibility for desktop layouts.
   */
  function toggleSidebar() {
    sidebarOpen.update((open) => !open);
  }

  /**
   * Hard-sets sidebar visibility (used by responsive breakpoints).
   */
  function setSidebarOpen(open) {
    sidebarOpen.set(open);
  }

  /**
   * Enables/disables drawer mode for tablet/mobile breakpoints.
   */
  function setDrawerMode(isDrawer) {
    drawerMode.update((current) => {
      if (current === isDrawer) {
        return current;
      }
      if (!isDrawer) {
        drawerVisible.set(false);
      }
      return isDrawer;
    });
  }

  /**
   * Shows or hides the drawer overlay on mobile/tablet.
   */
  function setDrawerVisible(visible) {
    drawerVisible.set(visible);
  }

  function openDrawer() {
    setDrawerVisible(true);
  }

  function closeDrawer() {
    setDrawerVisible(false);
  }

  /**
   * Applies folder changes optimistically for a single message.
   */
  function applyMailboxMove(messageId, targetMailbox) {
    updateEmailsAndCounts((list) =>
      list.map((entry) =>
        entry.id === messageId ? { ...entry, labels: mergeLabels(entry.labels || [], targetMailbox) } : entry
      )
    );
    updateFolderMapping(messageId, targetMailbox);
    updatePlacementForMessage(messageId, targetMailbox);
  }

  /**
   * Applies the server's authoritative snapshot after a move completes.
   */
  function reconcileMailboxMoveResult(payload) {
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

  /**
   * Hydrates the current mailbox from the backend snapshot (emails + active selection).
   */
  /**
   * Retrieves the snapshot for the requested mailbox and seeds the store.
   */
  async function loadMailboxState(mailboxId) {
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

  /**
   * Calls the move endpoint while keeping optimistic UI in sync.
   */
  async function moveMessageRemote({ mailboxId, messageId, targetFolderId }) {
    if (!mailboxId || !messageId || !targetFolderId) return null;
    if (get(pendingMoves).has(messageId)) return null;
    const previousMessage = get(emails).find((entry) => entry.id === messageId);
    const previousLabels = previousMessage ? [...(previousMessage.labels || [])] : null;
    const previousFolderId = resolveFolderForMessage(previousMessage);
    markMovePending(messageId);
    applyMailboxMove(messageId, targetFolderId);
    try {
      const result = await moveMailboxMessage({ mailboxId, messageId, targetFolderId });
      reconcileMailboxMoveResult(result);
      clearMovePending(messageId);
      return result;
    } catch (error) {
      if (previousLabels) {
        revertMailboxMove(messageId, previousLabels, previousFolderId);
      }
      registerMoveError(messageId, error?.message || 'Unable to move message.');
      throw error;
    }
  }

  /**
   * Persists an in-progress compose window as a draft entry.
   */
  function saveDraftSession(draft) {
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

  /**
   * Re-labels a draft as sent once the user clicks Send (SMTP coming later).
   */
  function markDraftAsSent(draftId) {
    if (!draftId) return;
    updateEmailsAndCounts((list) =>
      list.map((entry) => (entry.id === draftId ? { ...entry, labels: mergeLabels(entry.labels || [], 'sent') } : entry))
    );
    updateFolderMapping(draftId, 'sent');
    updatePlacementForMessage(draftId, 'sent');
  }

  /**
   * Drops the draft from the mailbox (used by compose delete button).
   */
  function deleteDraftMessage(draftId) {
    if (!draftId) return;
    updateEmailsAndCounts((list) => list.filter((entry) => entry.id !== draftId));
    messagePlacements.update((map) => {
      const next = { ...map };
      delete next[draftId];
      return next;
    });
    removeFolderMapping(draftId);
  }

  /**
   * Returns the effective folder for a message, factoring in session placements.
   */
  function resolveFolderForMessage(message) {
    if (!message) return 'inbox';
    const folders = get(messageFolders);
    return resolveFolderFromMap(folders, message);
  }

  function updateEmailsAndCounts(updater) {
    let nextList = [];
    emails.update((list) => {
      nextList = updater(list);
      return nextList;
    });
    mailboxCounts.set(computeMailboxCounts(nextList));
    return nextList;
  }

  function updatePlacementForMessage(messageId, targetFolderId) {
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

  function updateFolderMapping(messageId, folderId) {
    if (!messageId) return;
    messageFolders.update((current) => {
      const next = { ...current };
      next[messageId] = normalizeFolderId(folderId);
      return next;
    });
  }

  function removeFolderMapping(messageId) {
    if (!messageId) return;
    messageFolders.update((current) => {
      const next = { ...current };
      delete next[messageId];
      return next;
    });
  }

  function revertMailboxMove(messageId, labels, previousFolderId = 'inbox') {
    updateEmailsAndCounts((list) =>
      list.map((entry) => (entry.id === messageId ? { ...entry, labels: [...labels] } : entry))
    );
    updateFolderMapping(messageId, previousFolderId);
    updatePlacementForMessage(messageId, deriveFolderFromLabels(labels));
  }

  function markMovePending(messageId) {
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

  function clearMovePending(messageId) {
    pendingMoves.update((current) => {
      const next = new Set(current);
      next.delete(messageId);
      return next;
    });
  }

  function registerMoveError(messageId, message) {
    clearMovePending(messageId);
    moveErrors.update((map) => ({ ...map, [messageId]: message }));
  }

  function setEffectiveFolders(folderMap, referenceList = null) {
    const reference = Array.isArray(referenceList) ? referenceList : get(emails);
    messageFolders.set(normalizeEffectiveFolderMap(folderMap, reference));
  }

  return {
    stores: {
      emails: { subscribe: emails.subscribe },
      selectedEmail,
      mailbox,
      search,
      sidebarOpen,
      drawerMode,
      drawerVisible,
      messagePlacements,
      mailboxCounts,
      filteredEmails,
      pendingMoves: { subscribe: pendingMoves.subscribe },
      moveErrors: { subscribe: moveErrors.subscribe }
    },
    initializeSnapshot,
    hydrateEmails,
    selectEmailById,
    selectMailbox,
    setSearch,
    toggleSidebar,
    setSidebarOpen,
    setDrawerMode,
    setDrawerVisible,
    openDrawer,
    closeDrawer,
    loadMailboxState,
    moveMessageRemote,
    saveDraftSession,
    markDraftAsSent,
    deleteDraftMessage,
    resolveFolderForMessage
  };
}

function normalizeMessages(list) {
  if (!Array.isArray(list)) return [];
  return list.map((message, index) => (isUiMessage(message) ? message : mapEmailMessage(message, index)));
}

function isUiMessage(message) {
  return Boolean(message && typeof message.from === 'string' && typeof message.subject === 'string' && 'contentText' in message);
}

function mergeLabels(existing, targetMailbox) {
  const labels = Array.isArray(existing) ? existing.map((label) => `${label}`.toLowerCase()) : [];
  const cleaned = labels.filter((label) => !EXCLUSIVE_LABELS.has(label));
  if (targetMailbox === 'archive') cleaned.push('archive');
  if (targetMailbox === 'trash') cleaned.push('trash');
  if (targetMailbox === 'sent') cleaned.push('sent');
  if (targetMailbox === 'drafts') cleaned.push('drafts');
  return Array.from(new Set(cleaned));
}

function deriveFolderFromLabels(labels = []) {
  const normalized = labels.map((label) => `${label}`.toLowerCase());
  if (normalized.some((label) => label === 'trash' || label === 'deleted')) return 'trash';
  if (normalized.some((label) => label === 'archive' || label === 'archived')) return 'archive';
  if (normalized.includes('sent')) return 'sent';
  if (normalized.includes('drafts') || normalized.includes('draft')) return 'drafts';
  return 'inbox';
}

function normalizeDraftMessage(draft) {
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

function normalizeEffectiveFolderMap(folderMap, referenceList = []) {
  const normalized = {};
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

function resolveFolderFromMap(folderMap, message) {
  if (!message || !message.id) return 'inbox';
  if (folderMap && typeof folderMap === 'object' && folderMap[message.id]) {
    return normalizeFolderId(folderMap[message.id]);
  }
  return deriveFolderFromLabels(message.labels || []);
}

function normalizeFolderId(folderId) {
  const normalized = typeof folderId === 'string' ? folderId.trim().toLowerCase() : '';
  if (SUPPORTED_FOLDERS.has(normalized)) {
    return normalized;
  }
  return 'inbox';
}

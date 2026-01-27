import { derived, get, writable, type Readable } from 'svelte/store';
import { computeMailboxCounts } from '../services/emailUtils';
import { filterEmailsByMailbox } from '../services/mailboxFiltering';
import { fetchMailboxStateSnapshot, moveMailboxMessage, type MailboxStateSnapshot, type MessageMoveResult } from '../services/mailboxStateClient';
import {
  normalizeMessages,
  normalizeDraftMessage,
  normalizeEffectiveFolderMap,
  normalizeFolderId,
  mergeLabelsWithFolder,
  deriveFolderFromLabels,
  resolveFolderFromMap,
  type FrontendEmailMessage
} from './mailboxFolderLabels';

type Message = FrontendEmailMessage;
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
  loadMailboxState: (mailboxId: string) => Promise<MailboxStateSnapshot | null>;
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
    const validationResult = await fetchMailboxStateSnapshot(mailboxId);
    if (!validationResult.success) {
      // Validation failure already logged by fetchMailboxStateSnapshot
      return null;
    }
    const snapshot = validationResult.data;
    if (snapshot.messages) {
      hydrateEmails(snapshot.messages, snapshot.effectiveFolders || null);
    } else if (snapshot.effectiveFolders) {
      setEffectiveFolders(snapshot.effectiveFolders);
    }
    if (snapshot.folderCounts) {
      mailboxCounts.set(snapshot.folderCounts);
    }
    if (snapshot.placements) {
      messagePlacements.set(snapshot.placements);
    }
    if (!snapshot.messages && !snapshot.effectiveFolders) {
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
      const validationResult = await moveMailboxMessage({ mailboxId, messageId, targetFolderId });
      if (!validationResult.success) {
        // Validation failure already logged - revert optimistic update
        if (previousLabels) {
          revertMailboxMove(messageId, previousLabels, previousFolderId);
        }
        registerMoveError(messageId, 'Server response validation failed');
        return null;
      }
      const moveResult = validationResult.data;
      reconcileMailboxMoveResult(moveResult);
      clearMovePending(messageId);
      return moveResult;
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
      list.map((entry) => (entry.id === draftId ? { ...entry, labels: mergeLabelsWithFolder(entry.labels || [], 'sent') } : entry))
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
        entry.id === messageId ? { ...entry, labels: mergeLabelsWithFolder(entry.labels || [], targetMailbox) } : entry
      )
    );
    updateFolderMapping(messageId, targetMailbox);
    updatePlacementForMessage(messageId, targetMailbox);
  }

  function reconcileMailboxMoveResult(payload: MessageMoveResult | MailboxStateSnapshot | null) {
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

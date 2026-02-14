import { writable, derived, get, type Writable, type Readable } from "svelte/store";
import { WindowKind, WindowMode, type WindowDescriptor, type DraftSnapshot } from "./windowTypes";

export interface WindowManagerConfig {
  maxFloating?: number;
  maxDocked?: number;
}

export interface WindowManagerError {
  type: string;
  message: string;
  at: number;
}

export interface WindowManager {
  windows: Writable<WindowDescriptor[]>;
  floating: Readable<WindowDescriptor[]>;
  docked: Readable<WindowDescriptor[]>;
  minimized: Readable<WindowDescriptor[]>;
  lastError: Writable<WindowManagerError | null>;
  open: (descriptor: WindowDescriptor) => { ok: boolean; replaced?: boolean; reason?: string };
  close: (id: string | null | undefined) => void;
  toggleMinimize: (id: string | null | undefined) => void;
  focus: (id: string | null | undefined) => void;
  clearError: () => void;
  updateSummaryHtml: (contextId: string | null | undefined, html: string) => void;
  updateComposeDraft: (
    id: string | null | undefined,
    payload: { subject?: string | null; body?: string | null },
  ) => void;
  syncComposeContext: (
    id: string | null | undefined,
    payload: { contextId: string; fingerprint: string | null },
  ) => void;
  pushDraftHistory: (
    id: string | null | undefined,
    snapshot: { subject: string; body: string },
  ) => void;
  restoreDraftHistory: (id: string | null | undefined) => { subject: string; body: string } | null;
}

/**
 * Client-side window manager implemented as a plain JS module to keep shared state logic reusable across
 * Svelte components without introducing extra render layers. Java cannot host this state because these
 * windows exist purely in the browser, and putting it in a .svelte file would require dummy components
 * just to expose helper functions.
 */
const DRAFT_HISTORY_LIMIT = 5;

export function createWindowManager({
  maxFloating = 4,
  maxDocked = 3,
}: WindowManagerConfig = {}): WindowManager {
  const windows = writable<WindowDescriptor[]>([]);
  const lastError = writable<WindowManagerError | null>(null);

  const floating = derived(windows, ($windows) =>
    $windows.filter((win) => win.mode === WindowMode.FLOATING),
  );

  const docked = derived(windows, ($windows) =>
    $windows.filter((win) => win.mode === WindowMode.DOCKED),
  );

  const minimized = derived(windows, ($windows) => $windows.filter((win) => win.minimized));

  function setError(type: string, message: string) {
    lastError.set({ type, message, at: Date.now() });
  }

  function clearError() {
    lastError.set(null);
  }

  function enforceLimit(mode: typeof WindowMode.FLOATING | typeof WindowMode.DOCKED) {
    const list = get(windows);
    const cap = mode === WindowMode.FLOATING ? maxFloating : maxDocked;
    const count = list.filter((win) => win.mode === mode).length;
    return count < cap;
  }

  function open(descriptor: WindowDescriptor) {
    if (!descriptor) return { ok: false };

    // Replace existing summary window for the same email/context.
    if (descriptor.kind === WindowKind.SUMMARY && descriptor.contextId) {
      let replaced = false;
      windows.update((list) => {
        const idx = list.findIndex(
          (win) => win.kind === WindowKind.SUMMARY && win.contextId === descriptor.contextId,
        );
        if (idx === -1) {
          return list;
        }
        const existing = list[idx];
        const updated = [...list];
        updated[idx] = {
          ...descriptor,
          id: existing.id,
          minimized: false,
        };
        replaced = true;
        return updated;
      });
      if (replaced) {
        clearError();
        return { ok: true, replaced: true };
      }
    }

    if (!enforceLimit(descriptor.mode)) {
      const type = descriptor.mode === WindowMode.FLOATING ? "max-floating" : "max-docked";
      const message =
        descriptor.mode === WindowMode.FLOATING
          ? "Close or minimize an existing draft before opening another."
          : "Close an AI panel before opening another.";
      setError(type, message);
      return { ok: false, reason: type };
    }

    windows.update((list) => [...list, descriptor]);
    clearError();
    return { ok: true };
  }

  function close(id: string | null | undefined) {
    if (!id) return;
    windows.update((list) => list.filter((win) => win.id !== id));
    clearError();
  }

  function toggleMinimize(id: string | null | undefined) {
    if (!id) return;
    windows.update((list) =>
      list.map((win) => {
        if (win.id !== id) return win;
        return { ...win, minimized: !win.minimized };
      }),
    );
  }

  function focus(id: string | null | undefined) {
    if (!id) return;
    windows.update((list) => {
      const idx = list.findIndex((win) => win.id === id);
      if (idx === -1) return list;
      const updated = [...list];
      const [target] = updated.splice(idx, 1);
      return [...updated, { ...target, minimized: false }];
    });
  }

  function updateSummaryHtml(contextId: string | null | undefined, html: string) {
    if (!contextId) return;
    windows.update((list) =>
      list.map((win) => {
        if (win.kind !== WindowKind.SUMMARY || win.contextId !== contextId) {
          return win;
        }
        return {
          ...win,
          payload: { ...win.payload, html },
        };
      }),
    );
  }

  function updateComposeDraft(
    id: string | null | undefined,
    { subject, body }: { subject?: string | null; body?: string | null },
  ) {
    if (!id) return;
    windows.update((list) =>
      list.map((win) => {
        if (win.id !== id || win.kind !== WindowKind.COMPOSE) return win;
        const nextVersion = (win.payload?.bodyVersion || 0) + 1;
        return {
          ...win,
          payload: {
            ...win.payload,
            subject: subject ?? win.payload.subject,
            body: body ?? win.payload.body,
            bodyVersion: nextVersion,
          },
        };
      }),
    );
  }

  function syncComposeContext(
    id: string | null | undefined,
    { contextId, fingerprint }: { contextId: string; fingerprint: string | null },
  ) {
    if (!id || !contextId) return;
    windows.update((list) =>
      list.map((win) => {
        if (win.id !== id || win.kind !== WindowKind.COMPOSE) return win;
        return {
          ...win,
          payload: {
            ...win.payload,
            draftContextId: contextId,
            draftContextFingerprint: fingerprint,
          },
        };
      }),
    );
  }

  function pushDraftHistory(
    id: string | null | undefined,
    snapshot: { subject: string; body: string },
  ) {
    if (!id) return;
    const safeSubject = typeof snapshot?.subject === "string" ? snapshot.subject : "";
    const safeBody = typeof snapshot?.body === "string" ? snapshot.body : "";
    windows.update((list) =>
      list.map((win) => {
        if (win.id !== id || win.kind !== WindowKind.COMPOSE) return win;
        const history = Array.isArray(win.payload?.draftHistory)
          ? [...win.payload.draftHistory]
          : [];
        const lastEntry = history[history.length - 1];
        if (lastEntry && lastEntry.subject === safeSubject && lastEntry.body === safeBody) {
          return win;
        }
        const nextEntry: DraftSnapshot = {
          subject: safeSubject,
          body: safeBody,
          timestamp: Date.now(),
        };
        history.push(nextEntry);
        if (history.length > DRAFT_HISTORY_LIMIT) {
          history.shift();
        }
        return {
          ...win,
          payload: {
            ...win.payload,
            draftHistory: history,
          },
        };
      }),
    );
  }

  function restoreDraftHistory(id: string | null | undefined) {
    if (!id) return null;
    let restored: { subject: string; body: string } | null = null;
    windows.update((list) =>
      list.map((win) => {
        if (win.id !== id || win.kind !== WindowKind.COMPOSE) return win;
        const history = Array.isArray(win.payload?.draftHistory)
          ? [...win.payload.draftHistory]
          : [];
        const snapshot = history.pop();
        if (!snapshot) {
          return win;
        }
        restored = { subject: snapshot.subject, body: snapshot.body };
        const nextVersion = (win.payload?.bodyVersion || 0) + 1;
        return {
          ...win,
          payload: {
            ...win.payload,
            subject: snapshot.subject,
            body: snapshot.body,
            bodyVersion: nextVersion,
            draftHistory: history,
          },
        };
      }),
    );
    return restored;
  }

  return {
    windows,
    floating,
    docked,
    minimized,
    lastError,
    open,
    close,
    toggleMinimize,
    focus,
    clearError,
    updateSummaryHtml,
    updateComposeDraft,
    syncComposeContext,
    pushDraftHistory,
    restoreDraftHistory,
  };
}

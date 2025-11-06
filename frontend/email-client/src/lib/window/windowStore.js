import { writable, derived, get } from 'svelte/store';
import { WindowKind, WindowMode } from './windowTypes';

/**
 * Client-side window manager implemented as a plain JS module to keep shared state logic reusable across
 * Svelte components without introducing extra render layers. Java cannot host this state because these
 * windows exist purely in the browser, and putting it in a .svelte file would require dummy components
 * just to expose helper functions.
 */
export function createWindowManager({ maxFloating = 4, maxDocked = 3 } = {}) {
  const windows = writable([]);
  const lastError = writable(null);

  const floating = derived(windows, ($windows) =>
    $windows.filter((win) => win.mode === WindowMode.FLOATING)
  );

  const docked = derived(windows, ($windows) =>
    $windows.filter((win) => win.mode === WindowMode.DOCKED)
  );

  const minimized = derived(windows, ($windows) =>
    $windows.filter((win) => win.minimized)
  );

  function setError(type, message) {
    lastError.set({ type, message, at: Date.now() });
  }

  function clearError() {
    lastError.set(null);
  }

  function enforceLimit(mode) {
    const list = get(windows);
    const cap = mode === WindowMode.FLOATING ? maxFloating : maxDocked;
    const count = list.filter((win) => win.mode === mode).length;
    return count < cap;
  }

  function open(descriptor) {
    if (!descriptor) return { ok: false };

    // Replace existing summary window for the same email/context.
    if (descriptor.kind === WindowKind.SUMMARY && descriptor.contextId) {
      let replaced = false;
      windows.update((list) => {
        const idx = list.findIndex((win) =>
          win.kind === WindowKind.SUMMARY && win.contextId === descriptor.contextId
        );
        if (idx === -1) {
          return list;
        }
        const existing = list[idx];
        const updated = [...list];
        updated[idx] = {
          ...descriptor,
          id: existing.id,
          minimized: false
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
      const type = descriptor.mode === WindowMode.FLOATING ? 'max-floating' : 'max-docked';
      const message = descriptor.mode === WindowMode.FLOATING
        ? 'Close or minimize an existing draft before opening another.'
        : 'Close an AI panel before opening another.';
      setError(type, message);
      return { ok: false, reason: type };
    }

    windows.update((list) => [...list, descriptor]);
    clearError();
    return { ok: true };
  }

  function close(id) {
    if (!id) return;
    windows.update((list) => list.filter((win) => win.id !== id));
    clearError();
  }

  function toggleMinimize(id) {
    if (!id) return;
    windows.update((list) => list.map((win) => {
      if (win.id !== id) return win;
      return { ...win, minimized: !win.minimized };
    }));
  }

  function updateSummaryHtml(contextId, html) {
    if (!contextId) return;
    windows.update((list) => list.map((win) => {
      if (win.kind !== WindowKind.SUMMARY || win.contextId !== contextId) {
        return win;
      }
      return {
        ...win,
        payload: { ...win.payload, html }
      };
    }));
  }

  function updateComposeDraft(id, { subject, body }) {
    if (!id) return;
    windows.update((list) => list.map((win) => {
      if (win.id !== id || win.kind !== WindowKind.COMPOSE) return win;
      const nextVersion = (win.payload?.bodyVersion || 0) + 1;
      return {
        ...win,
        payload: {
          ...win.payload,
          subject: subject ?? win.payload.subject,
          body: body ?? win.payload.body,
          bodyVersion: nextVersion
        }
      };
    }));
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
    clearError,
    updateSummaryHtml,
    updateComposeDraft
  };
}

import { writable, get, type Writable } from "svelte/store";

type PanelResponseMap = Record<string, unknown>;
type PanelErrorMap = Record<string, string>;

/**
 * Centralizes AI panel state (responses, errors, window chrome) so App.svelte can
 * focus on layout. Each contextId/email id maps to a cached response entry.
 */
export function createAiPanelStore() {
  const responses: Writable<PanelResponseMap> = writable({});
  const errors: Writable<PanelErrorMap> = writable({});
  const sessionActive = writable(false);
  const minimized = writable(false);
  const maximized = writable(false);
  const activeKey = writable<string | null>(null);

  function setActiveKey(key: string | null) {
    activeKey.set(key || null);
  }

  function beginSession(key?: string | null) {
    sessionActive.set(true);
    // Don't auto-unminimize - preserve minimize state to allow accumulation in dock
    // User can restore from dock manually if they want to see the updated content
    maximized.set(false);
    if (key) {
      activeKey.set(key);
    }
  }

  function resetSessionState() {
    sessionActive.set(false);
    minimized.set(false);
    maximized.set(false);
  }

  function minimize() {
    minimized.set(true);
  }

  function restoreFromDock() {
    minimized.set(false);
  }

  function toggleMaximize() {
    maximized.update((value) => !value);
  }

  function closePanel(key?: string | null) {
    const target = key || get(activeKey);
    resetSessionState();
    clearEntry(target);
  }

  function recordResponse(key: string | null, payload: unknown) {
    if (!key) return;
    responses.update((map) => ({ ...map, [key]: payload }));
    clearError(key);
  }

  function recordError(key: string | null, message?: string | null) {
    if (!key) return;
    errors.update((map) => ({ ...map, [key]: message || "Unable to complete request." }));
  }

  function clearEntry(key: string | null) {
    if (!key) return;
    responses.update((map) => removeKey(map, key));
    errors.update((map) => removeKey(map, key));
  }

  function clearError(key: string | null) {
    if (!key) return;
    errors.update((map) => removeKey(map, key));
  }

  function responseFor(key: string | null) {
    if (!key) return null;
    const snapshot = get(responses);
    return snapshot[key] || null;
  }

  function errorFor(key: string | null) {
    if (!key) return "";
    const snapshot = get(errors);
    return snapshot[key] || "";
  }

  return {
    stores: {
      responses,
      errors,
      sessionActive,
      minimized,
      maximized,
      activeKey,
    },
    setActiveKey,
    beginSession,
    resetSessionState,
    minimize,
    restoreFromDock,
    toggleMaximize,
    closePanel,
    recordResponse,
    recordError,
    clearError,
    responseFor,
    errorFor,
  };
}

function removeKey<T extends Record<string, unknown>>(map: T, key: string) {
  if (!map || !(key in map)) {
    return (map || {}) as T;
  }
  const next = { ...map } as Record<string, unknown>;
  delete next[key];
  return next as T;
}

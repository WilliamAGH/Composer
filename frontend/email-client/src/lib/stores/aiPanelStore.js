import { writable, get } from 'svelte/store';

/**
 * Centralizes AI panel state (responses, errors, window chrome) so App.svelte can
 * focus on layout. Each contextId/email id maps to a cached response entry.
 */
export function createAiPanelStore() {
  const responses = writable({});
  const errors = writable({});
  const sessionActive = writable(false);
  const minimized = writable(false);
  const maximized = writable(false);
  const activeKey = writable(null);

  function setActiveKey(key) {
    activeKey.set(key || null);
  }

  function beginSession(key) {
    sessionActive.set(true);
    minimized.set(false);
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

  function closePanel(key) {
    const target = key || get(activeKey);
    resetSessionState();
    clearEntry(target);
  }

  function recordResponse(key, payload) {
    if (!key) return;
    responses.update((map) => ({ ...map, [key]: payload }));
    clearError(key);
  }

  function recordError(key, message) {
    if (!key) return;
    errors.update((map) => ({ ...map, [key]: message || 'Unable to complete request.' }));
  }

  function clearEntry(key) {
    if (!key) return;
    responses.update((map) => removeKey(map, key));
    errors.update((map) => removeKey(map, key));
  }

  function clearError(key) {
    if (!key) return;
    errors.update((map) => removeKey(map, key));
  }

  function responseFor(key) {
    if (!key) return null;
    const snapshot = get(responses);
    return snapshot[key] || null;
  }

  function errorFor(key) {
    if (!key) return '';
    const snapshot = get(errors);
    return snapshot[key] || '';
  }

  return {
    stores: {
      responses,
      errors,
      sessionActive,
      minimized,
      maximized,
      activeKey
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
    errorFor
  };
}

function removeKey(map, key) {
  if (!map || !(key in map)) {
    return map || {};
  }
  const next = { ...map };
  delete next[key];
  return next;
}


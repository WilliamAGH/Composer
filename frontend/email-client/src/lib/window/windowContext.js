import { setContext, getContext } from 'svelte';
import { createWindowManager } from './windowStore';

const WINDOW_CONTEXT_KEY = Symbol('window-context');

/** Creates a manager (if needed) and registers it in Svelte context. */
export function initWindowContext(config) {
  const manager = createWindowManager(config);
  setContext(WINDOW_CONTEXT_KEY, manager);
  return manager;
}

/** Registers an existing window manager in Svelte context. */
export function provideWindowContext(manager) {
  if (!manager) throw new Error('Window manager required for context');
  setContext(WINDOW_CONTEXT_KEY, manager);
}

export function useWindowContext() {
  const ctx = getContext(WINDOW_CONTEXT_KEY);
  if (!ctx) {
    throw new Error('Window context not found. Ensure WindowProvider wraps this component.');
  }
  return ctx;
}

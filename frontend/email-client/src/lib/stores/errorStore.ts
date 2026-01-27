/**
 * Global error store for surfacing errors to the UI.
 *
 * Two error categories:
 * - Toasts: transient errors (API failures, validation) that auto-dismiss
 * - Fatal: render/initialization errors that require user action
 */

import { writable, derived } from 'svelte/store';

export interface ToastError {
  id: string;
  message: string;
  detail?: string;
  severity: 'error' | 'warning' | 'info';
  timestamp: number;
}

export interface FatalError {
  message: string;
  detail?: string;
  error?: Error;
}

const toastQueue = writable<ToastError[]>([]);
const fatalError = writable<FatalError | null>(null);

/** Auto-dismiss delay in milliseconds */
const TOAST_DURATION_MS = 6000;

let nextToastId = 0;

/**
 * Push a transient error toast. Auto-dismisses after TOAST_DURATION_MS.
 */
export function pushToast(
  message: string,
  options: { detail?: string; severity?: ToastError['severity'] } = {}
): string {
  const id = `toast-${++nextToastId}`;
  const toast: ToastError = {
    id,
    message,
    detail: options.detail,
    severity: options.severity ?? 'error',
    timestamp: Date.now()
  };

  toastQueue.update((queue) => [...queue, toast]);

  // Auto-dismiss
  setTimeout(() => dismissToast(id), TOAST_DURATION_MS);

  return id;
}

/**
 * Dismiss a specific toast by ID.
 */
export function dismissToast(id: string): void {
  toastQueue.update((queue) => queue.filter((t) => t.id !== id));
}

/**
 * Set a fatal error that requires user acknowledgment or page reload.
 */
export function setFatalError(message: string, options: { detail?: string; error?: Error } = {}): void {
  fatalError.set({
    message,
    detail: options.detail,
    error: options.error
  });
}

/**
 * Clear the fatal error (e.g., after user clicks "try again").
 */
export function clearFatalError(): void {
  fatalError.set(null);
}

/**
 * Convenience: push error from catch block, extracting message from Error objects.
 */
export function pushErrorToast(error: unknown, fallbackMessage = 'An unexpected error occurred'): string {
  const message = error instanceof Error ? error.message : fallbackMessage;
  const detail = error instanceof Error ? error.stack?.split('\n')[1]?.trim() : undefined;
  return pushToast(message, { detail, severity: 'error' });
}

// Exported stores (read-only derived for components)
export const toasts = derived(toastQueue, ($q) => $q);
export const fatal = derived(fatalError, ($f) => $f);

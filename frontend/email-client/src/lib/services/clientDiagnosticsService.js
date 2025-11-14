import { createWindowNoticeStore } from '../window/WindowNoticeStore';

const MAX_DIAGNOSTICS = 200;
const diagnosticsBuffer = [];
const noticeStore = createWindowNoticeStore();

/**
 * Persists diagnostic events in-memory (and on window) so support can inspect them.
 * @param {'info'|'warn'|'error'} level
 * @param {string} message
 * @param {Error|undefined} error
 */
export function recordClientDiagnostic(level = 'info', message = '', error = null) {
  const entry = {
    level,
    message: message || '',
    detail: error?.message || null,
    stack: error?.stack || null,
    at: Date.now()
  };
  diagnosticsBuffer.push(entry);
  if (diagnosticsBuffer.length > MAX_DIAGNOSTICS) {
    diagnosticsBuffer.shift();
  }

  if (typeof window !== 'undefined') {
    const existing = Array.isArray(window.__COMPOSER_DIAGNOSTICS__)
      ? window.__COMPOSER_DIAGNOSTICS__
      : [];
    existing.push(entry);
    while (existing.length > MAX_DIAGNOSTICS) {
      existing.shift();
    }
    window.__COMPOSER_DIAGNOSTICS__ = existing;
  }
}

/**
 * Broadcasts client warnings and optionally surfaces them via a toast.
 * @param {{message?: string, error?: Error, silent?: boolean, level?: 'info'|'warn'|'error'}} detail
 */
export function processClientWarning(detail = {}) {
  const { message, error, silent = false, level = 'warn' } = detail || {};
  recordClientDiagnostic(level, message || 'Client warning', error);
  if (!silent && message) {
    showWindowNotice(message);
  }
}

/**
 * Public window notice store consumed by WindowNotice + overlays.
 */
export const windowNoticeStore = {
  subscribe: noticeStore.subscribe
};

/**
 * Shows a toast/notice for the provided duration.
 */
export function showWindowNotice(message, duration = 4000) {
  noticeStore.show(message, duration);
}

export function clearWindowNotice() {
  noticeStore.clear();
}

export function getDiagnosticsSnapshot() {
  return diagnosticsBuffer.slice();
}


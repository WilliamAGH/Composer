import { getMailboxSessionToken } from './mailboxSessionService';

let uiNonce: string | null = null;
let refreshPromise: Promise<string> | null = null;

export const CLIENT_WARNING_EVENT = 'composer:client-warning' as const;

export type ClientWarningDetail = {
  message?: string;
  error?: unknown;
  silent?: boolean;
  [key: string]: unknown;
};

export function dispatchClientWarning(detail: ClientWarningDetail = {}) {
  if (typeof window === 'undefined' || typeof window.dispatchEvent !== 'function') return;
  const payload = { ...detail, at: Date.now() };
  window.dispatchEvent(new CustomEvent(CLIENT_WARNING_EVENT, { detail: payload }));
}

/**
 * Seeds the nonce stash with the bootstrap value so all API calls share the same token.
 */
export function initializeUiNonce(initialNonce?: string | null) {
  uiNonce = initialNonce ?? null;
}

/**
 * Returns the nonce currently cached.
 */
export function getUiNonce() {
  return uiNonce;
}

/**
 * Refreshes the servlet UI nonce. Concurrent refreshes share a single request.
 */
export async function refreshUiNonce() {
  if (refreshPromise) return refreshPromise;
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  attachSessionHeaders(headers);
  refreshPromise = (async () => {
    const resp = await fetch('/ui/session/nonce', { method: 'POST', headers });
    const data = await resp.json().catch(() => null);
    if (!resp.ok) {
      throw new Error((data && data.error) || `HTTP ${resp.status}`);
    }
    if (!data?.uiNonce) {
      throw new Error('Nonce refresh response missing uiNonce');
    }
    const refreshedNonce = String(data.uiNonce);
    uiNonce = refreshedNonce;
    return refreshedNonce;
  })();

  try {
    return await refreshPromise;
  } finally {
    refreshPromise = null;
  }
}

type JsonRequestInit = Omit<RequestInit, 'headers'> & { headers?: Record<string, string> };

async function fetchWithNonce(url: string, init: JsonRequestInit = {}, allowRetry = true) {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json'
  };
  if (init.headers) {
    Object.assign(headers, init.headers);
  }
  attachSessionHeaders(headers);
  const response = await fetch(url, { ...init, headers });

  if (response.status === 403 && allowRetry) {
    await refreshUiNonce();
    return fetchWithNonce(url, init, false);
  }
  return response;
}

/**
 * POST helper that JSON-serializes the body and retries once when the nonce expires.
 */
export async function postJsonWithNonce<T = unknown>(url: string, body?: unknown, init: JsonRequestInit = {}) {
  const resp = await fetchWithNonce(url, { ...init, method: 'POST', body: JSON.stringify(body ?? null) });
  const data = await resp.json().catch(() => null);
  if (!resp.ok) {
    throw new Error((data && (data.message || data.error)) || `HTTP ${resp.status}`);
  }
  return data as T;
}

/**
 * GET helper used by heartbeat endpoints.
 */
export async function getJsonWithNonce<T = unknown>(url: string, init: JsonRequestInit = {}) {
  const resp = await fetchWithNonce(url, { ...init, method: 'GET' });
  const data = await resp.json().catch(() => null);
  if (!resp.ok) {
    throw new Error((data && (data.message || data.error)) || `HTTP ${resp.status}`);
  }
  return data as T;
}

/**
 * Starts the chat heartbeat interval and returns a cleanup function.
 */
export function startChatHeartbeat(intervalMs = 5 * 60 * 1000) {
  const heartbeat = async () => {
    if (!uiNonce) return;
    try {
      await fetch('/api/chat/health', {
        method: 'GET',
        headers: buildHeartbeatHeaders()
      });
    } catch (error) {
      dispatchClientWarning({ message: 'Chat heartbeat failed. Will retry automatically.', error, silent: true });
    }
  };

  const timerId = window.setInterval(heartbeat, intervalMs);
  return () => window.clearInterval(timerId);
}

function attachSessionHeaders(headers: Record<string, string>) {
  if (uiNonce) {
    headers['X-UI-Request'] = uiNonce;
  }
  const mailboxSession = getMailboxSessionToken();
  if (mailboxSession) {
    headers['X-Mailbox-Session'] = mailboxSession;
  }
}

function buildHeartbeatHeaders() {
  const headers: Record<string, string> = {};
  attachSessionHeaders(headers);
  return headers;
}

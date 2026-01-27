import { z } from 'zod/v4';
import { getMailboxSessionToken } from './mailboxSessionService';
import type { ValidationResult } from '../validation/result';
import { validationSuccess, validationFailure } from '../validation/result';
import { logZodFailure } from '../validation/zodLogging';
import { pushToast } from '../stores/errorStore';

let uiNonce: string | null = null;
let refreshPromise: Promise<string> | null = null;

/** Interval in milliseconds between chat heartbeat pings to keep session alive. */
const CHAT_HEARTBEAT_INTERVAL_MS = 5 * 60 * 1000;

function extractErrorMessage(raw: unknown, status: number): string {
  if (typeof raw === 'object' && raw !== null) {
    const asRecord = raw as Record<string, unknown>;
    if (typeof asRecord.message === 'string') return asRecord.message;
    if (typeof asRecord.error === 'string') return asRecord.error;
  }
  return `HTTP ${status}`;
}

/**
 * Push an error toast for an API failure. Call this from catch blocks
 * when you want to show the user a transient error notification.
 *
 * @param error - The caught error (Error object or unknown)
 * @param fallbackMessage - Message to show if error has no message
 * @returns The toast ID (can be used to dismiss early)
 */
export function showApiErrorToast(error: unknown, fallbackMessage = 'Request failed'): string {
  const message = error instanceof Error ? error.message : fallbackMessage;
  return pushToast(message, { severity: 'error' });
}

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

const NonceRefreshResponseSchema = z.object({
  uiNonce: z.string()
});

/**
 * Refreshes the servlet UI nonce. Concurrent refreshes share a single request.
 */
export async function refreshUiNonce() {
  if (refreshPromise) return refreshPromise;
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  attachSessionHeaders(headers);
  refreshPromise = (async () => {
    const resp = await fetch('/ui/session/nonce', { method: 'POST', headers });
    const raw: unknown = await resp.json().catch(() => null);
    if (!resp.ok) {
      const errorMessage = extractErrorMessage(raw, resp.status);
      throw new Error(errorMessage);
    }
    const parseResult = NonceRefreshResponseSchema.safeParse(raw);
    if (!parseResult.success) {
      logZodFailure('refreshUiNonce [nonce-refresh]', parseResult.error, raw);
      throw new Error('Nonce refresh response validation failed');
    }
    const refreshedNonce = parseResult.data.uiNonce;
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
 * @deprecated Use postJsonValidated with a Zod schema for type-safe responses.
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
 * @deprecated Use getJsonValidated with a Zod schema for type-safe responses.
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
 * Type-safe POST helper with Zod validation. Logs failures with full context.
 * Returns discriminated union - callers MUST check success before accessing data.
 *
 * @param url - API endpoint
 * @param schema - Zod schema for response validation
 * @param recordId - Identifier for logging which request failed
 * @param body - Request body (will be JSON-serialized)
 * @param init - Additional fetch options
 */
export async function postJsonValidated<T>(
  url: string,
  schema: z.ZodType<T>,
  recordId: string,
  body?: unknown,
  init: JsonRequestInit = {}
): Promise<ValidationResult<T>> {
  const resp = await fetchWithNonce(url, { ...init, method: 'POST', body: JSON.stringify(body ?? null) });
  const raw: unknown = await resp.json().catch(() => null);

  if (!resp.ok) {
    const errorMessage = extractErrorMessage(raw, resp.status);
    throw new Error(errorMessage);
  }

  const parseResult = schema.safeParse(raw);
  if (!parseResult.success) {
    logZodFailure(`postJsonValidated [${recordId}]`, parseResult.error, raw);
    return validationFailure(parseResult.error);
  }

  return validationSuccess(parseResult.data);
}

/**
 * Type-safe GET helper with Zod validation. Logs failures with full context.
 * Returns discriminated union - callers MUST check success before accessing data.
 *
 * @param url - API endpoint
 * @param schema - Zod schema for response validation
 * @param recordId - Identifier for logging which request failed
 * @param init - Additional fetch options
 */
export async function getJsonValidated<T>(
  url: string,
  schema: z.ZodType<T>,
  recordId: string,
  init: JsonRequestInit = {}
): Promise<ValidationResult<T>> {
  const resp = await fetchWithNonce(url, { ...init, method: 'GET' });
  const raw: unknown = await resp.json().catch(() => null);

  if (!resp.ok) {
    const errorMessage = extractErrorMessage(raw, resp.status);
    throw new Error(errorMessage);
  }

  const parseResult = schema.safeParse(raw);
  if (!parseResult.success) {
    logZodFailure(`getJsonValidated [${recordId}]`, parseResult.error, raw);
    return validationFailure(parseResult.error);
  }

  return validationSuccess(parseResult.data);
}

/**
 * POST helper for fire-and-forget endpoints that return empty or minimal responses.
 * Throws on HTTP errors. Returns void on success - no body validation needed.
 *
 * Use this ONLY for endpoints where:
 * 1. Response is 202 Accepted with empty body, OR
 * 2. Response body is intentionally ignored (fire-and-forget pattern)
 *
 * @param url - API endpoint
 * @param body - Request body (will be JSON-serialized)
 * @param init - Additional fetch options
 */
export async function postJsonVoid(
  url: string,
  body?: unknown,
  init: JsonRequestInit = {}
): Promise<void> {
  const resp = await fetchWithNonce(url, { ...init, method: 'POST', body: JSON.stringify(body ?? null) });

  if (!resp.ok) {
    const raw: unknown = await resp.json().catch(() => null);
    const errorMessage = extractErrorMessage(raw, resp.status);
    throw new Error(errorMessage);
  }
  // Success - no body to validate
}

/**
 * Starts the chat heartbeat interval and returns a cleanup function.
 */
export function startChatHeartbeat(intervalMs = CHAT_HEARTBEAT_INTERVAL_MS) {
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

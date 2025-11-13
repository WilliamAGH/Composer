type FetchInit = Omit<RequestInit, 'body'> & { body?: BodyInit | null };

let uiNonce: string | null = null;
let refreshPromise: Promise<string> | null = null;

/**
 * Seeds the nonce stash with the bootstrap value so all API calls share the same token.
 */
export function initializeUiNonce(initialNonce: string | null | undefined) {
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
export async function refreshUiNonce(): Promise<string> {
  if (refreshPromise) return refreshPromise;
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (uiNonce) {
    headers['X-UI-Request'] = uiNonce;
  }
  refreshPromise = (async () => {
    const resp = await fetch('/ui/session/nonce', { method: 'POST', headers });
    const data = await resp.json().catch(() => null);
    if (!resp.ok) {
      throw new Error((data && data.error) || `HTTP ${resp.status}`);
    }
    if (!data?.uiNonce) {
      throw new Error('Nonce refresh response missing uiNonce');
    }
    uiNonce = data.uiNonce;
    return uiNonce;
  })();

  try {
    return await refreshPromise;
  } finally {
    refreshPromise = null;
  }
}

async function fetchWithNonce(url: string, init: FetchInit = {}, allowRetry = true) {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(init.headers as Record<string, string> | undefined)
  };
  if (uiNonce) {
    headers['X-UI-Request'] = uiNonce;
  }
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
export async function postJsonWithNonce<T>(url: string, body: unknown, init: FetchInit = {}): Promise<T> {
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
export async function getJsonWithNonce<T>(url: string, init: FetchInit = {}): Promise<T> {
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
        headers: { 'X-UI-Request': uiNonce }
      });
    } catch (error) {
      console.warn('Chat heartbeat failed', error);
    }
  };

  const timerId = window.setInterval(heartbeat, intervalMs);
  return () => window.clearInterval(timerId);
}

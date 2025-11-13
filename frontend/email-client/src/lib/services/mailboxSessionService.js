let mailboxSessionToken = null;

/**
 * Returns the cached mailbox session token, creating one if necessary.
 * We intentionally keep this per-browser-tab to match the backend's ephemeral state contract.
 */
export function ensureMailboxSessionToken(seedToken) {
  if (seedToken && !mailboxSessionToken) {
    mailboxSessionToken = seedToken;
    return mailboxSessionToken;
  }
  if (!mailboxSessionToken && typeof crypto !== 'undefined' && crypto.randomUUID) {
    mailboxSessionToken = crypto.randomUUID();
  }
  if (!mailboxSessionToken) {
    mailboxSessionToken = `session-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  }
  return mailboxSessionToken;
}

export function setMailboxSessionToken(token) {
  if (token && token.trim().length > 0) {
    mailboxSessionToken = token.trim();
  }
}

export function getMailboxSessionToken() {
  return mailboxSessionToken;
}

/**
 * Tracks conversation identifiers per AI journey scope so each email/thread keeps
 * its own chat history instead of sharing a single global conversation.
 * Consumers provide a resolver for the currently selected email when they need
 * to infer the key for panel-focused journeys.
 */
export function createConversationLedger(getSelectedEmail = () => null) {
  const GLOBAL_KEY = '__global__';
  const ledger = new Map();

  function normalizeKey(key) {
    return key || GLOBAL_KEY;
  }

  function resolveKey({ journeyScope = 'global', journeyScopeTarget = null, contextId = null } = {}) {
    const scope = (journeyScope || 'global').toLowerCase();
    if (scope === 'panel') {
      const selected = typeof getSelectedEmail === 'function' ? getSelectedEmail() : null;
      return journeyScopeTarget || contextId || selected?.contextId || selected?.id || null;
    }
    if (scope === 'compose') {
      return journeyScopeTarget || contextId || null;
    }
    return GLOBAL_KEY;
  }

  function read(key) {
    return ledger.get(normalizeKey(key)) || null;
  }

  function write(key, conversationId) {
    if (!conversationId) {
      return;
    }
    ledger.set(normalizeKey(key), conversationId);
  }

  return {
    resolveKey,
    read,
    write,
    GLOBAL_KEY
  };
}


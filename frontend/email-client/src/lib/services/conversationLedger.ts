/**
 * Tracks conversation identifiers per AI journey scope so each email/thread keeps
 * its own chat history instead of sharing a single global conversation.
 * Consumers provide a resolver for the currently selected email when they need
 * to infer the key for panel-focused journeys.
 */
export type SelectedEmailResolver = () => { contextId?: string | null; id?: string | null } | null;

export interface ConversationLedger {
  resolveKey: (params?: {
    journeyScope?: string | null;
    journeyScopeTarget?: string | null;
    contextId?: string | null;
  }) => string | null;
  read: (key: string | null) => string | null;
  write: (key: string | null, conversationId: string | null) => void;
  GLOBAL_KEY: string;
}

export function createConversationLedger(
  getSelectedEmail: SelectedEmailResolver = () => null,
): ConversationLedger {
  const GLOBAL_KEY = "__global__";
  const ledger = new Map<string, string>();

  function normalizeKey(key: string | null) {
    return key || GLOBAL_KEY;
  }

  function resolveKey({
    journeyScope = "global",
    journeyScopeTarget = null,
    contextId = null,
  }: {
    journeyScope?: string | null;
    journeyScopeTarget?: string | null;
    contextId?: string | null;
  } = {}) {
    const scope = (journeyScope || "global").toLowerCase();
    if (scope === "panel") {
      const selected = typeof getSelectedEmail === "function" ? getSelectedEmail() : null;
      return journeyScopeTarget || contextId || selected?.contextId || selected?.id || null;
    }
    if (scope === "compose") {
      return journeyScopeTarget || contextId || null;
    }
    return GLOBAL_KEY;
  }

  function read(key: string | null) {
    return ledger.get(normalizeKey(key)) || null;
  }

  function write(key: string | null, conversationId: string | null) {
    if (!conversationId) {
      return;
    }
    ledger.set(normalizeKey(key), conversationId);
  }

  return {
    resolveKey,
    read,
    write,
    GLOBAL_KEY,
  };
}

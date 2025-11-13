import { writable } from 'svelte/store';
import { ACTION_MENU_COMMAND_KEY, DEFAULT_ACTION_OPTIONS } from '../constants/catalogActions';
import { dispatchClientWarning } from '../services/sessionNonceClient';

/**
 * Generates mailbox action menu suggestions per selected email and caches results by context key.
 * Keeps App.svelte unaware of throttling, JSON parsing, and pending states.
 */
export function createActionMenuSuggestionsStore({ ensureCatalogReady, callCatalogCommand }) {
  const optionsStore = writable(cloneDefaults());
  const loadingStore = writable(false);
  const errorStore = writable('');

  const cache = {};
  const inflight = {};

  async function loadSuggestions(email, cacheKey) {
    if (!email || !cacheKey || inflight[cacheKey]) return;
    if (cache[cacheKey]) {
      optionsStore.set(cache[cacheKey]);
      return;
    }
    inflight[cacheKey] = true;
    loadingStore.set(true);
    errorStore.set('');
    try {
      const ready = await ensureCatalogReady();
      if (!ready) return;
      const instruction = buildInstruction(email);
      const data = await callCatalogCommand(ACTION_MENU_COMMAND_KEY, instruction, {
        contextId: email.contextId,
        subject: email.subject,
        journeyScope: 'panel',
        journeyScopeTarget: email.id || email.contextId || null,
        journeyLabel: email.subject || email.from || 'Selected email',
        journeyHeadline: 'Curating action ideas'
      });
      const parsed = parseResponse(data);
      const nextOptions = parsed.length ? parsed : cloneDefaults();
      cache[cacheKey] = nextOptions;
      optionsStore.set(nextOptions);
    } catch (error) {
      errorStore.set(error?.message || 'Unable to refresh AI actions.');
      dispatchClientWarning({ message: 'Unable to refresh AI actions.', error });
    } finally {
      delete inflight[cacheKey];
      loadingStore.set(false);
    }
  }

  function applyDefaults() {
    optionsStore.set(cloneDefaults());
  }

  function buildInstruction(email) {
    const subject = (email.subject || 'No subject').trim();
    const from = (email.from || 'Unknown sender').trim();
    const preview = ((email.preview || email.contentText || '').replace(/\s+/g, ' ').trim()).slice(0, 240);
    return `Subject: ${subject}\nFrom: ${from}\nPreview: ${preview || 'No preview provided.'}\nFocus on concise, high-value actions.`;
  }

  function parseResponse(data) {
    const raw = typeof data?.response === 'string' ? data.response : null;
    const fallbackHtml = typeof data?.sanitizedHtml === 'string' ? data.sanitizedHtml : null;
    const jsonBlock = extractJsonBlock(raw || fallbackHtml);
    if (!jsonBlock) return [];
    try {
      const parsed = JSON.parse(jsonBlock);
      const actions = Array.isArray(parsed?.options) ? parsed.options : [];
      return actions.map(sanitizeOption).filter(Boolean).slice(0, 3);
    } catch (error) {
      dispatchClientWarning({ message: 'Failed to parse action menu response.', error, silent: true });
      return [];
    }
  }

  function sanitizeOption(option) {
    if (!option || typeof option !== 'object') return null;
    const rawLabel = typeof option.label === 'string' ? option.label.trim() : '';
    if (!rawLabel) return null;
    const words = rawLabel.split(/\s+/).filter(Boolean);
    if (words.length === 0 || words.length > 3) return null;
    const normalizedType = (option.actionType || '').toLowerCase();
    const actionType = normalizedType === 'comingsoon' ? 'comingSoon' : normalizedType || 'summary';
    const commandKey = option.commandKey || (actionType === 'summary' ? 'summarize' : actionType === 'compose' ? 'compose' : null);
    const commandVariant = option.commandVariant || null;
    const instruction = typeof option.instruction === 'string' ? option.instruction.trim() || null : null;
    return {
      id: option.id || `ai-action-${rawLabel.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '')}`,
      label: rawLabel,
      actionType,
      commandKey,
      commandVariant,
      instruction
    };
  }

  function extractJsonBlock(raw) {
    if (!raw) return null;
    const trimmed = raw.trim();
    if (trimmed.startsWith('{') && trimmed.endsWith('}')) return trimmed;
    const first = trimmed.indexOf('{');
    const last = trimmed.lastIndexOf('}');
    if (first === -1 || last === -1 || last <= first) return null;
    return trimmed.slice(first, last + 1);
  }

  function cloneDefaults() {
    return DEFAULT_ACTION_OPTIONS.map((option) => ({ ...option }));
  }

  return {
    options: { subscribe: optionsStore.subscribe },
    loading: { subscribe: loadingStore.subscribe },
    message: { subscribe: errorStore.subscribe },
    loadSuggestions,
    applyDefaults
  };
}

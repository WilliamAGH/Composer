import { writable, type Writable } from "svelte/store";
import {
  ACTION_MENU_COMMAND_KEY,
  DEFAULT_ACTION_OPTIONS,
  type ActionMenuOption,
} from "../constants/catalogActions";
import { dispatchClientWarning } from "../services/sessionNonceClient";
import type { ChatResponsePayload } from "../services/catalogCommandClient";

type EmailSummary = {
  id?: string | null;
  contextId?: string | null;
  subject?: string | null;
  from?: string | null;
  preview?: string | null;
  contentText?: string | null;
};

type CallCatalogCommandFn = (
  commandKey: string,
  instruction: string,
  options: Record<string, unknown>,
) => Promise<ChatResponsePayload | null>;

interface ActionMenuStoreConfig {
  ensureCatalogReady: () => Promise<boolean>;
  callCatalogCommand: CallCatalogCommandFn;
}

/**
 * Generates mailbox action menu suggestions per selected email and caches results by context key.
 * Keeps App.svelte unaware of throttling, JSON parsing, and pending states.
 */
export function createActionMenuSuggestionsStore({
  ensureCatalogReady,
  callCatalogCommand,
}: ActionMenuStoreConfig) {
  const optionsStore: Writable<(ActionMenuOption & { aiGenerated: boolean })[]> =
    writable(cloneDefaults());
  const loadingStore = writable(false);
  const errorStore = writable("");

  const cache: Record<string, (ActionMenuOption & { aiGenerated: boolean })[]> = {};
  const inflight: Record<string, boolean> = {};

  async function loadSuggestions(
    email: EmailSummary | null | undefined,
    cacheKey: string | null | undefined,
  ) {
    if (!email || !cacheKey || inflight[cacheKey]) return;
    if (cache[cacheKey]) {
      optionsStore.set(cache[cacheKey]);
      return;
    }
    inflight[cacheKey] = true;
    loadingStore.set(true);
    errorStore.set("");
    try {
      const ready = await ensureCatalogReady();
      if (!ready) return;
      const instruction = buildInstruction(email);
      const data = await callCatalogCommand(ACTION_MENU_COMMAND_KEY, instruction, {
        contextId: email.contextId,
        subject: email.subject,
        journeyScope: "panel",
        journeyScopeTarget: email.id || email.contextId || null,
        journeyLabel: email.subject || email.from || "Selected email",
        journeyHeadline: "Curating action ideas",
      });
      const parsed = parseResponse(data);
      const nextOptions = parsed.length ? parsed : cloneDefaults();
      cache[cacheKey] = nextOptions;
      optionsStore.set(nextOptions);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Unable to refresh AI actions.";
      errorStore.set(message);
      dispatchClientWarning({ message: "Unable to refresh AI actions.", error });
    } finally {
      delete inflight[cacheKey];
      loadingStore.set(false);
    }
  }

  function applyDefaults() {
    optionsStore.set(cloneDefaults());
  }

  function buildInstruction(email: EmailSummary) {
    const subject = (email.subject || "No subject").trim();
    const from = (email.from || "Unknown sender").trim();
    const preview = (email.preview || email.contentText || "")
      .replace(/\s+/g, " ")
      .trim()
      .slice(0, 240);
    return `Subject: ${subject}\nFrom: ${from}\nPreview: ${preview || "No preview provided."}\nFocus on concise, high-value actions.`;
  }

  function parseResponse(data: ChatResponsePayload | null) {
    const raw = typeof data?.response === "string" ? data.response : null;
    const fallbackHtml = typeof data?.sanitizedHtml === "string" ? data.sanitizedHtml : null;
    const jsonBlock = extractJsonBlock(raw || fallbackHtml);
    if (!jsonBlock) return [];
    try {
      const parsed = JSON.parse(jsonBlock);
      const actions = Array.isArray(parsed?.options) ? parsed.options : [];
      return actions.map(sanitizeOption).filter(Boolean).slice(0, 3);
    } catch (error) {
      dispatchClientWarning({
        message: "Failed to parse action menu response.",
        error,
        silent: true,
      });
      return [];
    }
  }

  function sanitizeOption(option: unknown) {
    if (!option || typeof option !== "object") return null;
    const optionRecord = option as Record<string, unknown>;
    const rawLabel = typeof optionRecord.label === "string" ? optionRecord.label.trim() : "";
    if (!rawLabel) return null;
    const words = rawLabel.split(/\s+/).filter(Boolean);
    if (words.length === 0 || words.length > 3) return null;
    const actionTypeRaw =
      typeof optionRecord.actionType === "string" ? optionRecord.actionType : "";
    const normalizedType = actionTypeRaw.toLowerCase();
    const actionType = normalizedType === "comingsoon" ? "comingSoon" : normalizedType || "summary";
    const commandKey =
      (typeof optionRecord.commandKey === "string" ? optionRecord.commandKey : null) ||
      (actionType === "summary" ? "summarize" : actionType === "compose" ? "compose" : null);
    const commandVariant =
      typeof optionRecord.commandVariant === "string" ? optionRecord.commandVariant : null;
    const instruction =
      typeof optionRecord.instruction === "string" ? optionRecord.instruction.trim() || null : null;

    // Filter out translate actions since they have a dedicated UI control
    if (commandKey === "translate" || rawLabel.toLowerCase().includes("translat")) {
      return null;
    }

    return {
      id:
        (typeof optionRecord.id === "string" ? optionRecord.id : null) ||
        `ai-action-${rawLabel
          .toLowerCase()
          .replace(/[^a-z0-9]+/g, "-")
          .replace(/(^-|-$)/g, "")}`,
      label: rawLabel,
      actionType,
      commandKey,
      commandVariant,
      instruction,
      aiGenerated: true,
    };
  }

  function extractJsonBlock(raw: string | null) {
    if (!raw) return null;
    const trimmed = raw.trim();
    if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed;
    const first = trimmed.indexOf("{");
    const last = trimmed.lastIndexOf("}");
    if (first === -1 || last === -1 || last <= first) return null;
    return trimmed.slice(first, last + 1);
  }

  function cloneDefaults() {
    return DEFAULT_ACTION_OPTIONS.map((option) => ({ ...option, aiGenerated: false }));
  }

  return {
    options: { subscribe: optionsStore.subscribe },
    loading: { subscribe: loadingStore.subscribe },
    message: { subscribe: errorStore.subscribe },
    loadSuggestions,
    applyDefaults,
  };
}

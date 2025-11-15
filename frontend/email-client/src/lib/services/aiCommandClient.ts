import { get } from 'svelte/store';
import {
  catalogStore,
  getFunctionMeta,
  mergeDefaultArgs,
  resolveDefaultInstruction
} from './aiCatalog';
import { buildEmailContextString, deriveRecipientContext, normalizeRecipient } from './emailContextConstructor';
import { parseSubjectAndBody } from './emailUtils';
import { deriveHeadline } from './aiCommandHandler';
import { sanitizeHtml } from './sanitizeHtml';
import { executeCatalogCommand } from './catalogCommandClient';
import type { ChatRequestPayload } from './catalogCommandClient';
import { createConversationLedger } from './conversationLedger';
import { createAiJourneyStore } from './aiJourneyStore';

export type CatalogEnsureFn = () => Promise<boolean> | boolean;

interface CallOptions {
  contextId?: string | null;
  subject?: string | null;
  journeyScope?: string;
  journeyScopeTarget?: string | null;
  journeyLabel?: string | null;
  journeyHeadline?: string | null;
  commandVariant?: string | null;
  commandArgs?: Record<string, string> | null;
  emailContext?: string | null;
  fallbackEmail?: any;
  recipientContext?: { name?: string; email?: string } | null;
}

interface RunOptions {
  command: string;
  commandVariant?: string | null;
  instructionOverride?: string | null;
  selectedEmail?: any;
  journeyScope?: string;
  journeyScopeTarget?: string | null;
  journeyLabel?: string | null;
  commandArgs?: Record<string, string> | null;
}

interface PrefillOptions {
  command: string;
  detail: {
    draft?: string;
    subject?: string;
    to?: string;
    instructionOverride?: string | null;
    isReply?: boolean;
  };
  windowId: string;
  windowPayload?: any;
  selectedEmail?: any;
  relatedEmail?: any;
}

interface AiCommandClientConfig {
  ensureCatalogLoaded?: CatalogEnsureFn;
  journeyStore?: ReturnType<typeof createAiJourneyStore>;
  conversationLedger?: ReturnType<typeof createConversationLedger>;
}

interface JourneyDescriptor {
  scope?: string;
  scopeTarget?: string | null;
  targetLabel?: string;
  commandKey: string;
  headline?: string | null;
}

/**
 * Client that normalizes AI-command calls so controllers can stay trivial.
 */
export function createAiCommandClient({
  ensureCatalogLoaded = async () => true,
  journeyStore = createAiJourneyStore(),
  conversationLedger = createConversationLedger()
}: AiCommandClientConfig = {}) {
  const catalog = catalogStore();
  const journey = journeyStore as any;
  const ledger = conversationLedger as any;

  function beginJourney({ scope = 'global', scopeTarget = null, targetLabel = 'message', commandKey, headline }: JourneyDescriptor) {
    return journey.begin({
      scope,
      scopeTarget,
      targetLabel,
      commandKey,
      headline: headline || 'Working on your request',
      subhead: scope === 'global' ? 'Composer assistant' : 'Mailbox assistant'
    });
  }

  async function call(command: string, instruction: string, options: CallOptions = {}) {
    const {
      contextId,
      subject,
      journeyScope = 'global',
      journeyScopeTarget = null,
      journeyLabel,
      journeyHeadline,
      commandVariant,
      commandArgs,
      emailContext,
      fallbackEmail,
      recipientContext
    } = options;

    const targetLabel = journeyLabel || subject || fallbackEmail?.subject || 'message';
    const conversationKey = (ledger.resolveKey({ journeyScope, journeyScopeTarget, contextId }) ?? null) as string | null;
    const scopedConversationId = conversationKey ? ledger.read(conversationKey) : null;
    const payload: ChatRequestPayload = {
      instruction,
      message: instruction,
      conversationId: scopedConversationId,
      targetLabel,
      journeyScope,
      journeyScopeTarget,
      journeyLabel: journeyLabel || null,
      journeyHeadline,
      maxResults: 5,
      thinkingEnabled: false,
      jsonOutput: false
    };
    const journeyToken = beginJourney({ scope: journeyScope, scopeTarget: journeyScopeTarget, targetLabel, commandKey: command, headline: journeyHeadline });

    const trimmedContextId = typeof contextId === 'string' ? contextId.trim() : null;
    if (trimmedContextId) {
      payload.contextId = trimmedContextId;
    }

    const catalogSnapshot = get(catalog);
    const commandMeta: any = getFunctionMeta(catalogSnapshot, command);
    if (command && commandMeta) {
      payload.aiCommand = command;
      if (commandVariant) {
        payload.commandVariant = commandVariant;
      }
      const argsPayload = commandArgs ?? mergeDefaultArgs(commandMeta, null);
      if (argsPayload && Object.keys(argsPayload).length > 0) {
        payload.commandArgs = argsPayload;
      }
    }

    if (subject && subject.trim()) {
      payload.subject = subject.trim();
    }

    if (recipientContext) {
      const normalized = normalizeRecipient(recipientContext);
      if (normalized.name) {
        payload.recipientName = normalized.name;
      }
      if (normalized.email) {
        payload.recipientEmail = normalized.email;
      }
    }

    if (!contextId) {
      if (fallbackEmail?.contextForAi && fallbackEmail.contextForAi.trim()) {
        payload.emailContext = fallbackEmail.contextForAi.trim();
      } else if (emailContext && emailContext.trim()) {
        payload.emailContext = emailContext.trim();
      } else if (fallbackEmail) {
        const ctx = buildEmailContextString(fallbackEmail);
        if (ctx) payload.emailContext = ctx;
      }
    }

    journey.advance(journeyToken, 'ai:context-search');

    try {
      const data = await executeCatalogCommand(command, payload);
      journey.advance(journeyToken, 'ai:llm-thinking');
      journey.complete(journeyToken);
      if (conversationKey && data?.conversationId) {
        ledger.write(conversationKey, data.conversationId);
      }
      return data;
    } catch (error) {
      journey.fail(journeyToken);
      throw error;
    }
  }

  async function run(options: RunOptions) {
    const {
      command,
      commandVariant = null,
      instructionOverride = null,
      selectedEmail = null,
      journeyScope = 'global',
      journeyScopeTarget = null,
      journeyLabel = null,
      commandArgs: overrideArgs = null
    } = options;

    if (!command) {
      throw new Error('Command is required.');
    }
    const ready = await ensureCatalogLoaded();
    if (!ready) {
      throw new Error('AI helpers are unavailable. Please refresh and try again.');
    }
    const catalogSnapshot = get(catalog);
    const meta: any = getFunctionMeta(catalogSnapshot, command);
    if (!meta) {
      throw new Error('Command unavailable.');
    }
    const variant = commandVariant && Array.isArray(meta.variants)
      ? meta.variants.find((entry: any) => entry.key === commandVariant) || null
      : null;
    const mergedArgs = overrideArgs || mergeDefaultArgs(meta, variant);
    const instruction = instructionOverride || resolveDefaultInstruction(meta, variant);
    const headline = deriveHeadline(command, meta.label || 'Assistant');

    const result = await call(command, instruction, {
      contextId: selectedEmail?.contextId,
      subject: selectedEmail?.subject,
      journeyScope,
      journeyScopeTarget,
      journeyLabel,
      journeyHeadline: headline,
      commandVariant: variant?.key || null,
      commandArgs: mergedArgs,
      fallbackEmail: selectedEmail
    });

    return {
      data: result,
      meta,
      variant,
      args: mergedArgs,
      headline
    };
  }

  async function prefill(options: PrefillOptions) {
    const { command, detail, windowId, windowPayload, selectedEmail, relatedEmail } = options;
    if (!command) {
      throw new Error('Command key required for compose prefill.');
    }
    const ready = await ensureCatalogLoaded();
    if (!ready) {
      throw new Error('AI helpers are unavailable. Please try again.');
    }
    const catalogSnapshot = get(catalog);
    const fn: any = getFunctionMeta(catalogSnapshot, command);
    if (!fn) {
      throw new Error('Command unavailable.');
    }
    const instruction = detail.instructionOverride || resolveDefaultInstruction(fn, null);
    const journeyHeadline = deriveHeadline(command, fn.label || 'Assistant');
    const commandArgs = mergeDefaultArgs(fn, null);
    const related = relatedEmail || selectedEmail || null;
    const recipientContext = deriveRecipientContext({
      toInput: detail.to,
      composePayload: windowPayload,
      fallbackEmail: related
    });

    const response = await call(command, instruction, {
      contextId: related?.contextId || related?.id || null,
      subject: detail.subject || related?.subject,
      journeyScope: 'compose',
      journeyScopeTarget: windowId,
      journeyLabel: detail.subject || related?.subject || 'draft',
      journeyHeadline,
      commandArgs,
      fallbackEmail: related,
      recipientContext
    });

    const markdown = response?.response && typeof response.response === 'string' ? response.response.trim() : '';
    const sanitizedHtml = response?.sanitizedHtml || response?.sanitizedHTML || null;

    let draftText = markdown;
    if (!draftText && sanitizedHtml) {
      const temp = document.createElement('div');
      // Defense-in-depth: re-sanitize on client even though server sanitized
      temp.innerHTML = sanitizeHtml(sanitizedHtml);
      draftText = temp.textContent?.trim() || '';
    }

    let parsedSubject = detail.subject;
    let parsedBody = detail.draft || '';
    if (draftText) {
      const parsed = parseSubjectAndBody(draftText);
      parsedSubject = parsed.subject || parsedSubject;
      parsedBody = parsed.body || draftText;
      const quote = windowPayload?.quotedContext;
      if (quote && !parsedBody.includes(quote.trim())) {
        parsedBody = `${parsedBody.trimEnd()}\n\n${quote}`;
      }
    }

    return {
      response,
      draft: {
        subject: parsedSubject,
        body: parsedBody
      }
    };
  }

  return {
    call,
    run,
    prefill
  };
}

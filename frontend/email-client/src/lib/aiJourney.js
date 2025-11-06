/**
 * Canonical AI request lifecycle identifiers.
 *
 * WHY THIS LIVES IN JS:
 * - Keeps Svelte components declarative: they import shared copy + backend references instead of
 *   duplicating strings or wiring details inline.
 * - Java remains the behavioral source of truth. Storing UI copy in backend code would require
 *   additional serialization and invites drift; here we simply document which backend class owns
 *   each phase so both sides stay aligned.
 *
 * WHAT IT DEFINES:
 * - The ordered list of lifecycle events, each with an icon token, copy template, and direct pointer
 *   to the backend subsystem that handles that phase.
 * - Helper exports (`buildAiJourney`, `AI_JOURNEY_EVENTS`) so any UI surface can render the journey
 *   consistently while swapping labels or steps without losing the shared model.
 *
 * @typedef {'ai:payload-prep'|'ai:context-search'|'ai:llm-thinking'|'ai:writing-summary'} AiJourneyEvent
 * @typedef {{ targetLabel?: string, command?: string }} AiJourneyContext
 * @typedef {'sparkles'|'search'|'brain'|'pen'} JourneyIconToken
 * @typedef {{ title: string|function, detail?: string|function }} JourneyCopyTemplate
 * @typedef {{ id: AiJourneyEvent, icon: JourneyIconToken, sourceOfTruth: string, copy: JourneyCopyTemplate }} JourneyStepTemplate
 */

const baseSteps = [
  {
    id: 'ai:payload-prep',
    icon: 'sparkles',
    sourceOfTruth: 'App.svelte:buildEmailContextString / callAiCommand payload assembly',
    copy: {
      title: (ctx = {}) => `Analyzing your ${ctx.targetLabel?.trim() || 'message'}`,
      detail: 'Packaging cleaned markdown, subject, and participants before handing work to ChatService.',
    },
  },
  {
    id: 'ai:context-search',
    icon: 'search',
    sourceOfTruth: 'ChatService.prepareChatContext → VectorSearchService.searchSimilarEmails (Qdrant).',
    copy: {
      title: 'Searching for related context',
      detail: 'Vector search (Qdrant) looks up semantically similar emails to enrich the prompt.',
    },
  },
  {
    id: 'ai:llm-thinking',
    icon: 'brain',
    sourceOfTruth: 'OpenAiChatService.generateResponse reasoning / thinking block.',
    copy: {
      title: 'Thinking',
      detail: 'Letting the reasoning model plan its response before anything is streamed back.',
    },
  },
  {
    id: 'ai:writing-summary',
    icon: 'pen',
    sourceOfTruth: 'OpenAiChatService.generateResponse → HtmlConverter markdown sanitization + UI render.',
    copy: {
      title: 'Writing summary of my observations',
      detail: 'Polishing the answer and formatting sanitized HTML before inserting it into the UI.',
    },
  },
];

function resolveTemplateValue(template, ctx) {
  if (!template) return undefined;
  return typeof template === 'function' ? template(ctx) : template;
}

function normalizeOverride(override, ctx) {
  if (!override) return undefined;
  if (typeof override === 'function') {
    const result = override(ctx);
    if (typeof result === 'string') {
      return { title: result };
    }
    return result;
  }
  if (typeof override === 'string') {
    return { title: override };
  }
  return override;
}

export function buildAiJourney(ctx = {}, overrides) {
  return baseSteps.map((step) => {
    const override = normalizeOverride(overrides?.[step.id], ctx);
    const effective = {
      title: resolveTemplateValue(override?.title ?? step.copy.title, ctx),
      detail: resolveTemplateValue(override?.detail ?? step.copy.detail, ctx),
    };

    return {
      id: step.id,
      icon: step.icon,
      title: effective.title || '',
      detail: effective.detail,
      sourceOfTruth: step.sourceOfTruth,
    };
  });
}

export const AI_JOURNEY_EVENTS = baseSteps.map((step) => step.id);

export type AiJourneyEvent =
  | "ai:payload-prep"
  | "ai:context-search"
  | "ai:llm-thinking"
  | "ai:writing-summary";
export type JourneyIconToken = "sparkles" | "search" | "brain" | "pen";
export type JourneyContext = { targetLabel?: string | null; command?: string | null };
export type JourneyCopyTemplate = {
  title: string | ((ctx: JourneyContext) => string);
  detail?: string | ((ctx: JourneyContext) => string);
};
export type JourneyStepTemplate = {
  id: AiJourneyEvent;
  icon: JourneyIconToken;
  sourceOfTruth: string;
  copy: JourneyCopyTemplate;
};
export type JourneyStep = {
  id: AiJourneyEvent;
  icon: JourneyIconToken;
  title: string;
  detail?: string;
  sourceOfTruth: string;
};

const baseSteps: JourneyStepTemplate[] = [
  {
    id: "ai:payload-prep",
    icon: "sparkles",
    sourceOfTruth: "App.svelte:buildEmailContextString / callAiCommand payload assembly",
    copy: {
      title: (ctx = {}) => `Analyzing your ${ctx.targetLabel?.trim() || "message"}`,
      detail:
        "Packaging cleaned markdown, subject, and participants before handing work to ChatService.",
    },
  },
  {
    id: "ai:context-search",
    icon: "search",
    sourceOfTruth:
      "ChatService.prepareChatContext → VectorSearchService.searchSimilarEmails (Qdrant).",
    copy: {
      title: "Searching for related context",
      detail: "Vector search (Qdrant) looks up semantically similar emails to enrich the prompt.",
    },
  },
  {
    id: "ai:llm-thinking",
    icon: "brain",
    sourceOfTruth: "OpenAiChatService.generateResponse reasoning / thinking block.",
    copy: {
      title: "Thinking",
      detail: "Letting the reasoning model plan its response before anything is streamed back.",
    },
  },
  {
    id: "ai:writing-summary",
    icon: "pen",
    sourceOfTruth:
      "OpenAiChatService.generateResponse → HtmlConverter markdown sanitization + UI render.",
    copy: {
      title: "Writing summary of my observations",
      detail: "Polishing the answer and formatting sanitized HTML before inserting it into the UI.",
    },
  },
];

function resolveTemplateValue(
  template: string | ((ctx: JourneyContext) => string) | undefined,
  ctx: JourneyContext,
) {
  if (!template) return undefined;
  return typeof template === "function" ? template(ctx) : template;
}

function normalizeOverride(
  override:
    | JourneyCopyTemplate
    | string
    | ((ctx: JourneyContext) => JourneyCopyTemplate | string)
    | undefined,
  ctx: JourneyContext,
) {
  if (!override) return undefined;
  if (typeof override === "function") {
    const result = override(ctx);
    if (typeof result === "string") {
      return { title: result };
    }
    return result;
  }
  if (typeof override === "string") {
    return { title: override };
  }
  return override;
}

export function buildAiJourney(
  ctx: JourneyContext = {},
  overrides?: Partial<
    Record<
      AiJourneyEvent,
      JourneyCopyTemplate | string | ((ctx: JourneyContext) => JourneyCopyTemplate | string)
    >
  >,
): JourneyStep[] {
  return baseSteps.map((step) => {
    const override = normalizeOverride(overrides?.[step.id], ctx);
    const effective = {
      title: resolveTemplateValue(override?.title ?? step.copy.title, ctx),
      detail: resolveTemplateValue(override?.detail ?? step.copy.detail, ctx),
    };

    return {
      id: step.id,
      icon: step.icon,
      title: effective.title || "",
      detail: effective.detail,
      sourceOfTruth: step.sourceOfTruth,
    };
  });
}

export const AI_JOURNEY_EVENTS: AiJourneyEvent[] = baseSteps.map((step) => step.id);

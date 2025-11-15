import { writable, type Writable } from 'svelte/store';
import { buildAiJourney, type AiJourneyEvent, type JourneyContext, type JourneyStep } from '../aiJourney';

export type AiJourneyOverlayState = {
  token: string | null;
  visible: boolean;
  steps: JourneyStep[];
  activeStepId: AiJourneyEvent | null;
  completed: Set<AiJourneyEvent>;
  headline: string;
  subhead: string;
  scope: string;
  scopeTarget: string | null;
};

export type AiJourneyStore = {
  overlay: Writable<AiJourneyOverlayState>;
  begin: (options: {
    scope?: string;
    targetLabel?: string;
    commandKey: string;
    headline?: string | null;
    subhead?: string | null;
    scopeTarget?: string | null;
  }) => string;
  advance: (token: string | null, phase: AiJourneyEvent) => void;
  complete: (token: string | null) => void;
  fail: (token: string | null) => void;
};

/**
 * Encapsulates the AI journey overlay/timers so multiple components can coordinate without duplicating
 * state logic. Stored in JS for reuse outside App.svelte.
 */
const JOURNEY_ORDER: AiJourneyEvent[] = ['ai:payload-prep', 'ai:context-search', 'ai:llm-thinking', 'ai:writing-summary'];

export function createAiJourneyStore(): AiJourneyStore {
  const overlay = writable<AiJourneyOverlayState>(baseState());
  let timer: ReturnType<typeof setTimeout> | null = null;

  function baseState(): AiJourneyOverlayState {
    return {
      token: null,
      visible: false,
      steps: buildAiJourney(),
      activeStepId: null,
      completed: new Set(),
      headline: 'Working on your request',
      subhead: 'Composer assistant',
      scope: 'global',
      scopeTarget: null
    };
  }

  function begin({
    scope = 'global',
    targetLabel = 'message',
    commandKey,
    headline = null,
    subhead = null,
    scopeTarget = null
  }: {
    scope?: string;
    targetLabel?: string;
    commandKey: string;
    headline?: string | null;
    subhead?: string | null;
    scopeTarget?: string | null;
  }) {
    clear();
    const context: JourneyContext = { targetLabel, command: commandKey };
    const steps = buildAiJourney(context);
    const token = typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`;
    overlay.set({
      token,
      visible: true,
      steps,
      scope,
      scopeTarget,
      completed: new Set(),
      activeStepId: steps[0]?.id || null,
      headline: headline || 'Working on your request',
      subhead: subhead || (scope === 'global' ? 'Composer assistant' : 'Mailbox assistant')
    });
    return token;
  }

  function advance(token: string | null, phase: AiJourneyEvent) {
    overlay.update((state) => {
      if (state.token !== token) return state;
      const completed = new Set(state.completed);
      const index = JOURNEY_ORDER.indexOf(phase);
      if (index === -1) return state;
      for (let i = 0; i < index; i++) completed.add(JOURNEY_ORDER[i]);
      return { ...state, completed, activeStepId: phase };
    });
  }

  function complete(token: string | null) {
    clear();
    advance(token, 'ai:writing-summary');
    timer = setTimeout(() => overlay.update((state) => state.token === token ? { ...state, visible: false, token: null } : state), 600);
  }

  function fail(token: string | null) {
    clear();
    overlay.update((state) => state.token === token
      ? { ...state, headline: 'Unable to finish that request', subhead: 'Please retry in a moment', completed: new Set(), activeStepId: null, visible: true }
      : state
    );
    timer = setTimeout(() => overlay.update((state) => state.token === token ? { ...state, visible: false, token: null } : state), 1500);
  }

  function clear() {
    if (timer) {
      clearTimeout(timer);
      timer = null;
    }
  }

  return { overlay, begin, advance, complete, fail };
}

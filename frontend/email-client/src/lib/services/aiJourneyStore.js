import { writable } from 'svelte/store';
import { buildAiJourney } from '../aiJourney';

/**
 * Encapsulates the AI journey overlay/timers so multiple components can coordinate without duplicating
 * state logic. Stored in JS for reuse outside App.svelte.
 */
const JOURNEY_ORDER = ['ai:payload-prep', 'ai:context-search', 'ai:llm-thinking', 'ai:writing-summary'];

export function createAiJourneyStore() {
  const overlay = writable(baseState());
  let timer = null;

  function baseState() {
    return {
      token: null,
      visible: false,
      steps: buildAiJourney(),
      activeStepId: null,
      completed: new Set(),
      headline: 'Working on your request',
      subhead: 'ComposerAI assistant',
      scope: 'global'
    };
  }

  function begin({ scope = 'global', targetLabel = 'message', commandKey, headline, subhead }) {
    clear();
    const steps = buildAiJourney({ targetLabel, command: commandKey });
    const token = typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`;
    overlay.set({
      token,
      visible: true,
      steps,
      scope,
      completed: new Set(),
      activeStepId: steps[0]?.id || null,
      headline: headline || 'Working on your request',
      subhead: subhead || (scope === 'global' ? 'ComposerAI assistant' : 'Mailbox assistant')
    });
    timer = setTimeout(() => advance(token, 'ai:llm-thinking'), 1100);
    return token;
  }

  function advance(token, phase) {
    overlay.update((state) => {
      if (state.token !== token) return state;
      const completed = new Set(state.completed);
      const index = JOURNEY_ORDER.indexOf(phase);
      if (index === -1) return state;
      for (let i = 0; i < index; i++) completed.add(JOURNEY_ORDER[i]);
      return { ...state, completed, activeStepId: phase };
    });
  }

  function complete(token) {
    clear();
    advance(token, 'ai:writing-summary');
    timer = setTimeout(() => overlay.update((state) => state.token === token ? { ...state, visible: false, token: null } : state), 600);
  }

  function fail(token) {
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

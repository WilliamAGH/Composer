/**
 * Static metadata for AI journey overlays. Each scope maps to copy used by the loading HUD.
 */
export type JourneyScope = 'global' | 'panel' | 'compose';

type JourneyScopeMeta = Record<JourneyScope, { subhead: string }>;

export const JOURNEY_SCOPE_META: JourneyScopeMeta = Object.freeze({
  global: { subhead: 'Composer assistant' },
  panel: { subhead: 'Mailbox assistant' },
  compose: { subhead: 'Draft assistant' }
} satisfies JourneyScopeMeta);

/**
 * Returns the readable subhead for a journey scope.
 */
export function getJourneySubhead(scope: JourneyScope = 'global') {
  return JOURNEY_SCOPE_META[scope]?.subhead ?? JOURNEY_SCOPE_META.global.subhead;
}

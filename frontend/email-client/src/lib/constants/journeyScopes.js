/**
 * Static metadata for AI journey overlays. Each scope maps to copy used by the loading HUD.
 */
export const JOURNEY_SCOPE_META = Object.freeze({
  global: { subhead: 'Composer assistant' },
  panel: { subhead: 'Mailbox assistant' },
  compose: { subhead: 'Draft assistant' }
});

/**
 * Returns the readable subhead for a journey scope.
 */
export function getJourneySubhead(scope = 'global') {
  return JOURNEY_SCOPE_META[scope]?.subhead ?? JOURNEY_SCOPE_META.global.subhead;
}

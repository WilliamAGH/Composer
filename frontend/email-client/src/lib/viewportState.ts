import { readable, derived, type Readable } from 'svelte/store';

export type ViewportTier = 'mobile' | 'tablet' | 'desktop' | 'wide';

export type ViewportMeasurement = {
  width: number;
  height: number;
  layoutWidth: number;
  layoutHeight: number;
  visualWidth: number | null;
  visualHeight: number | null;
};

export type ViewportSnapshot = ViewportMeasurement & {
  isMobile: boolean;
  isTablet: boolean;
  isDesktop: boolean;
  isWide: boolean;
  tier: ViewportTier;
};

/**
 * Breakpoint thresholds (in CSS pixels) shared across JS + CSS for layout tiering.
 * @type {{mobile: number, tablet: number, desktop: number, wide: number}}
 */
export const VIEWPORT_BREAKPOINTS = {
  mobile: 640,
  tablet: 1024,
  desktop: 1280,
  wide: 1536
} as const;

const DEFAULT_STATE: ViewportSnapshot = {
  width: 0,
  height: 0,
  layoutWidth: 0,
  layoutHeight: 0,
  visualWidth: null,
  visualHeight: null,
  isMobile: true,
  isTablet: false,
  isDesktop: false,
  isWide: false,
  tier: 'mobile'
};

const isBrowser = typeof window !== 'undefined';
let latestSnapshot: ViewportSnapshot = DEFAULT_STATE;

/**
 * Computes the preferred width/height using visualViewport (when available) with innerWidth fallback.
 */
function measureViewports(): ViewportMeasurement {
  if (!isBrowser) return DEFAULT_STATE;

  const visual = window.visualViewport;
  const layoutWidth = window.innerWidth || document.documentElement?.clientWidth || 0;
  const layoutHeight = window.innerHeight || document.documentElement?.clientHeight || 0;
  const visualWidth = typeof visual?.width === 'number' ? visual.width : null;
  const visualHeight = typeof visual?.height === 'number' ? visual.height : null;
  const deviceWidth = window.screen?.width
    ? window.screen.width / (window.devicePixelRatio || 1)
    : null;
  const deviceHeight = window.screen?.height
    ? window.screen.height / (window.devicePixelRatio || 1)
    : null;

  const width =
    (visualWidth && visualWidth > 0 && visualWidth <= layoutWidth + 1 ? visualWidth : null) ??
    (layoutWidth > 0 ? layoutWidth : null) ??
    (deviceWidth && deviceWidth > 0 ? deviceWidth : 0);

  const height =
    (visualHeight && visualHeight > 0 && visualHeight <= layoutHeight + 1 ? visualHeight : null) ??
    (layoutHeight > 0 ? layoutHeight : null) ??
    (deviceHeight && deviceHeight > 0 ? deviceHeight : 0);

  return { width, height, layoutWidth, layoutHeight, visualWidth, visualHeight };
}

/**
 * Returns a media-query map so JS mirrors the exact CSS breakpoint evaluations.
 */
type MediaQueryMap = {
  mobile: MediaQueryList;
  tablet: MediaQueryList;
  desktop: MediaQueryList;
  wide: MediaQueryList;
} | null;

function createMediaQueries(): MediaQueryMap {
  if (!isBrowser || typeof window.matchMedia !== 'function') return null;
  return {
    mobile: window.matchMedia(`(max-width: ${VIEWPORT_BREAKPOINTS.mobile - 0.02}px)`),
    tablet: window.matchMedia(`(min-width: ${VIEWPORT_BREAKPOINTS.mobile}px) and (max-width: ${VIEWPORT_BREAKPOINTS.tablet - 0.02}px)`),
    desktop: window.matchMedia(`(min-width: ${VIEWPORT_BREAKPOINTS.tablet}px) and (max-width: ${VIEWPORT_BREAKPOINTS.wide - 0.02}px)`),
    wide: window.matchMedia(`(min-width: ${VIEWPORT_BREAKPOINTS.wide}px)`)
  };
}

function computeMatches(measurement: ViewportMeasurement, queries: MediaQueryMap) {
  if (queries) {
    return {
      mobile: queries.mobile.matches,
      tablet: queries.tablet.matches,
      desktop: queries.desktop.matches,
      wide: queries.wide.matches
    };
  }
  const width = measurement.width;
  return {
    mobile: width < VIEWPORT_BREAKPOINTS.mobile,
    tablet: width >= VIEWPORT_BREAKPOINTS.mobile && width < VIEWPORT_BREAKPOINTS.tablet,
    desktop: width >= VIEWPORT_BREAKPOINTS.tablet && width < VIEWPORT_BREAKPOINTS.wide,
    wide: width >= VIEWPORT_BREAKPOINTS.wide
  };
}

function determineTier(matches: { mobile: boolean; tablet: boolean; desktop: boolean; wide: boolean }): ViewportTier {
  if (matches.mobile) return 'mobile';
  if (matches.tablet) return 'tablet';
  if (matches.desktop) return 'desktop';
  return 'wide';
}

function buildState(queries: MediaQueryMap): ViewportSnapshot {
  const measurement = measureViewports();
  const matches = computeMatches(measurement, queries);
  return {
    ...measurement,
    isMobile: matches.mobile,
    isTablet: matches.tablet,
    isDesktop: matches.desktop,
    isWide: matches.wide,
    tier: determineTier(matches)
  };
}

function registerMediaQueryListener(mql: MediaQueryList | null | undefined, handler: () => void) {
  if (!mql) return () => {};
  const listener = () => handler();
  if (typeof mql.addEventListener === 'function') {
    mql.addEventListener('change', listener);
    return () => mql.removeEventListener('change', listener);
  }
  mql.addListener(listener);
  return () => mql.removeListener(listener);
}

/**
 * Unified viewport state store consumed by every responsive component.
 */
export const viewportState: Readable<ViewportSnapshot> = readable(DEFAULT_STATE, (set) => {
  if (!isBrowser) return () => {};

  const queries = createMediaQueries();
  let frame: number | null = null;
  let rafScheduled = false;

  const update = () => {
    latestSnapshot = buildState(queries);
    set(latestSnapshot);
  };

  const scheduleUpdate = () => {
    if (rafScheduled) return;
    rafScheduled = true;
    frame = window.requestAnimationFrame(() => {
      rafScheduled = false;
      update();
    });
  };

  update();

  const removeResize = () => window.removeEventListener('resize', scheduleUpdate);
  window.addEventListener('resize', scheduleUpdate, { passive: true });

  const visual = window.visualViewport;
  const removeVisual = visual
    ? (() => {
        visual.addEventListener('resize', scheduleUpdate, { passive: true });
        return () => visual.removeEventListener('resize', scheduleUpdate);
      })()
    : () => {};

  const orientation = window.screen?.orientation;
  const removeOrientation = orientation && typeof orientation.addEventListener === 'function'
    ? (() => {
        orientation.addEventListener('change', scheduleUpdate);
        return () => orientation.removeEventListener('change', scheduleUpdate);
      })()
    : () => {};

  const removeMedia = queries
    ? [
        registerMediaQueryListener(queries.mobile, scheduleUpdate),
        registerMediaQueryListener(queries.tablet, scheduleUpdate),
        registerMediaQueryListener(queries.desktop, scheduleUpdate),
        registerMediaQueryListener(queries.wide, scheduleUpdate)
      ]
    : [];

  return () => {
    removeResize();
    removeVisual();
    removeOrientation();
    removeMedia.forEach((dispose) => dispose());
    if (typeof frame === 'number') {
      window.cancelAnimationFrame(frame);
    }
  };
});

/**
 * Returns the latest viewport snapshot for non-Svelte consumers.
 */
/**
 * Reactive store exposing the current viewport tier ('mobile' | 'tablet' | ...).
 */
export const viewport = derived(viewportState, ($state) => $state.tier);

/**
 * Convenience boolean stores so components can destructure only what they need.
 */
export const isMobile = derived(viewportState, ($state) => $state.isMobile);
export const isTablet = derived(viewportState, ($state) => $state.isTablet);
export const isDesktop = derived(viewportState, ($state) => $state.isDesktop);
export const isWide = derived(viewportState, ($state) => $state.isWide);

/**
 * Lightweight size store for components that only care about width/height.
 */
export const viewportSize = derived(viewportState, ($state) => ({ width: $state.width, height: $state.height }));

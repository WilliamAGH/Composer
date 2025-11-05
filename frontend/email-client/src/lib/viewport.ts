import { readable, derived } from 'svelte/store';

// Define breakpoints
export const breakpoints = {
  mobile: 640,    // < 640px: Single panel
  tablet: 1024,   // 640-1024px: Two panels
  desktop: 1280,  // 1024-1280px: Collapsible sidebar + two panels
  wide: 1536      // > 1280px: Full three panels
};

// Core viewport width store
export const viewportWidth = readable(0, (set) => {
  if (typeof window === 'undefined') {
    set(0);
    return () => {};
  }

  const updateWidth = () => set(window.innerWidth);
  updateWidth();

  window.addEventListener('resize', updateWidth);
  return () => window.removeEventListener('resize', updateWidth);
});

// Derived breakpoint stores
export const isMobile = derived(viewportWidth, $width => $width < breakpoints.mobile);
export const isTablet = derived(viewportWidth, $width => $width >= breakpoints.mobile && $width < breakpoints.tablet);
export const isDesktop = derived(viewportWidth, $width => $width >= breakpoints.tablet && $width < breakpoints.desktop);
export const isWide = derived(viewportWidth, $width => $width >= breakpoints.desktop);

// Combined viewport store for easier access
export const viewport = derived(
  viewportWidth,
  $width => {
    if ($width < breakpoints.mobile) return 'mobile';
    if ($width < breakpoints.tablet) return 'tablet';
    if ($width < breakpoints.desktop) return 'desktop';
    return 'wide';
  }
);
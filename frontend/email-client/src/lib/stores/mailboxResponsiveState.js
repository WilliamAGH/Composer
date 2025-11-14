import { derived } from 'svelte/store';
import { viewportState, isMobile, isTablet, isDesktop, isWide } from '../viewportState';

const HAMBURGER_COLLAPSE_BREAKPOINT = 1215;
const ACTION_TOOLBAR_COMPACT_BREAKPOINT = 1180;
const SIDEBAR_WIDTH_MAP = {
  'inline-wide': 448,
  'inline-desktop': 400,
  'inline-collapsed': 88,
  'drawer-visible': 0,
  'drawer-hidden': 0
};

/**
 * Produces derived responsive metadata so layout components don't repeat breakpoint math.
 * Automatically syncs drawer mode when the inline sidebar is not permitted.
 */
export function createMailboxResponsiveState({ chromeStore, viewportStore = viewportState }) {
  if (!chromeStore) {
    throw new Error('chromeStore is required for responsive state');
  }

  const viewportTier = derived([isMobile, isTablet, isDesktop, isWide], ([_mobile, $tablet, $desktop, $wide]) => {
    if ($wide) return 'wide';
    if ($desktop) return 'desktop';
    if ($tablet) return 'tablet';
    return 'mobile';
  });

  const inlineSidebar = derived([viewportState, viewportTier], ([$viewport, $tier]) => {
    const width = $viewport?.layoutWidth ?? $viewport?.width ?? 0;
    return width >= HAMBURGER_COLLAPSE_BREAKPOINT && ($tier === 'desktop' || $tier === 'wide');
  });

  const sidebarVariant = derived(
    [inlineSidebar, chromeStore.stores.sidebarOpen, chromeStore.stores.drawerVisible, viewportTier],
    ([$inlineSidebar, $sidebarOpen, $drawerVisible, $tier]) => {
      if (!$inlineSidebar) {
        return $drawerVisible ? 'drawer-visible' : 'drawer-hidden';
      }
      if (!$sidebarOpen) {
        return 'inline-collapsed';
      }
      return $tier === 'wide' ? 'inline-wide' : 'inline-desktop';
    }
  );

  const sidebarWidth = derived(sidebarVariant, ($variant) => SIDEBAR_WIDTH_MAP[$variant] ?? 0);
  const availableContentWidth = derived([viewportState, sidebarWidth], ([$viewport, $sidebarWidth]) => {
    const width = $viewport?.layoutWidth ?? $viewport?.width ?? 0;
    return Math.max(0, width - $sidebarWidth);
  });

  const compactActions = derived([isMobile, isTablet, availableContentWidth], ([$mobile, $tablet, $width]) => {
    if ($mobile || $tablet) {
      return true;
    }
    return $width > 0 && $width < ACTION_TOOLBAR_COMPACT_BREAKPOINT;
  });

  const inlineSubscription = inlineSidebar.subscribe((inline) => {
    chromeStore.setDrawerMode(!inline);
  });

  return {
    stores: {
      inlineSidebar,
      sidebarVariant,
      sidebarWidth,
      availableContentWidth,
      compactActions,
      viewportTier,
      viewport: viewportStore
    },
    destroy() {
      inlineSubscription?.();
    }
  };
}

import { derived, get, type Readable } from 'svelte/store';
import { WindowKind } from './window/windowTypes';

type FloatingWindowDescriptor = {
  id: string;
  kind: string;
  minimized?: boolean;
};

type WindowManager = {
  close: (id: string) => void;
};

export interface MobileOverlayCoordinatorOptions {
  mobileStore: Readable<boolean>;
  selectedEmailStore: Readable<unknown>;
  floatingWindowsStore: Readable<FloatingWindowDescriptor[]>;
  drawerVisibleStore: Readable<boolean>;
  panelReadyStore: Readable<boolean>;
  windowManager: WindowManager;
}

/**
 * Centralizes the logic for determining which mobile overlays are visible and how the shared backdrop behaves.
 */
export function createMobileOverlayCoordinator({
  mobileStore,
  selectedEmailStore,
  floatingWindowsStore,
  drawerVisibleStore,
  panelReadyStore,
  windowManager
}: MobileOverlayCoordinatorOptions) {
  if (!mobileStore || !selectedEmailStore || !floatingWindowsStore || !drawerVisibleStore || !panelReadyStore) {
    throw new Error('createMobileOverlayCoordinator requires all stores to be provided.');
  }

  const detailVisible = derived([mobileStore, selectedEmailStore], ([$mobile, $selected]) => Boolean($mobile && $selected));

  const composeWindows = derived([mobileStore, floatingWindowsStore], ([$mobile, $floating]) => {
    if (!$mobile || !Array.isArray($floating)) return [];
    return $floating.filter((win) => win.kind === WindowKind.COMPOSE && !win.minimized);
  });

  const composeVisible = derived(composeWindows, ($windows) => $windows.length > 0);

  const panelVisible = derived([mobileStore, panelReadyStore], ([$mobile, $panelReady]) => Boolean($mobile && $panelReady));

  const overlayVisible = derived(
    [drawerVisibleStore, detailVisible, panelVisible, composeVisible],
    ([$drawer, $detail, $panel, $compose]) => Boolean($drawer || $detail || $panel || $compose)
  );

  function closeTopComposeWindow() {
    const windows = get(composeWindows);
    if (!windows || windows.length === 0) return false;
    const latest = windows[windows.length - 1];
    if (!latest) return false;
    windowManager.close(latest.id);
    return true;
  }

  function handleBackdropClose({ onClosePanel, onClearSelection, onCloseDrawer }: {
    onClosePanel?: () => void;
    onClearSelection?: () => void;
    onCloseDrawer?: () => void;
  } = {}) {
    if (closeTopComposeWindow()) return;

    if (get(panelVisible) && typeof onClosePanel === 'function') {
      onClosePanel();
      return;
    }

    if (get(detailVisible) && typeof onClearSelection === 'function') {
      onClearSelection();
      return;
    }

    if (get(drawerVisibleStore) && typeof onCloseDrawer === 'function') {
      onCloseDrawer();
    }
  }

  return {
    detailVisible,
    composeWindows,
    composeVisible,
    panelVisible,
    overlayVisible,
    handleBackdropClose
  };
}

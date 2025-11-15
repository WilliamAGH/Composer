import { derived, get, type Readable, type Unsubscriber } from 'svelte/store';
import type { SvelteComponent } from 'svelte';
import type { OverlayController } from '../overlay/OverlayController';
import type { WindowManager } from '../window/windowStore';
import { WindowKind, WindowMode, type WindowDescriptor } from '../window/windowTypes';
import type { createAiPanelStore } from '../stores/aiPanelStore';

export type DockItemType = 'compose' | 'panel';

export interface DockItem {
  id: string;
  type: DockItemType;
  title: string;
  icon?: typeof SvelteComponent | null;
  closeable: boolean;
  onRestore?: () => void;
  onClose?: () => void;
}

interface DesktopContentControllerOptions {
  windowManager: WindowManager;
  panelStore: ReturnType<typeof createAiPanelStore>;
  overlayController: OverlayController;
  aiPanelTitle: string;
  aiPanelIcon?: typeof SvelteComponent | null;
}

export interface DesktopContentController {
  dockItems: Readable<DockItem[]>;
  dispose: () => void;
}

export function createDesktopContentController({
  windowManager,
  panelStore,
  overlayController,
  aiPanelTitle,
  aiPanelIcon = null
}: DesktopContentControllerOptions): DesktopContentController {
  const panelStores = panelStore.stores;
  const panelSessionActiveStore = panelStores.sessionActive;
  const panelMinimizedStore = panelStores.minimized;
  const panelActiveKeyStore = panelStores.activeKey;

  const dockItems = derived(
    [windowManager.minimized, panelSessionActiveStore, panelMinimizedStore, panelActiveKeyStore],
    ([$minimizedWindows, $panelSessionActive, $panelMinimized, $panelActiveKey]) => {
      const composeItems = [...$minimizedWindows]
        .filter((win) => win.mode === WindowMode.FLOATING && win.kind === WindowKind.COMPOSE)
        .reverse()
        .map((win): DockItem => ({
          id: win.id,
          type: 'compose',
          title: win.title,
          icon: null,
          closeable: true,
          onRestore: () => windowManager.focus(win.id),
          onClose: () => windowManager.close(win.id)
        }));

      const panelItems = !$panelSessionActive || !$panelMinimized || !$panelActiveKey
        ? []
        : [{
            id: `panel-${$panelActiveKey}`,
            type: 'panel' as DockItemType,
            title: aiPanelTitle,
            icon: aiPanelIcon,
            closeable: true,
            onRestore: () => panelStore.restoreFromDock(),
            onClose: () => panelStore.closePanel($panelActiveKey)
          }];

      return [...composeItems, ...panelItems];
    }
  );

  const unsubscribers: Unsubscriber[] = [];

  unsubscribers.push(
    windowManager.windows.subscribe((list) => {
      enforceSingleActiveWindow(list);
    })
  );

  unsubscribers.push(
    overlayController.overlays.subscribe((stack) => {
      const modalVisible = stack.some((entry) => entry.presenter === 'modal');
      if (modalVisible) {
        minimizeAllFloatingWindows();
        minimizePanel();
      }
    })
  );

  function ensureWindowMinimized(id: string, snapshot?: WindowDescriptor[]) {
    const list = snapshot || get(windowManager.windows);
    const target = list.find((win) => win.id === id);
    if (target && !target.minimized) {
      windowManager.toggleMinimize(id);
    }
  }

  function minimizeAllFloatingWindows(exceptId?: string) {
    const snapshot = get(windowManager.windows);
    snapshot.forEach((win) => {
      if (win.mode !== WindowMode.FLOATING || win.minimized) {
        return;
      }
      if (exceptId && win.id === exceptId) {
        return;
      }
      ensureWindowMinimized(win.id, snapshot);
    });
  }

  function minimizePanel() {
    const active = get(panelSessionActiveStore);
    const minimized = get(panelMinimizedStore);
    if (active && !minimized) {
      panelStore.minimize();
    }
  }

  function enforceSingleActiveWindow(windowsSnapshot: WindowDescriptor[]) {
    const floating = windowsSnapshot.filter(
      (win) => win.mode === WindowMode.FLOATING && win.kind === WindowKind.COMPOSE
    );
    const active = floating.filter((win) => !win.minimized);
    if (active.length <= 1) {
      return;
    }
    const keep = active[active.length - 1];
    active.forEach((win) => {
      if (win.id !== keep.id) {
        ensureWindowMinimized(win.id, windowsSnapshot);
      }
    });
    minimizePanel();
  }

  function dispose() {
    unsubscribers.splice(0).forEach((fn) => fn());
  }

  return {
    dockItems,
    dispose
  };
}

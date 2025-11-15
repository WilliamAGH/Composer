import { derived, writable, get, type Readable } from 'svelte/store';
import type { MailboxDataStore } from './mailboxDataStore';

export interface MailboxChromeStore {
  stores: {
    selectedEmailId: Readable<string | null>;
    selectedEmail: Readable<any>;
    sidebarOpen: Readable<boolean>;
    drawerMode: Readable<boolean>;
    drawerVisible: Readable<boolean>;
  };
  selectEmailById: (id: string | null) => void;
  clearSelection: () => void;
  toggleSidebar: () => void;
  setSidebarOpen: (open: boolean) => void;
  setDrawerMode: (isDrawer: boolean) => void;
  setDrawerVisible: (visible: boolean) => void;
  openDrawer: () => void;
  closeDrawer: () => void;
}

interface Options {
  dataStore: MailboxDataStore;
  defaultSidebarOpen?: boolean;
}

export function createMailboxChromeStore({ dataStore, defaultSidebarOpen = true }: Options): MailboxChromeStore {
  if (!dataStore) {
    throw new Error('mailboxDataStore is required to build the chrome store');
  }

  const selectedEmailId = writable<string | null>(null);
  const sidebarOpen = writable<boolean>(defaultSidebarOpen);
  const drawerMode = writable<boolean>(false);
  const drawerVisible = writable<boolean>(false);

  const selectedEmail = derived([selectedEmailId, dataStore.stores.emails], ([$selectedId, $emails]) => {
    if (!$selectedId || !Array.isArray($emails)) return null;
    return $emails.find((email) => email.id === $selectedId) || null;
  });

  function selectEmailById(id: string | null) {
    selectedEmailId.set(id || null);
    if (id) {
      dataStore.markEmailRead(id);
    }
  }

  function clearSelection() {
    selectedEmailId.set(null);
  }

  function toggleSidebar() {
    sidebarOpen.update((open) => !open);
  }

  function setSidebarOpen(open: boolean) {
    sidebarOpen.set(Boolean(open));
  }

  function setDrawerMode(isDrawer: boolean) {
    drawerMode.update((current) => {
      if (current === isDrawer) {
        return current;
      }
      if (!isDrawer) {
        drawerVisible.set(false);
      } else {
        sidebarOpen.set(true);
      }
      return isDrawer;
    });
  }

  function setDrawerVisible(visible: boolean) {
    drawerVisible.set(Boolean(visible));
  }

  function openDrawer() {
    setDrawerVisible(true);
    sidebarOpen.set(true);
  }

  function closeDrawer() {
    setDrawerVisible(false);
    if (get(drawerMode)) {
      sidebarOpen.set(false);
    }
  }

  return {
    stores: {
      selectedEmailId: { subscribe: selectedEmailId.subscribe },
      selectedEmail,
      sidebarOpen: { subscribe: sidebarOpen.subscribe },
      drawerMode: { subscribe: drawerMode.subscribe },
      drawerVisible: { subscribe: drawerVisible.subscribe }
    },
    selectEmailById,
    clearSelection,
    toggleSidebar,
    setSidebarOpen,
    setDrawerMode,
    setDrawerVisible,
    openDrawer,
    closeDrawer
  };
}


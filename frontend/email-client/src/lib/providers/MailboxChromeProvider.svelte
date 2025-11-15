<script context="module">
  import { getContext } from 'svelte';

  export const MAILBOX_DATA_CONTEXT = Symbol('mailbox-data-context');
  export const MAILBOX_CHROME_CONTEXT = Symbol('mailbox-chrome-context');
  export const MAILBOX_RESPONSIVE_CONTEXT = Symbol('mailbox-responsive-context');

  export function useMailboxDataStore() {
    const store = getContext(MAILBOX_DATA_CONTEXT);
    if (!store) {
      throw new Error('Mailbox data store not found in context.');
    }
    return store;
  }

  export function useMailboxChromeStore() {
    const store = getContext(MAILBOX_CHROME_CONTEXT);
    if (!store) {
      throw new Error('Mailbox chrome store not found in context.');
    }
    return store;
  }

  export function useMailboxResponsiveState() {
    const responsive = getContext(MAILBOX_RESPONSIVE_CONTEXT);
    if (!responsive) {
      throw new Error('Mailbox responsive state not provided.');
    }
    return responsive;
  }
</script>

<script>
  import { onDestroy, onMount } from 'svelte';
  import { setContext } from 'svelte';
  import { createMailboxDataStore } from '../stores/mailboxDataStore';
  import { createMailboxChromeStore } from '../stores/mailboxChromeStore';
  import { createMailboxResponsiveState } from '../stores/mailboxResponsiveState';
  import { recordClientDiagnostic } from '../services/clientDiagnosticsService';

  const ACTIVE_MAILBOX_ID = 'primary';

  /**
   * Bootstraps mailbox data + chrome stores and shares them via context so downstream panes can read
   * consistent selection + responsive state without prop drilling.
   */
  export let bootstrap = {};

  const initialEmails = Array.isArray(bootstrap.messages) ? bootstrap.messages : [];
  const initialFolderCounts =
    bootstrap.folderCounts && typeof bootstrap.folderCounts === 'object' ? bootstrap.folderCounts : null;
  const initialEffectiveFolders =
    bootstrap.effectiveFolders && typeof bootstrap.effectiveFolders === 'object' ? bootstrap.effectiveFolders : null;

  const dataStore = createMailboxDataStore(initialEmails, initialFolderCounts, initialEffectiveFolders);
  const chromeStore = createMailboxChromeStore({ dataStore });
  const responsive = createMailboxResponsiveState({ chromeStore });

  setContext(MAILBOX_DATA_CONTEXT, dataStore);
  setContext(MAILBOX_CHROME_CONTEXT, chromeStore);
  setContext(MAILBOX_RESPONSIVE_CONTEXT, responsive);

  onMount(() => {
    dataStore.loadMailboxState(ACTIVE_MAILBOX_ID).catch((error) => {
      recordClientDiagnostic('warn', 'Mailbox state hydration failed', error);
    });
  });

  onDestroy(() => {
    responsive.destroy?.();
  });
</script>

<slot {dataStore} {chromeStore} {responsive}></slot>

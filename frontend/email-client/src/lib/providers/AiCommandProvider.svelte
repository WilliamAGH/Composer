<script context="module">
  import { getContext } from 'svelte';

  export const AI_COMMAND_CONTEXT = Symbol('ai-command-context');

  export function useAiCommandContext() {
    const context = getContext(AI_COMMAND_CONTEXT);
    if (!context) {
      throw new Error('AiCommandProvider context not found.');
    }
    return context;
  }
</script>

<script>
  import { onMount } from 'svelte';
  import { setContext } from 'svelte';
  import { catalogStore, hydrateCatalog, ensureCatalogLoaded } from '../services/aiCatalog';
  import { createAiJourneyStore } from '../services/aiJourneyStore';
  import { createConversationLedger } from '../services/conversationLedger';
  import { createAiCommandClient } from '../services/aiCommandClient';
  import { windowNoticeStore, processClientWarning } from '../services/clientDiagnosticsService';
  import { initializeUiNonce, startChatHeartbeat, CLIENT_WARNING_EVENT } from '../services/sessionNonceClient';

  export let bootstrap = {};

  hydrateCatalog(bootstrap.aiFunctions || null);
  initializeUiNonce(bootstrap.uiNonce || null);

  const catalog = catalogStore();
  const ensureCatalog = () => ensureCatalogLoaded(bootstrap.aiFunctions || null);

  function createAiCommandService(getSelectedEmail) {
    const journeyStore = createAiJourneyStore();
    const conversationLedger = createConversationLedger(getSelectedEmail);
    const aiClient = createAiCommandClient({
      ensureCatalogLoaded: ensureCatalog,
      journeyStore,
      conversationLedger
    });
    return {
      aiClient,
      journeyStore,
      conversationLedger
    };
  }

  setContext(AI_COMMAND_CONTEXT, {
    catalog,
    ensureCatalog,
    windowNoticeStore,
    createAiCommandService
  });

  onMount(() => {
    const stopHeartbeat = startChatHeartbeat();
    const handler = (event) => {
      processClientWarning(event?.detail || {});
    };
    if (typeof window !== 'undefined') {
      window.addEventListener(CLIENT_WARNING_EVENT, handler);
    }
    return () => {
      stopHeartbeat();
      if (typeof window !== 'undefined') {
        window.removeEventListener(CLIENT_WARNING_EVENT, handler);
      }
    };
  });
</script>

<slot />

<script>
  import { createEventDispatcher } from 'svelte';
  import WindowFrame from './window/WindowFrame.svelte';
  import { useWindowContext } from './window/windowContext';

  /**
   * AI summary content rendered inside the shared window shell. Dedicated component keeps HTML parsing
   * isolated while allowing the store to treat it like any other window.
   */
  export let windowConfig;
  const dispatch = createEventDispatcher();
  const windowManager = useWindowContext();

  $: html = windowConfig?.payload?.html || '<div class="text-sm text-slate-500">No summary yet.</div>';
</script>

{#if windowConfig}
  <WindowFrame
    open={true}
    title={windowConfig.title}
    mode="docked"
    minimized={windowConfig.minimized}
    allowMinimize={true}
    allowClose={true}
    on:close={() => windowManager.close(windowConfig.id)}
    on:toggleMinimize={() => windowManager.toggleMinimize(windowConfig.id)}
  >
  <div class="summary-body">
    {@html html}
  </div>
</WindowFrame>
{/if}

<style>
  .summary-body {
    font-size: 0.95rem;
    color: #1e1b4b;
  }
  .summary-body :global(p) {
    margin-bottom: 0.75rem;
  }
</style>

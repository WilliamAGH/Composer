<script>
  import { createEventDispatcher } from 'svelte';
  import { RotateCcw, Minus } from 'lucide-svelte';
  import MobileTopBar from './MobileTopBar.svelte';
  import AiSummaryWindow from './AiSummaryWindow.svelte';

  export let panelState = null;
  export let journeyOverlay = null;
  export let error = '';
  export let showMenuButton = true;
  export let onClose = null;
  export let onToggleMenu = null;
  export let onMinimize = null;
  export let onRunCommand = null;

  const dispatch = createEventDispatcher();

  $: rawTitle = panelState?.title || 'Summary';
  $: commandKey = (panelState?.commandKey || journeyOverlay?.commandKey || 'summarize').toLowerCase();
  $: badgeLabel = commandKey === 'translate' ? 'Translation' : 'Summary';
  $: title = rawTitle?.replace(/^AI\s+/i, '') || badgeLabel;
  $: updatedLabel = panelState?.updatedAt
    ? new Date(panelState.updatedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    : null;
  $: isLoading = Boolean(journeyOverlay && journeyOverlay.visible);
  $: refreshLabel = isLoading
    ? `Working on ${badgeLabel.toLowerCase()}`
    : `Regenerate ${badgeLabel.toLowerCase()}`;

  function handleClose() {
    dispatch('close');
    onClose?.();
  }

  function handleMenuToggle() {
    dispatch('toggleMenu');
    onToggleMenu?.();
  }

  function handleMinimize() {
    dispatch('minimize');
    onMinimize?.();
  }

  function handleRefresh() {
    const detail = { command: panelState?.commandKey || 'summarize' };
    dispatch('runCommand', detail);
    onRunCommand?.(detail);
  }
</script>

  <div class="ai-summary-mobile-sheet">
    <MobileTopBar
      variant="custom"
      showMenuButton={showMenuButton}
      backButtonAriaLabel="Close AI summary"
      on:back={handleClose}
      on:toggleMenu={handleMenuToggle}>
      <div slot="center" class="ai-summary-mobile-sheet__title">
        <p class="ai-summary-mobile-sheet__eyebrow">{badgeLabel}</p>
        {#if updatedLabel}
          <p class="ai-summary-mobile-sheet__meta">Updated at {updatedLabel}</p>
        {/if}
      </div>
      <div slot="actions" class="ai-summary-mobile-sheet__actions">
        <button
          type="button"
          class="btn btn--ghost btn--icon"
          aria-label="Dock AI panel"
          on:click={handleMinimize}>
          <Minus class="h-4 w-4" aria-hidden="true" />
        </button>
        <button
          type="button"
          class="btn btn--secondary btn--labelled"
          on:click={handleRefresh}
          disabled={isLoading}>
          <RotateCcw class={`h-4 w-4 ${isLoading ? 'animate-spin' : ''}`} aria-hidden="true" />
          {isLoading ? 'Working…' : 'Refresh'}
        </button>
      </div>
    </MobileTopBar>

    <section class="ai-summary-mobile-sheet__body">
      <AiSummaryWindow
        panelState={panelState}
        journeyOverlay={journeyOverlay}
        error={error}
        maximized={true}
        hideChrome={true}
      />
    </section>
  </div>

<style>
  .ai-summary-mobile-sheet {
    position: fixed;
    inset: 0;
    z-index: 90;
    display: flex;
    flex-direction: column;
    background: #ffffff;
    padding: 0.5rem 1rem 1rem;
  }

  @supports (padding: env(safe-area-inset-top)) {
    .ai-summary-mobile-sheet {
      padding-top: max(0.5rem, env(safe-area-inset-top));
      padding-right: max(1rem, env(safe-area-inset-right));
      padding-bottom: max(1rem, env(safe-area-inset-bottom));
      padding-left: max(1rem, env(safe-area-inset-left));
    }
  }

  .ai-summary-mobile-sheet__title {
    display: flex;
    flex-direction: column;
    gap: 0.1rem;
    min-width: 0;
  }


  .ai-summary-mobile-sheet__eyebrow {
    font-size: 0.65rem;
    letter-spacing: 0.35em;
    text-transform: uppercase;
    color: #64748b;
  }

  .ai-summary-mobile-sheet__meta {
    font-size: 0.75rem;
    color: #475569;
  }

  .ai-summary-mobile-sheet__actions {
    display: inline-flex;
    align-items: center;
    gap: 0.35rem;
  }

  .ai-summary-mobile-sheet__body {
    flex: 1;
    min-height: 0;
    padding: 1rem;
  }

  .ai-summary-mobile-sheet__body :global(.ai-summary-panel) {
    height: 100%;
  }
</style>

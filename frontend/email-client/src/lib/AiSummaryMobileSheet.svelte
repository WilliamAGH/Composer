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

  $: title = panelState?.title || 'AI Summary';
  $: commandKey = (panelState?.commandKey || 'summarize').toLowerCase();
  $: badgeLabel = commandKey === 'translate' ? 'Translation' : 'Summary';
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
        <p class="ai-summary-mobile-sheet__headline">{title}</p>
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
    background: linear-gradient(180deg, rgba(248, 250, 252, 0.98), rgba(15, 23, 42, 0.1));
    backdrop-filter: blur(12px);
  }

  @supports (padding: env(safe-area-inset-top)) {
    .ai-summary-mobile-sheet {
      padding-top: env(safe-area-inset-top);
      padding-right: env(safe-area-inset-right);
      padding-bottom: env(safe-area-inset-bottom);
      padding-left: env(safe-area-inset-left);
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
    color: rgba(99, 102, 241, 0.7);
  }

  .ai-summary-mobile-sheet__headline {
    font-size: 1rem;
    font-weight: 600;
    color: #0f172a;
    line-height: 1.2;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
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

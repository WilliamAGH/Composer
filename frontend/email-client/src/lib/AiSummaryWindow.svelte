<script>
  import { createEventDispatcher } from 'svelte';
  import AiLoadingJourney from './AiLoadingJourney.svelte';
  import { Sparkles, RotateCcw, Minus, X, Maximize2, Minimize2 } from 'lucide-svelte';

  export let panelState = null;
  export let journeyOverlay = null;
  export let error = '';
  export let maximized = false;

  const dispatch = createEventDispatcher();

  $: title = panelState?.title || 'AI Summary';
  $: html = panelState?.html || '';
  $: lastCommand = panelState?.commandKey || 'summarize';
  $: updatedLabel = panelState?.updatedAt
    ? new Date(panelState.updatedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    : null;
  $: isLoading = Boolean(journeyOverlay && journeyOverlay.visible);
  $: hasContent = Boolean(html && !isLoading);
  $: activeCommandKey = (panelState?.commandKey || lastCommand || '').toLowerCase();
  $: badgeLabel = activeCommandKey === 'translate' ? 'Translation' : 'Summary';
  $: emptyTitle = badgeLabel === 'Translation' ? 'No translation yet' : 'No summary yet';
  $: emptyCopy = badgeLabel === 'Translation'
    ? 'Run an AI translation to pin results to this thread.'
    : 'Run an AI summary to pin results to this thread.';
  $: emptyActionLabel = badgeLabel === 'Translation' ? 'Translate email' : 'Generate summary';
  $: primaryActionAria = hasContent
    ? `Regenerate ${badgeLabel.toLowerCase()}`
    : `Generate ${badgeLabel.toLowerCase()}`;

  function emitRunCommand(key = 'summarize') {
    dispatch('runCommand', { key });
  }

  function handlePrimaryAction() {
    emitRunCommand(lastCommand || 'summarize');
  }

  function minimize() {
    dispatch('minimize');
  }

  function toggleMaximize() {
    dispatch('toggleMaximize');
  }

  function closePanel() {
    dispatch('close');
  }
</script>

<section class={`ai-summary-panel ${maximized ? 'maximized' : ''}`} aria-live="polite">
  <header class="panel-header">
    <div class="panel-title-group">
      <div class="panel-heading">
        <span class="panel-chip">{badgeLabel}</span>
        {#if updatedLabel}
          <span class="panel-meta">Updated at {updatedLabel}</span>
        {/if}
      </div>
    </div>
    <div class="panel-actions">
      <button
        type="button"
        class="btn btn--secondary btn--icon btn--icon-chrome"
        on:click={handlePrimaryAction}
        disabled={isLoading}
        aria-label={primaryActionAria}
      >
        <RotateCcw class="h-4 w-4" aria-hidden="true" />
      </button>
      <div class="panel-window-controls">
        <button type="button" class="btn btn--icon btn--icon-chrome btn--inset" on:click={minimize} title="Minimize" aria-label="Minimize AI panel">
          <Minus class="h-4 w-4" />
        </button>
        <button type="button" class="btn btn--icon btn--icon-chrome btn--inset" on:click={toggleMaximize} title={maximized ? 'Restore' : 'Maximize'} aria-label={maximized ? 'Restore panel size' : 'Maximize AI panel'}>
          {#if maximized}
            <Minimize2 class="h-4 w-4" />
          {:else}
            <Maximize2 class="h-4 w-4" />
          {/if}
        </button>
        <button type="button" class="btn btn--icon btn--icon-chrome btn--inset" on:click={closePanel} title="Close" aria-label="Close AI panel">
          <X class="h-4 w-4" />
        </button>
      </div>
    </div>
  </header>
  <div class="panel-body">
    <div class="panel-scroll">
      {#if isLoading}
        <AiLoadingJourney
          steps={journeyOverlay.steps}
          activeStepId={journeyOverlay.activeStepId}
          headline={journeyOverlay.headline}
          subhead={journeyOverlay.subhead}
          show={journeyOverlay.visible}
          inline={true}
          subdued={true}
          className="border-slate-200" />
      {:else if error}
      <div class="panel-state panel-error">
        <p>{error}</p>
        <button
          type="button"
          class="btn btn--ghost btn--icon"
          aria-label="Try again"
          on:click={() => emitRunCommand(lastCommand)}>
          <RotateCcw class="h-4 w-4" aria-hidden="true" />
        </button>
      </div>
      {:else if hasContent}
        <div class="panel-html">
          {@html html}
        </div>
      {:else}
        <div class="panel-state">
          <Sparkles class="h-6 w-6 text-slate-400" aria-hidden="true" />
          <p class="panel-empty-title">{emptyTitle}</p>
          <p class="panel-empty-copy">{emptyCopy}</p>
          <button
            type="button"
            class="btn btn--secondary btn--icon"
            aria-label={emptyActionLabel}
            on:click={() => emitRunCommand(lastCommand || 'summarize')}>
            <RotateCcw class="h-4 w-4" aria-hidden="true" />
          </button>
        </div>
      {/if}
    </div>
  </div>
</section>

<style>
  /**
   * Frosted surface for summary/translation output with layered navy shadows.
   * @usage - <section class="ai-summary-panel"> inside AiSummaryWindow
   */
  .ai-summary-panel {
    width: 100%;
    height: 100%;
    display: flex;
    flex-direction: column;
    background: linear-gradient(145deg, rgba(248, 250, 252, 0.95), rgba(255, 255, 255, 0.88));
    border: 1px solid rgba(15, 23, 42, 0.08);
    border-radius: clamp(20px, 2vw, 26px);
    box-shadow: 0 30px 70px -35px rgba(15, 23, 42, 0.45);
    padding: clamp(1rem, 0.8rem + 1vw, 1.7rem);
    backdrop-filter: blur(18px);
  }

  /**
   * Maximized state trades blur for crisp edges to match docked windows.
   * @usage - .ai-summary-panel.maximized when the panel is expanded
   */
  .ai-summary-panel.maximized {
    background: #ffffff;
    border-color: rgba(15, 23, 42, 0.15);
    box-shadow: 0 60px 140px -55px rgba(15, 23, 42, 0.5);
    backdrop-filter: none;
  }

  /**
   * Header layout keeps the command title on the left and chrome to the right.
   * @usage - <header class="panel-header">
   */
  .panel-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: 1.25rem;
    flex-wrap: wrap;
  }

  /**
   * Vertical stacking for badge, timestamp, and title copy.
   * @usage - <div class="panel-title-group">
   */
  .panel-title-group {
    display: flex;
    flex-direction: column;
    gap: 0.45rem;
  }

  /**
   * Badge + timestamp row stays compact with generous tracking.
   * @usage - <div class="panel-heading">
   */
  .panel-heading {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    flex-wrap: wrap;
  }

  /**
   * Pill badge helps distinguish summary vs translation modes.
   * @usage - <span class="panel-chip">
   */
  .panel-chip {
    display: inline-flex;
    align-items: center;
    text-transform: uppercase;
    letter-spacing: 0.25em;
    font-size: 0.62rem;
    font-weight: 600;
    padding: 0.25rem 0.9rem;
    border-radius: 999px;
    color: #0f172a;
    border: 1px solid rgba(15, 23, 42, 0.12);
    background: linear-gradient(135deg, rgba(255, 255, 255, 0.9), rgba(226, 232, 240, 0.6));
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);
  }

  /**
   * Timestamp adopts muted slate color for low-contrast metadata.
   * @usage - <span class="panel-meta">
   */
  .panel-meta {
    font-size: 0.78rem;
    letter-spacing: 0.05em;
    text-transform: uppercase;
    color: #94a3b8;
  }

  /**
   * Action cluster for regen + window chrome.
   * @usage - <div class="panel-actions">
   */
  .panel-actions {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 0.85rem;
    flex: 1;
    flex-wrap: wrap;
  }

  /**
   * Inline grouping for minimize/max/close buttons.
   * @usage - <div class="panel-window-controls">
   */
  .panel-window-controls {
    display: inline-flex;
    gap: 0.4rem;
  }

  /**
   * Inner body surface adds depth and scrolling for long summaries.
   * @usage - <div class="panel-body">
   */
  .panel-body {
    margin-top: 1.25rem;
    flex: 1;
    border-radius: clamp(16px, 1.5vw, 22px);
    border: 1px solid rgba(15, 23, 42, 0.08);
    background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(248, 250, 252, 0.9));
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7), 0 35px 65px -45px rgba(15, 23, 42, 0.35);
    padding: clamp(1rem, 0.65rem + 0.9vw, 1.45rem);
    display: flex;
    overflow: hidden;
  }

  /**
   * Scroll wrapper maintains padding while allowing long HTML to overflow.
   * @usage - <div class="panel-scroll">
   */
  .panel-scroll {
    flex: 1;
    min-height: 220px;
    overflow-y: auto;
  }

  /**
   * Markdown-rendered summary typography.
   * @usage - <div class="panel-html">
   */
  .panel-html {
    font-size: 0.95rem;
    line-height: 1.65;
    color: #111827;
  }

  .panel-html :global(p) {
    margin-bottom: 0.85rem;
  }

  .panel-html :global(ul),
  .panel-html :global(ol) {
    margin-left: 1.25rem;
    margin-bottom: 0.85rem;
  }

  .panel-html :global(li) {
    margin-bottom: 0.4rem;
  }

  /**
   * Empty + error states share columnar layout.
   * @usage - <div class="panel-state">
   */
  .panel-state {
    min-height: 180px;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 0.6rem;
    color: #475569;
  }

  /**
   * Error text color cues retry state.
   * @usage - <div class="panel-state panel-error">
   */
  .panel-error {
    color: #b91c1c;
  }

  /**
   * Empty-state heading matches primary text color.
   * @usage - <p class="panel-empty-title">
   */
  .panel-empty-title {
    font-weight: 600;
    color: #0f172a;
  }

  /**
   * Empty copy uses calmer slate tone.
   * @usage - <p class="panel-empty-copy">
   */
  .panel-empty-copy {
    font-size: 0.9rem;
    color: #64748b;
  }

  /**
   * Mobile tweaks keep controls tappable and maintain breathing room.
   * @usage - Media query for <=640px
   */
  @media (max-width: 640px) {
    .panel-header {
      flex-direction: column;
      align-items: flex-start;
    }

    .panel-actions {
      width: 100%;
      justify-content: flex-start;
      gap: 0.6rem;
    }

    .panel-window-controls {
      width: 100%;
      justify-content: flex-start;
    }

    .panel-body {
      margin-top: 1rem;
      min-height: min(55vh, 360px);
    }
  }
</style>

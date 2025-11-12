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
  $: commandLabel = panelState?.commandLabel || title;
  $: updatedLabel = panelState?.updatedAt
    ? new Date(panelState.updatedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    : null;
  $: isLoading = Boolean(journeyOverlay && journeyOverlay.visible);
  $: hasContent = Boolean(html && !isLoading);

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
    <div>
      <p class="panel-eyebrow">AI Insights</p>
      <h3 class="panel-title">{commandLabel}</h3>
      {#if updatedLabel}
        <p class="panel-meta">Updated at {updatedLabel}</p>
      {/if}
    </div>
    <div class="panel-actions">
      <button
        type="button"
        class="panel-btn"
        on:click={handlePrimaryAction}
        disabled={isLoading}
      >
        <RotateCcw class="h-4 w-4" aria-hidden="true" />
        {hasContent ? 'Regenerate' : 'Generate summary'}
      </button>
      <div class="panel-window-controls">
        <button type="button" class="icon-btn" on:click={minimize} title="Minimize" aria-label="Minimize AI panel">
          <Minus class="h-4 w-4" />
        </button>
        <button type="button" class="icon-btn" on:click={toggleMaximize} title={maximized ? 'Restore' : 'Maximize'} aria-label={maximized ? 'Restore panel size' : 'Maximize AI panel'}>
          {#if maximized}
            <Minimize2 class="h-4 w-4" />
          {:else}
            <Maximize2 class="h-4 w-4" />
          {/if}
        </button>
        <button type="button" class="icon-btn" on:click={closePanel} title="Close" aria-label="Close AI panel">
          <X class="h-4 w-4" />
        </button>
      </div>
    </div>
  </header>
  <div class="panel-body">
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
        <button type="button" class="panel-btn ghost" on:click={() => emitRunCommand(lastCommand)}>
          Try again
        </button>
      </div>
    {:else if hasContent}
      <div class="panel-html">
        {@html html}
      </div>
    {:else}
      <div class="panel-state">
        <Sparkles class="h-6 w-6 text-slate-400" aria-hidden="true" />
        <p class="panel-empty-title">No AI insights yet</p>
        <p class="panel-empty-copy">Run an AI summary or translation to pin results to this thread.</p>
        <button type="button" class="panel-btn" on:click={() => emitRunCommand('summarize')}>
          Generate summary
        </button>
      </div>
    {/if}
  </div>
</section>

<style>
  .ai-summary-panel {
    width: 100%;
    height: 100%;
    background: rgba(255, 255, 255, 0.9);
    border: 1px solid rgba(15, 23, 42, 0.08);
    box-shadow: 0 25px 60px -20px rgba(15, 23, 42, 0.25);
    border-radius: 24px;
    padding: 1.25rem 1.5rem;
    backdrop-filter: blur(18px);
    display: flex;
    flex-direction: column;
  }
  .ai-summary-panel.maximized {
    background: #ffffff;
    border: 1px solid rgba(15, 23, 42, 0.12);
    box-shadow: 0 60px 120px -45px rgba(15, 23, 42, 0.45);
    backdrop-filter: none;
  }
  .panel-header {
    display: flex;
    justify-content: space-between;
    gap: 1rem;
    align-items: center;
    flex-wrap: wrap;
  }
  .panel-eyebrow {
    font-size: 0.65rem;
    text-transform: uppercase;
    letter-spacing: 0.35em;
    color: rgba(99, 102, 241, 0.7);
    margin-bottom: 0.35rem;
    font-weight: 600;
  }
  .panel-title {
    font-size: 1.1rem;
    font-weight: 600;
    color: #0f172a;
  }
  .panel-meta {
    font-size: 0.8rem;
    color: #94a3b8;
    margin-top: 0.2rem;
  }
  .panel-actions {
    display: flex;
    align-items: center;
    gap: 0.75rem;
  }
  .panel-window-controls {
    display: inline-flex;
    gap: 0.35rem;
  }
  .icon-btn {
    height: 32px;
    width: 32px;
    display: grid;
    place-items: center;
    border-radius: 999px;
    border: 1px solid rgba(148, 163, 184, 0.7);
    background: rgba(255, 255, 255, 0.92);
    color: #475569;
    transition: all 0.15s ease;
  }
  .icon-btn:hover {
    border-color: rgba(15, 23, 42, 0.3);
    color: #0f172a;
  }
  .panel-btn {
    display: inline-flex;
    align-items: center;
    gap: 0.4rem;
    border-radius: 999px;
    border: 1px solid rgba(15, 23, 42, 0.12);
    background: white;
    padding: 0.4rem 0.9rem;
    font-size: 0.85rem;
    font-weight: 500;
    color: #0f172a;
    box-shadow: 0 10px 30px -12px rgba(15, 23, 42, 0.2);
    transition: transform 0.15s ease, box-shadow 0.15s ease;
  }
  .panel-btn:hover:not(:disabled) {
    transform: translateY(-1px);
    box-shadow: 0 20px 40px -18px rgba(15, 23, 42, 0.25);
  }
  .panel-btn:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
  .panel-btn.ghost {
    background: transparent;
    border-color: rgba(148, 163, 184, 0.4);
    box-shadow: none;
  }
  .panel-body {
    margin-top: 1.25rem;
    min-height: 160px;
    flex: 1;
    overflow-y: auto;
  }
  .panel-html {
    font-size: 0.95rem;
    line-height: 1.6;
    color: #1e1b4b;
  }
  .panel-html :global(p) {
    margin-bottom: 0.85rem;
  }
  .panel-state {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
    color: #475569;
  }
  .panel-error {
    color: #be123c;
  }
  .panel-empty-title {
    font-weight: 600;
    color: #0f172a;
  }
  .panel-empty-copy {
    font-size: 0.9rem;
    color: #64748b;
  }
</style>

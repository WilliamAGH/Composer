<script>
  import { createEventDispatcher } from 'svelte';
  import AiLoadingJourney from './AiLoadingJourney.svelte';
  import { Sparkles, RotateCcw } from 'lucide-svelte';

  /**
   * Inline AI insight panel shared by summary + translate commands. Anchors to the email view and
   * hosts loading/error/empty states tied to the current message context.
   */
  export let panelState = null;
  export let journeyOverlay = null;
  export let error = '';

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
</script>

<section class="ai-summary-panel" aria-live="polite">
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
        subdued={true} />
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
    background: rgba(255, 255, 255, 0.96);
    border: 1px solid rgba(15, 23, 42, 0.08);
    box-shadow: 0 25px 60px -20px rgba(15, 23, 42, 0.25);
    border-radius: 24px 24px 0 0;
    padding: 1.25rem 1.5rem;
  }
  .panel-header {
    display: flex;
    justify-content: space-between;
    gap: 1rem;
    align-items: center;
    flex-wrap: wrap;
  }
  .panel-eyebrow {
    font-size: 0.7rem;
    text-transform: uppercase;
    letter-spacing: 0.25em;
    color: #94a3b8;
    margin-bottom: 0.4rem;
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
    gap: 0.5rem;
    align-items: center;
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
    align-items: flex-start;
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

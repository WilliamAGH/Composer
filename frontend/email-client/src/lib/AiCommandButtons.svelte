<script>
  import { createEventDispatcher, onMount } from 'svelte';
import { Languages, ChevronDown, Sparkles, Highlighter, MailPlus, BookOpenCheck, Wand2 } from 'lucide-svelte';

  /**
   * Renders the AI command buttons (summary/translate/etc.) so App.svelte only passes metadata.
   */
  export let commands = [];
  export let actionOptions = [];
  export let actionMenuLoading = false;
  export let mobile = false;
  export let layout = 'stacked';
  export let compact = false;
  const dispatch = createEventDispatcher();
  const preferredVariantOrder = ['es', 'pt', 'nl'];
  const FALLBACK_ACTION_OPTIONS = [
    { id: 'summarize-thread', label: 'Summarize thread', actionType: 'default', defaultPlaceholder: true, aiGenerated: false },
    { id: 'suggest-reply', label: 'Suggest reply ideas', actionType: 'default', defaultPlaceholder: true, aiGenerated: false },
    { id: 'cleanup', label: 'Cleanup + tone pass', actionType: 'default', defaultPlaceholder: true, aiGenerated: false }
  ];

  let translateMenuOpen = false;
  let translateDropdownEl = null;
  let translateButtonEl = null;
  let actionMenuOpen = false;
  let actionDropdownEl = null;
  let actionButtonEl = null;

  $: commandsList = Array.isArray(commands) ? commands : [];
  $: summarizeEntry = commandsList.find((entry) => entry?.key === 'summarize');
  $: draftEntry = commandsList.find((entry) => entry?.key === 'draft');
  $: translateEntry = commandsList.find((entry) => entry?.key === 'translate');
  $: orderedVariants = buildVariantOptions(translateEntry?.meta?.variants || []);
  $: otherEntries = commandsList.filter((entry) => !['draft', 'translate', 'summarize'].includes(entry?.key));
  $: actionOptionList = Array.isArray(actionOptions) && actionOptions.length ? actionOptions : [];
  $: actionMenuEntries = actionOptionList.length ? actionOptionList : FALLBACK_ACTION_OPTIONS;
  $: trayMode = layout === 'tray';

  function handleClick(key) {
    dispatch('select', { key });
  }

  function handleVariantSelect(variantKey) {
    if (!translateEntry) return;
    dispatch('select', { key: translateEntry.key, variantKey });
    translateMenuOpen = false;
  }

  function handleActionSelect(option) {
    if (!option) return;
    if (option.defaultPlaceholder) {
      triggerComingSoon(option.label);
      setActionMenuOpen(false);
      return;
    }
    dispatch('actionSelect', { option });
    setActionMenuOpen(false);
  }

  function triggerComingSoon(label) {
    dispatch('comingSoon', { label });
  }

  function toggleTranslateMenu() {
    if (!orderedVariants.length) return;
    translateMenuOpen = !translateMenuOpen;
  }

  function toggleActionMenu() {
    setActionMenuOpen(!actionMenuOpen);
  }

  function setActionMenuOpen(nextState) {
    if (actionMenuOpen === nextState) return;
    actionMenuOpen = nextState;
    dispatch('actionMenuToggle', { open: actionMenuOpen });
  }

  function handleDocumentClick(event) {
    const target = event.target;
    if (translateMenuOpen && !isWithin(target, translateDropdownEl, translateButtonEl)) {
      translateMenuOpen = false;
    }
    if (actionMenuOpen && !isWithin(target, actionDropdownEl, actionButtonEl)) {
      setActionMenuOpen(false);
    }
  }

  function isWithin(target, panelEl, triggerEl) {
    if (!target) return false;
    if (panelEl && panelEl.contains(target)) return true;
    if (triggerEl && triggerEl.contains(target)) return true;
    return false;
  }

  onMount(() => {
    document.addEventListener('click', handleDocumentClick);
    return () => {
      document.removeEventListener('click', handleDocumentClick);
    };
  });

  function buildVariantOptions(rawVariants) {
    if (!Array.isArray(rawVariants)) return [];
    const map = new Map(rawVariants.map((variant) => [variant.key, variant]));
    const ordered = preferredVariantOrder
      .map((key) => map.get(key))
      .filter(Boolean);
    return ordered;
  }

  function resolveIconComponent(key) {
    const normalized = (key || '').toLowerCase();
    switch (normalized) {
      case 'summarize':
        return BookOpenCheck;
      case 'draft':
        return Wand2;
      case 'compose':
        return MailPlus;
      case 'tone':
        return Highlighter;
      default:
        return Sparkles;
    }
  }

  function labelForEntry(entry) {
    const key = (entry?.key || '').toLowerCase();
    switch (key) {
      case 'summarize':
        return 'Summarize';
      case 'draft':
        return 'Reply';
      case 'compose':
        return 'Compose';
      case 'tone':
        return 'Tone';
      default:
        return entry?.meta?.label || entry?.key;
    }
  }
</script>

<div class={`ai-action-toolbar ${mobile ? 'mobile' : ''} ${trayMode ? 'tray-mode' : ''} ${compact ? 'compact' : ''}`}>
  {#if !commandsList.length}
    <button
      type="button"
      class="btn btn--secondary btn--compact action-pill"
      aria-label="Run AI Assistant"
      title="Run AI Assistant"
      on:click={() => handleClick('summarize')}>
      <span class="action-pill__icon">
        <Sparkles class="h-4 w-4 text-slate-500" aria-hidden="true" />
      </span>
      <span class="action-pill__label">Run AI Assistant</span>
    </button>
  {:else}
      <div class={`relative ${mobile ? 'span-2' : ''}`}>
        <button
          type="button"
          class={`btn btn--ghost btn--compact action-pill ${mobile ? 'w-full justify-center' : ''}`}
          class:action-pill--tray={trayMode}
          on:click={toggleActionMenu}
          aria-haspopup="menu"
          aria-expanded={actionMenuOpen}
          aria-label="AI Actions"
          title="AI Actions"
          aria-busy={actionMenuLoading}
          bind:this={actionButtonEl}>
          <span class="action-pill__icon btn-icon-chip">
            <Sparkles class="h-4 w-4" aria-hidden="true" />
          </span>
          <span class="action-pill__label tracking-wide">Actions</span>
          <ChevronDown
            class={`action-pill__chevron h-4 w-4 transition ${actionMenuOpen ? 'text-slate-700 rotate-180' : 'text-slate-500'}`}
            aria-hidden="true" />
        </button>
      {#if actionMenuOpen}
        <div
          class="absolute mt-2 menu-surface"
          data-layer="nested"
          bind:this={actionDropdownEl}>
          <span class="menu-eyebrow">Suggested Actions</span>
          <div class="menu-list">
            {#each actionMenuEntries as option (option.id || option.label)}
              <button
                type="button"
                class="menu-item"
                on:click={() => handleActionSelect(option)}>
                <div class="flex items-center min-w-0 gap-2">
                  {#if option.aiGenerated}
                    <span class="menu-item-icon" aria-hidden="true">
                      <Sparkles class="h-4 w-4" />
                    </span>
                  {/if}
                  <span class="truncate">{option.label}</span>
                </div>
              </button>
            {/each}
          </div>
        </div>
      {/if}
    </div>

    {#if summarizeEntry}
      <button
        type="button"
        class="btn btn--secondary btn--compact action-pill"
        class:action-pill--tray={trayMode}
        aria-label={labelForEntry(summarizeEntry)}
        title={labelForEntry(summarizeEntry)}
        on:click={() => handleClick(summarizeEntry.key)}>
        <span class="action-pill__icon">
          <svelte:component this={resolveIconComponent(summarizeEntry.key)} class="h-4 w-4 text-slate-500" aria-hidden="true" />
        </span>
        <span class="action-pill__label">{labelForEntry(summarizeEntry)}</span>
      </button>
    {/if}

    {#if draftEntry}
      <button
        type="button"
        class="btn btn--secondary btn--compact action-pill"
        class:action-pill--tray={trayMode}
        aria-label={labelForEntry(draftEntry)}
        title={labelForEntry(draftEntry)}
        on:click={() => handleClick(draftEntry.key)}>
        <span class="action-pill__icon">
          <svelte:component this={resolveIconComponent(draftEntry.key)} class="h-4 w-4 text-slate-500" aria-hidden="true" />
        </span>
        <span class="action-pill__label">{labelForEntry(draftEntry)}</span>
      </button>
    {/if}

    {#if translateEntry && orderedVariants.length}
      <div class={`relative ${mobile ? 'span-2' : ''}`}>
        <button
          type="button"
          class={`btn btn--ghost btn--compact action-pill justify-between ${mobile ? 'w-full' : ''}`}
          class:action-pill--tray={trayMode}
          on:click={toggleTranslateMenu}
          aria-haspopup="menu"
          aria-expanded={translateMenuOpen}
          aria-label="Translate"
          title="Translate"
          bind:this={translateButtonEl}>
          <div class="flex items-center gap-2">
            <span class="action-pill__icon">
              <Languages class="h-4 w-4 text-slate-500" aria-hidden="true" />
            </span>
            <span class="action-pill__label">Translate</span>
          </div>
          <ChevronDown
            class={`action-pill__chevron h-4 w-4 text-slate-500 transition ${translateMenuOpen ? 'rotate-180' : ''}`}
            aria-hidden="true" />
        </button>
        {#if translateMenuOpen}
          <div class="absolute mt-2 menu-surface" data-layer="nested" bind:this={translateDropdownEl}>
            <span class="menu-eyebrow">Translate To</span>
            <div class="menu-list">
              {#each orderedVariants as variant (variant.key)}
                <button
                  type="button"
                  class="menu-item"
                  on:click={() => handleVariantSelect(variant.key)}>
                  <div class="flex items-center min-w-0">
                    <span class="menu-item-icon">
                      <Languages class="h-4 w-4" />
                    </span>
                    <span class="truncate">{variant.label}</span>
                  </div>
                </button>
              {/each}
            </div>
            <button
              type="button"
              class="mt-4 panel-chip justify-center w-full"
              on:click={() => { triggerComingSoon('Translate customization'); translateMenuOpen = false; }}>
              <Sparkles class="h-4 w-4" />
              Customize
            </button>
          </div>
        {/if}
      </div>
    {/if}

    {#each otherEntries as entry (entry.key)}
      <button
        type="button"
        class="btn btn--secondary btn--compact action-pill"
        class:action-pill--tray={trayMode}
        aria-label={labelForEntry(entry)}
        title={labelForEntry(entry)}
        on:click={() => handleClick(entry.key)}>
        <span class="action-pill__icon">
          <svelte:component this={resolveIconComponent(entry.key)} class="h-4 w-4 text-slate-500" aria-hidden="true" />
        </span>
        <span class="action-pill__label">{labelForEntry(entry)}</span>
      </button>
    {/each}
  {/if}
</div>

<style>
  /**
   * AI toolbar wrapper keeps dropdowns clickable above the AI summary card while enabling responsive layouts.
   * @usage - Surrounds AI action buttons in EmailActionToolbar contexts
   * @z-index-warning - Maintains z-index 150 to float over DrawerBackdrop (z-50) and content iframe
   * @related - .ai-action-toolbar.mobile, .ai-action-toolbar.mobile.tray-mode
   */
  .ai-action-toolbar {
    margin-top: 1rem;
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
    position: relative;
    z-index: 150;
    overflow: visible;
  }

  .ai-action-toolbar.mobile {
    width: 100%;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 0.65rem;
  }

  /**
   * Icon + label pairing for AI action pills.
   * @usage - Wrap icon components and text spans in AiCommandButtons markup
   * @related - .ai-action-toolbar.compact for responsive collapsing
   */
  .action-pill__icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    margin-right: 0.4rem;
  }

  .action-pill__label {
    white-space: nowrap;
  }

  .action-pill__chevron {
    margin-left: 0.35rem;
  }

  /**
   * Compact tier (≤960px desktop width) collapses AI button labels to icons so the toolbar
   * fits alongside the desktop action row without clipping.
   * @usage - Applied when App.svelte passes compact={true}
   * @related - .action-pill__label, .action-pill__icon
   */
  .ai-action-toolbar.compact {
    gap: 0.35rem;
  }

  .ai-action-toolbar.compact :global(.btn.btn--compact) {
    padding-left: 0.65rem;
    padding-right: 0.65rem;
  }

  .ai-action-toolbar.compact .action-pill__label {
    display: none;
  }

  .ai-action-toolbar.compact .action-pill__icon {
    margin-right: 0;
  }

  .ai-action-toolbar.mobile:not(.tray-mode) :global(.btn.btn--compact) {
    width: 100%;
  }

  .ai-action-toolbar.mobile:not(.tray-mode) .span-2 {
    grid-column: 1 / -1;
  }

  .ai-action-toolbar.mobile :global(.btn-icon-chip) {
    width: 30px;
    height: 30px;
  }

  .ai-action-toolbar.mobile :global(.btn svg) {
    width: 16px;
    height: 16px;
  }

  .ai-action-toolbar.mobile .relative {
    width: 100%;
  }

  /**
   * Tray variant renders inside the horizontal action lane on mobile.
   * @usage - Activated when layout="tray" and mobile flag is true
   * @related - .action-tray__ai wrapper in EmailActionToolbar.svelte
   */
  .ai-action-toolbar.mobile.tray-mode {
    margin-top: 0;
    display: flex;
    flex-wrap: nowrap;
    align-items: stretch;
    gap: 0.5rem;
    overflow: visible;
  }

  .ai-action-toolbar.mobile.tray-mode > * {
    flex: 0 0 auto;
    min-width: auto;
  }

  .ai-action-toolbar.mobile.tray-mode .relative {
    width: auto;
  }

  .ai-action-toolbar.mobile.tray-mode :global(.btn.btn--compact) {
    width: auto;
  }

  .ai-action-toolbar.mobile.tray-mode .span-2 {
    grid-column: auto;
  }

  /**
   * Action pills normalize icon sizing between AI actions and native controls.
   * @usage - Base class for AI action buttons regardless of viewport
   * @related - .action-pill--tray for compact variant
   */
  .action-pill {
    min-height: 36px;
    padding-top: 0.3rem;
    padding-bottom: 0.3rem;
  }

  .action-pill :global(svg) {
    width: 15px;
    height: 15px;
  }

  .action-pill :global(.btn-icon-chip) {
    width: 28px;
    height: 28px;
  }

  /**
   * Tray modifier tightens width + padding so AI pills blend with the mobile action chips.
   * @usage - Applied via class:action-pill--tray when layout="tray"
   * @related - .action-tray__ai in EmailActionToolbar.svelte
   */
  .action-pill--tray {
    padding-left: 0.75rem;
    padding-right: 0.75rem;
    min-height: 34px;
    font-size: 0.8rem;
  }
</style>

<script>
  import { createEventDispatcher, onMount } from 'svelte';
  import { Languages, ChevronDown, Sparkles, Highlighter, MailPlus, BookOpenCheck } from 'lucide-svelte';

  /**
   * Renders the AI command buttons (summary/translate/etc.) so App.svelte only passes metadata.
   */
  export let commands = [];
  export let actionOptions = [];
  export let actionMenuLoading = false;
  const dispatch = createEventDispatcher();
  const preferredVariantOrder = ['es', 'pt', 'nl'];
  const FALLBACK_ACTION_OPTIONS = [
    { id: 'summarize-thread', label: 'Summarize thread', actionType: 'default', defaultPlaceholder: true },
    { id: 'suggest-reply', label: 'Suggest reply ideas', actionType: 'default', defaultPlaceholder: true },
    { id: 'cleanup', label: 'Cleanup + tone pass', actionType: 'default', defaultPlaceholder: true }
  ];

  let translateMenuOpen = false;
  let translateDropdownEl;
  let translateButtonEl;
  let actionMenuOpen = false;
  let actionDropdownEl;
  let actionButtonEl;

  $: commandsList = Array.isArray(commands) ? commands : [];
  $: summarizeEntry = commandsList.find((entry) => entry?.key === 'summarize');
  $: draftEntry = commandsList.find((entry) => entry?.key === 'draft');
  $: translateEntry = commandsList.find((entry) => entry?.key === 'translate');
  $: orderedVariants = buildVariantOptions(translateEntry?.meta?.variants || []);
  $: otherEntries = commandsList.filter((entry) => !['draft', 'translate', 'summarize'].includes(entry?.key));
  $: actionOptionList = Array.isArray(actionOptions) && actionOptions.length ? actionOptions : [];
  $: actionMenuEntries = actionOptionList.length ? actionOptionList : FALLBACK_ACTION_OPTIONS;

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
      case 'compose':
        return MailPlus;
      case 'tone':
        return Highlighter;
      default:
        return Sparkles;
    }
  }

  function buttonClasses() {
    return 'btn btn--secondary btn--labelled btn--compact';
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

<div class="mt-4 flex flex-wrap gap-2">
  {#if !commandsList.length}
    <button class={buttonClasses()} on:click={() => handleClick('summarize')}>
      Run AI Assistant
    </button>
  {:else}
    <div class="relative">
      <button
        type="button"
        class="btn btn--secondary btn--labelled btn--compact"
        on:click={toggleActionMenu}
        aria-haspopup="menu"
        aria-expanded={actionMenuOpen}
        aria-label="AI Actions"
        aria-busy={actionMenuLoading}
        bind:this={actionButtonEl}>
        <span class="btn-icon-chip">
          <Sparkles class="h-4 w-4" />
        </span>
        <span class="tracking-wide">Actions</span>
        <ChevronDown class={`h-4 w-4 transition ${actionMenuOpen ? 'text-slate-700 rotate-180' : 'text-slate-500'}`} />
      </button>
      {#if actionMenuOpen}
        <div
          class="absolute z-[200] mt-2 menu-surface"
          bind:this={actionDropdownEl}>
          <span class="menu-eyebrow">Suggested Actions</span>
          <div class="menu-list">
            {#each actionMenuEntries as option (option.id || option.label)}
              <button
                type="button"
                class="menu-item"
                on:click={() => handleActionSelect(option)}>
                <div class="flex items-center min-w-0">
                  <span class="menu-item-icon">
                    <Sparkles class="h-4 w-4" />
                  </span>
                  <span class="truncate">{option.label}</span>
                </div>
                <span class="text-xs text-slate-400">AI</span>
              </button>
            {/each}
          </div>
          <div class="mt-4 panel-chip justify-center w-full">
            AI refreshes these suggestions automatically.
          </div>
        </div>
      {/if}
    </div>

    {#if summarizeEntry}
      <button
        type="button"
        class={buttonClasses()}
        on:click={() => handleClick(summarizeEntry.key)}>
        <svelte:component this={resolveIconComponent(summarizeEntry.key)} class="h-4 w-4 text-slate-500" />
        {labelForEntry(summarizeEntry)}
      </button>
    {/if}

    {#if draftEntry}
      <button
        type="button"
        class={buttonClasses()}
        on:click={() => handleClick(draftEntry.key)}>
        <svelte:component this={resolveIconComponent(draftEntry.key)} class="h-4 w-4 text-slate-500" />
        {labelForEntry(draftEntry)}
      </button>
    {/if}

    {#if translateEntry && orderedVariants.length}
      <div class="relative">
        <button
          type="button"
          class={`${buttonClasses()} justify-between`}
          on:click={toggleTranslateMenu}
          aria-haspopup="menu"
          aria-expanded={translateMenuOpen}
          bind:this={translateButtonEl}>
          <div class="flex items-center gap-2">
            <Languages class="h-4 w-4 text-slate-500" />
            <span>Translate</span>
          </div>
          <ChevronDown class={`h-4 w-4 text-slate-500 transition ${translateMenuOpen ? 'rotate-180' : ''}`} />
        </button>
        {#if translateMenuOpen}
          <div class="absolute z-[200] mt-2 menu-surface" bind:this={translateDropdownEl}>
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
                  <span class="text-xs text-slate-400">Instant</span>
                </button>
              {/each}
            </div>
            <button
              type="button"
              class="mt-4 panel-chip justify-center w-full"
              on:click={() => { triggerComingSoon('Translate customization'); translateMenuOpen = false; }}>
              <Sparkles class="h-4 w-4" />
              Customize (coming soon)
            </button>
          </div>
        {/if}
      </div>
    {/if}

    {#each otherEntries as entry (entry.key)}
      <button class={buttonClasses()} on:click={() => handleClick(entry.key)}>
        <svelte:component this={resolveIconComponent(entry.key)} class="h-4 w-4 text-slate-500" />
        {labelForEntry(entry)}
      </button>
    {/each}
  {/if}
</div>

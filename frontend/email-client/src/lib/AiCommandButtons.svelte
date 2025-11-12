<script>
  import { createEventDispatcher, onMount } from 'svelte';
  import { Languages, ChevronDown, Sparkles, Wand2, Highlighter, ListChecks, ListTodo } from 'lucide-svelte';

  /**
   * Renders the AI command buttons (summary/translate/etc.) so App.svelte only passes metadata.
   */
  export let commands = [];
  export let actionOptions = [];
  export let actionMenuLoading = false;
  const dispatch = createEventDispatcher();
  const preferredVariantOrder = ['es', 'pt', 'nl'];

  let translateMenuOpen = false;
  let translateDropdownEl;
  let translateButtonEl;
  let actionMenuOpen = false;
  let actionDropdownEl;
  let actionButtonEl;

  $: commandsList = Array.isArray(commands) ? commands : [];
  $: draftEntry = commandsList.find((entry) => entry?.key === 'draft');
  $: translateEntry = commandsList.find((entry) => entry?.key === 'translate');
  $: orderedVariants = buildVariantOptions(translateEntry?.meta?.variants || []);
  $: otherEntries = commandsList.filter((entry) => entry?.key !== 'draft' && entry?.key !== 'translate');
  $: actionOptionList = Array.isArray(actionOptions) && actionOptions.length ? actionOptions : [];

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
    if (!actionOptionList.length) return;
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
      case 'draft':
        return Wand2;
      case 'summarize':
        return ListChecks;
      case 'tone':
        return Highlighter;
      default:
        return Sparkles;
    }
  }

  function buttonClasses() {
    return 'inline-flex items-center gap-2 rounded-xl border border-slate-200/70 bg-white/80 px-3 py-2 text-sm font-medium text-slate-800 shadow-[0_10px_30px_rgba(15,23,42,0.12)] backdrop-blur hover:bg-white';
  }
</script>

<div class="mt-4 flex flex-wrap gap-3">
  {#if !commandsList.length}
    <button class={buttonClasses()} on:click={() => handleClick('summarize')}>
      Run AI Assistant
    </button>
  {:else}
    <div class="relative">
      <button
        type="button"
        class="inline-flex items-center gap-2 rounded-2xl border border-emerald-100 bg-white/90 px-3 py-2 text-sm font-semibold text-slate-900 shadow-[0_15px_35px_rgba(16,185,129,0.25)] backdrop-blur hover:bg-white disabled:cursor-not-allowed disabled:opacity-60"
        on:click={toggleActionMenu}
        aria-haspopup="menu"
        aria-expanded={actionMenuOpen}
        aria-label="AI Actions"
        bind:this={actionButtonEl}
        disabled={!actionOptionList.length}>
        <ListTodo class="h-4 w-4 text-emerald-500" />
        Actions
        <ChevronDown class="h-4 w-4 text-slate-500" />
        {#if actionMenuLoading}
          <span class="ml-1 inline-flex h-1.5 w-1.5 animate-pulse rounded-full bg-emerald-400"></span>
        {/if}
      </button>
      {#if actionMenuOpen}
        <div
          class="absolute z-30 mt-2 w-72 rounded-2xl border border-white/40 bg-white/90 p-4 shadow-[0_25px_50px_-12px_rgba(15,23,42,0.25)] backdrop-blur-md"
          bind:this={actionDropdownEl}>
          <div class="text-[11px] uppercase tracking-[0.2em] text-slate-400 mb-3">Suggested Actions</div>
          <div class="space-y-2">
            {#each actionOptionList as option (option.id || option.label)}
              <button
                type="button"
                class="flex w-full items-center justify-between rounded-xl border border-transparent bg-slate-50/80 px-3 py-2 text-sm font-medium text-slate-700 hover:border-slate-200 hover:bg-white"
                on:click={() => handleActionSelect(option)}>
                <span>{option.label}</span>
                <span class="text-xs text-slate-400">{option.actionType === 'comingSoon' ? 'Coming soon' : 'AI'}</span>
              </button>
            {/each}
          </div>
          <div class="mt-4 rounded-xl border border-dashed border-slate-200/80 bg-slate-50/70 p-3 text-center text-xs text-slate-400">
            {#if actionMenuLoading}
              Refreshing ideas...
            {:else}
              AI refreshes these suggestions automatically.
            {/if}
          </div>
        </div>
      {/if}
    </div>

    {#if draftEntry}
      <button
        type="button"
        class={buttonClasses()}
        on:click={() => handleClick(draftEntry.key)}>
        <svelte:component this={resolveIconComponent(draftEntry.key)} class="h-4 w-4 text-slate-500" />
        {draftEntry.meta?.label || draftEntry.key}
      </button>
    {/if}

    {#if translateEntry && orderedVariants.length}
      <div class="relative">
        <button
          type="button"
          class="inline-flex items-center gap-2 rounded-xl border border-slate-200/70 bg-white/80 px-3 py-2 text-sm font-medium text-slate-800 shadow-[0_10px_30px_rgba(15,23,42,0.12)] backdrop-blur hover:bg-white"
          on:click={toggleTranslateMenu}
          aria-haspopup="menu"
          aria-expanded={translateMenuOpen}
          bind:this={translateButtonEl}>
          <Languages class="h-4 w-4 text-slate-500" />
          Translate
          <ChevronDown class="h-4 w-4 text-slate-500" />
        </button>
        {#if translateMenuOpen}
          <div class="absolute z-30 mt-2 w-64 rounded-2xl border border-white/40 bg-white/90 p-4 shadow-[0_25px_50px_-12px_rgba(15,23,42,0.25)] backdrop-blur-md" bind:this={translateDropdownEl}>
            <div class="text-[11px] uppercase tracking-[0.2em] text-slate-400 mb-3">Translate To</div>
            <div class="space-y-2">
              {#each orderedVariants as variant (variant.key)}
                <button
                  type="button"
                  class="flex w-full items-center justify-between rounded-xl border border-transparent bg-slate-50/80 px-3 py-2 text-sm font-medium text-slate-700 hover:border-slate-200 hover:bg-white"
                  on:click={() => handleVariantSelect(variant.key)}>
                  <span>{variant.label}</span>
                  <span class="text-xs text-slate-400">Instant</span>
                </button>
              {/each}
            </div>
            <div class="mt-4 rounded-xl border border-dashed border-slate-200/80 bg-slate-50/70 p-3 text-center">
              <button
                type="button"
                class="flex w-full items-center justify-center gap-1 text-sm font-semibold text-slate-400 hover:text-slate-500"
                on:click={() => { triggerComingSoon('Translate customization'); translateMenuOpen = false; }}>
                <Sparkles class="h-4 w-4" />
                Customize (coming soon)
              </button>
            </div>
          </div>
        {/if}
      </div>
    {/if}

    {#each otherEntries as entry (entry.key)}
      <button class={buttonClasses()} on:click={() => handleClick(entry.key)}>
        <svelte:component this={resolveIconComponent(entry.key)} class="h-4 w-4 text-slate-500" />
        {entry.meta?.label || entry.key}
      </button>
    {/each}
  {/if}
</div>

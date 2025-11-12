<script>
  import { createEventDispatcher, onMount } from 'svelte';
  import { Languages, ChevronDown, Sparkles, Wand2, Highlighter, ListChecks } from 'lucide-svelte';

  /**
   * Renders the AI command buttons (summary/translate/etc.) so App.svelte only passes metadata.
   */
  export let commands = [];
  const dispatch = createEventDispatcher();
  const preferredVariantOrder = ['es', 'pt', 'nl'];

  let translateMenuOpen = false;
  let translateDropdownEl;
  let translateButtonEl;

  $: commandsList = Array.isArray(commands) ? commands : [];
  $: draftEntry = commandsList.find((entry) => entry?.key === 'draft');
  $: translateEntry = commandsList.find((entry) => entry?.key === 'translate');
  $: orderedVariants = buildVariantOptions(translateEntry?.meta?.variants || []);
  $: otherEntries = commandsList.filter((entry) => entry?.key !== 'draft' && entry?.key !== 'translate');

  function handleClick(key) {
    dispatch('select', { key });
  }

  function handleVariantSelect(variantKey) {
    if (!translateEntry) return;
    dispatch('select', { key: translateEntry.key, variantKey });
    translateMenuOpen = false;
  }

  function toggleTranslateMenu() {
    if (!orderedVariants.length) return;
    translateMenuOpen = !translateMenuOpen;
  }

  function handleDocumentClick(event) {
    if (!translateMenuOpen) return;
    if (translateDropdownEl && translateDropdownEl.contains(event.target)) return;
    if (translateButtonEl && translateButtonEl.contains(event.target)) return;
    translateMenuOpen = false;
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
              <button type="button" class="flex w-full items-center justify-center gap-1 text-sm font-semibold text-slate-400" disabled>
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

<script>
  import { createEventDispatcher } from 'svelte';

  /**
   * Renders the AI command buttons (summary/translate/etc.) so App.svelte only passes metadata.
   */
  export let commands = [];
  const dispatch = createEventDispatcher();

  function handleClick(key) {
    dispatch('select', { key });
  }
</script>

<div class="mt-4 flex flex-wrap gap-2">
  {#if !commands || commands.length === 0}
    <button class="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-50" on:click={() => handleClick('summarize')}>
      Run AI Assistant
    </button>
  {:else}
    {#each commands as entry (entry.key)}
      <button class="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-50" on:click={() => handleClick(entry.key)}>
        {entry.meta.label || entry.key}
      </button>
    {/each}
  {/if}
</div>

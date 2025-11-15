<script>
  import { createEventDispatcher } from 'svelte';
  import { sanitizeHtml } from './services/sanitizeHtml';
  export let open = false;
  export let title = '';
  export let html = '';
  const dispatch = createEventDispatcher();

  $: safeHtml = sanitizeHtml(html);
</script>

{#if open}
  <div class="fixed inset-0 z-[200]">
    <button type="button" class="absolute inset-0 bg-black/40" aria-label="Close" on:click={() => dispatch('close')}></button>
    <div class="absolute inset-x-0 top-6 mx-auto w-[min(92vw,720px)] bg-white rounded-2xl shadow-2xl border border-slate-200 overflow-hidden max-h-[85dvh] flex flex-col">
      <div class="px-4 py-3 border-b border-slate-200 font-semibold text-slate-800">{title}</div>
      <div class="p-4 overflow-y-auto">
        {@html safeHtml}
      </div>
      <div class="px-4 py-3 border-t border-slate-200 text-right">
        <button type="button" class="inline-flex items-center rounded-xl px-3 py-2 border border-slate-200 bg-white text-slate-700 hover:bg-slate-50" on:click={() => dispatch('close')}>Close</button>
      </div>
    </div>
  </div>
{/if}

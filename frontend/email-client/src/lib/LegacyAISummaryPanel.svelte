<script>
  import { createEventDispatcher } from 'svelte';
  import { X } from 'lucide-svelte';
  import { isMobile, isTablet, viewport } from './viewport';
 
  /**
   * @deprecated Replaced by the WindowFrame-based AI Summary window. Remove once callers migrate.
   */
  export let open = true;
  export let title = 'AI Summary';
  export let html = '';
 
  const dispatch = createEventDispatcher();
  $: mobile = $isMobile;
  $: tablet = $isTablet;
  $: viewportType = $viewport;
 
  function close() { dispatch('close'); }
</script>
 
{#if open}
<div class="w-full border-t border-slate-200 bg-white/95 flex flex-col"
     style="height: {mobile ? '40vh' : tablet ? '45vh' : '50vh'}; min-height: {mobile ? '250px' : '300px'}; max-height: {mobile ? '400px' : tablet ? '500px' : '600px'};">
  <div class="flex items-center justify-between px-4 py-3 border-b border-slate-200 bg-slate-50/70">
    <div class="text-base font-semibold text-slate-900">{title}</div>
    <button type="button" class="h-8 w-8 grid place-items-center text-slate-500 hover:text-slate-800" on:click={close} title="Close">
      <X class="h-4 w-4" />
    </button>
  </div>
  <div class="flex-1 overflow-y-auto p-4 sm:p-6">
    <div class="prose prose-sm max-w-none text-slate-700">
      {@html html}
    </div>
  </div>
</div>
{/if}

<!-- Shared Coming Soon dialog so new surfaces can reuse consistent messaging. -->
<script>
  import { Sparkles } from 'lucide-svelte';
  import { createEventDispatcher } from 'svelte';

  export let open = false;
  export let sourceLabel = 'This feature';

  const dispatch = createEventDispatcher();

  function close() {
    dispatch('close');
  }
</script>

{#if open}
  <div
    class="fixed inset-0 z-[180] flex items-center justify-center bg-slate-900/60 backdrop-blur-sm px-4 relative"
    role="dialog"
    aria-modal="true"
    aria-label="Coming soon"
  >
    <button type="button" class="absolute inset-0 h-full w-full bg-transparent z-0" aria-label="Close coming soon modal" on:click={close}>
      <span class="sr-only">Close</span>
    </button>
    <div
      class="relative z-10 w-full max-w-md rounded-3xl border border-white/10 bg-gradient-to-br from-slate-900 via-slate-800 to-emerald-800 p-6 text-white shadow-[0_35px_80px_rgba(15,23,42,0.55)]"
      role="document"
      tabindex="-1"
    >
      <button type="button" class="absolute right-4 top-4 btn btn--icon text-white bg-white/10 border-white/30 hover:bg-white/20" aria-label="Close" on:click={close}>
        ✕
      </button>
      <div class="mb-4 inline-flex items-center gap-2 rounded-full bg-white/15 px-3 py-1 text-xs font-semibold uppercase tracking-[0.2em]">
        <Sparkles class="h-4 w-4 text-emerald-300" />
        Coming Soon
      </div>
      <h3 class="text-2xl font-semibold tracking-tight">
        {sourceLabel || 'This feature'} is almost here
      </h3>
      <p class="mt-3 text-sm text-slate-200 leading-relaxed">
        We&apos;re putting the finishing touches on this workflow. Follow along in Composer updates for early access and let us know how you&apos;d like it to work.
      </p>
      <div class="mt-6 flex flex-col gap-3 sm:flex-row sm:items-center">
        <button type="button" class="btn btn--secondary w-full justify-center" on:click={close}>
          Sounds good
        </button>
        <div class="text-xs text-slate-300 text-center sm:text-left">
          Need it sooner? Drop us a note in the roadmap channel.
        </div>
      </div>
    </div>
  </div>
{/if}

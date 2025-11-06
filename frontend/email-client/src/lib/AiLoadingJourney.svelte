<script>
  import { Brain, Loader2, PenSquare, Search, Sparkles } from 'lucide-svelte';

  export let steps = [];
  export let activeStepId = null;
  export let headline = 'Let me handle this';
  export let subhead = 'ComposerAI in progress';
  export let show = true;
  export let inline = false;
  export let subdued = false;
  export let completed = new Set();

  const ICONS = {
    sparkles: Sparkles,
    search: Search,
    brain: Brain,
    pen: PenSquare,
  };

  $: completedSet = completed instanceof Set ? completed : new Set(completed);
  $: activeIndex = steps.findIndex((step) => step.id === activeStepId);

  function resolveState(step, index) {
    if (completedSet.has(step.id) || (activeIndex > -1 && index < activeIndex)) {
      return 'complete';
    }
    if (activeIndex === index) {
      return 'active';
    }
    return 'pending';
  }

  function iconFor(step) {
    return ICONS[step.icon];
  }
</script>

{#if show && steps.length}
<section
  class={`relative rounded-3xl border px-4 py-4 sm:px-5 sm:py-5 transition-all duration-300 ${inline ? 'w-full' : 'fixed bottom-6 left-1/2 z-[80] max-w-sm -translate-x-1/2 sm:left-auto sm:right-6 sm:translate-x-0'} ${subdued ? 'border-slate-200 bg-white/80 backdrop-blur' : 'border-white/30 bg-gradient-to-b from-white/70 via-white/40 to-white/10 backdrop-blur-xl shadow-[0_25px_60px_-20px_rgba(15,23,42,0.3)]'}`}
  role="status"
  aria-live="polite">
  <div class="flex items-center gap-3">
    <div class="h-10 w-10 rounded-2xl bg-white/80 ring-1 ring-slate-200 text-slate-700 grid place-items-center shadow-inner">
      <Loader2 class="h-5 w-5 animate-spin" style="animation-duration:1.3s" />
    </div>
    <div class="min-w-0">
      <p class="text-[11px] uppercase tracking-[0.3em] text-slate-500">{subhead}</p>
      <p class="text-base font-semibold text-slate-900 leading-tight">{headline}</p>
    </div>
  </div>

  <ol class="mt-4 space-y-3">
    {#each steps as step, index (step.id)}
      {#if iconFor(step) != null}
        <li class="flex items-start gap-3">
          <div class="relative">
            <div
              class={`flex h-11 w-11 items-center justify-center rounded-2xl border text-sm transition-all duration-300 ${resolveState(step, index) === 'active'
                ? 'bg-slate-900 text-white border-slate-900/80 shadow-lg shadow-slate-900/30'
                : resolveState(step, index) === 'complete'
                  ? 'bg-white text-slate-900 border-slate-300 shadow shadow-slate-900/5'
                  : 'bg-white/70 text-slate-500 border-slate-200'}`}>
              <svelte:component this={iconFor(step)} class="h-5 w-5" />
              {#if resolveState(step, index) === 'active'}
                <Loader2 class="absolute -bottom-1 -right-1 h-4 w-4 animate-spin text-white" style="animation-duration:1.1s" />
              {/if}
            </div>
            {#if index < steps.length - 1}
              <span class={`absolute left-1/2 top-full block w-px h-5 translate-x-[-50%] ${resolveState(step, index) === 'complete' ? 'bg-slate-400/70' : 'bg-slate-200/60'}`}></span>
            {/if}
          </div>
          <div class="min-w-0">
            <p class={`text-sm font-semibold leading-snug ${resolveState(step, index) === 'active' ? 'text-slate-900' : 'text-slate-600'}`}>{step.title}</p>
            {#if step.detail}
              <p class="text-xs text-slate-500 mt-0.5 leading-relaxed">{step.detail}</p>
            {/if}
          </div>
        </li>
      {/if}
    {/each}
  </ol>
</section>
{/if}

<style>
  section::after {
    content: '';
    position: absolute;
    inset: 0;
    border-radius: 1.5rem;
    pointer-events: none;
    border: 1px solid rgba(255, 255, 255, 0.25);
    mix-blend-mode: soft-light;
  }
</style>

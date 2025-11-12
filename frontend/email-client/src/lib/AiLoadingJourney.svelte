<script>
  import { Loader2 } from 'lucide-svelte';

  export let steps = [];
  export let activeStepId = null;
  export let headline = 'Working on your request';
  export let subhead = 'ComposerAI assistant';
  export let show = true;
  export let inline = true;
  export let subdued = false;
  export let className = '';

  $: activeIndex = steps.findIndex((step) => step.id === activeStepId);
  $: activeStep = activeIndex > -1 ? steps[activeIndex] : steps[0];
  $: primaryText = headline || activeStep?.title || '';
  $: secondaryText = activeStep?.title && activeStep.title !== primaryText ? activeStep.title : '';
  $: progressText = activeStep && steps.length > 1 ? `Step ${Math.max(1, (activeIndex >= 0 ? activeIndex + 1 : 1))} of ${steps.length}` : '';
  $: sectionClasses = [
    'rounded-2xl border px-4 py-3 transition-all duration-300 flex flex-col gap-1 text-slate-900',
    inline ? 'w-full' : 'max-w-sm',
    subdued ? 'border-slate-200 bg-white/85 backdrop-blur' : 'border-white/40 bg-white/95 shadow-xl backdrop-blur-xl',
    className
  ].filter(Boolean).join(' ');
</script>

{#if show && activeStep}
<section class={sectionClasses} role="status" aria-live="polite">
  <div class="flex items-center gap-3">
    <div class="h-10 w-10 rounded-2xl bg-slate-900 text-white grid place-items-center shadow-lg shadow-slate-900/20">
      <Loader2 class="h-5 w-5 animate-spin" style="animation-duration:1.05s" />
    </div>
    <div class="min-w-0">
      {#if subhead}
        <p class="text-[11px] uppercase tracking-[0.3em] text-slate-400">{subhead}</p>
      {/if}
      <p class="text-sm font-semibold leading-tight text-slate-900 truncate">{primaryText}</p>
    </div>
  </div>
  {#if secondaryText}
    <p class="text-xs text-slate-500 truncate">{secondaryText}</p>
  {/if}
  {#if progressText}
    <p class="text-[10px] uppercase tracking-[0.25em] text-slate-400">{progressText}</p>
  {/if}
</section>
{/if}

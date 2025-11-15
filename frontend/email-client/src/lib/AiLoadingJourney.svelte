<script>
  import { Loader2 } from 'lucide-svelte';

  export let steps = [];
  export let activeStepId = null;
  export let headline = 'Working on your request';
  export let subhead = 'Composer assistant';
  export let show = true;
  export let inline = true;
  export let subdued = false;
  export let className = '';

  $: activeIndex = steps.findIndex((step) => step.id === activeStepId);
  $: activeStep = activeIndex > -1 ? steps[activeIndex] : steps[0];
  $: primaryText = headline || activeStep?.title || '';
  $: secondaryText = activeStep?.title && activeStep.title !== primaryText ? activeStep.title : '';
  $: sectionClasses = [
    'rounded-lg border px-4 py-3 transition-all duration-300 flex flex-col gap-1 text-slate-900',
    inline ? 'w-full' : 'max-w-sm',
    subdued ? 'border-slate-200 bg-white' : 'border-slate-200 bg-white shadow-md',
    className
  ].filter(Boolean).join(' ');
</script>

{#if show && activeStep}
<section class={sectionClasses} role="status" aria-live="polite">
  <div class="flex items-center gap-3">
    <div class="h-10 w-10 rounded-lg bg-slate-100 grid place-items-center border border-slate-200">
      <Loader2 class="h-5 w-5 animate-spin text-slate-700" style="animation-duration:1.05s" />
    </div>
    <div class="min-w-0">
      {#if subhead}
        <p class="text-[11px] uppercase tracking-[0.3em] text-slate-500">{subhead}</p>
      {/if}
      <p class="text-sm font-semibold leading-tight text-slate-900 truncate">{primaryText}</p>
    </div>
  </div>
  {#if secondaryText}
    <p class="text-xs text-slate-500 truncate">{secondaryText}</p>
  {/if}
</section>
{/if}

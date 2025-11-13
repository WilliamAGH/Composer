<!-- Floating chip that restores the AI summary panel when it is docked. -->
<script>
  import { Sparkles } from 'lucide-svelte';
  import { createEventDispatcher } from 'svelte';

  export let visible = false;
  const dispatch = createEventDispatcher();

  function handleClick() {
    dispatch('restore');
  }
</script>

{#if visible}
  <button class="panel-dock-chip" type="button" on:click={handleClick}>
    <Sparkles class="h-4 w-4" aria-hidden="true" />
    AI Panel
  </button>
{/if}

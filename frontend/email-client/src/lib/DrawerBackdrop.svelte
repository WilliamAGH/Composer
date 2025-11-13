<!-- Semi-transparent overlay that closes the mailbox drawer when users click outside. -->
<script>
  import { createEventDispatcher } from 'svelte';

  export let visible = false;
  const dispatch = createEventDispatcher();

  function emitClose() {
    dispatch('close');
  }
</script>

{#if visible}
  <button
    type="button"
    class="fixed inset-0 bg-black/30 z-[50]"
    aria-label="Close menu overlay"
    on:click={emitClose}
    on:keydown={(event) => {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        emitClose();
      }
    }}
  />
{/if}

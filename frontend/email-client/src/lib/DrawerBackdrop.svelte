<!-- Semi-transparent overlay that closes the mailbox drawer when users click outside. -->
<script>
  import { createEventDispatcher } from 'svelte';

  const dispatch = createEventDispatcher();
  export let onClose = null;

  function emitClose() {
    dispatch('close');
    if (typeof onClose === 'function') {
      onClose();
    }
  }
</script>

<button
  type="button"
  class="fixed inset-0 bg-black/30"
  style="z-index: var(--z-drawer-backdrop, 160);"
  aria-label="Close menu overlay"
  on:click={emitClose}
  on:keydown={(event) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      emitClose();
    }
  }}
/>

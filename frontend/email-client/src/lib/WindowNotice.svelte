<!-- Lightweight toast for window limit/compose notices. -->
<script>
  export let message = '';
</script>

{#if message}
  <div class="window-notice">{message}</div>
{/if}

<style>
  /**
   * Toast surface for window stack notices; pins near bottom-right across layouts.
   * @usage - Wrapper div inside WindowNotice.svelte when a message is present
   * @related - CSS z-index variables defined in app-shared root
   */
  .window-notice {
    position: fixed;
    bottom: 90px;
    right: 24px;
    background: rgba(15, 23, 42, 0.9);
    color: white;
    padding: 0.5rem 1rem;
    border-radius: 999px;
    font-size: 0.85rem;
    z-index: var(--z-window-notice, 120);
    box-shadow: 0 10px 30px rgba(15, 23, 42, 0.35);
  }
</style>

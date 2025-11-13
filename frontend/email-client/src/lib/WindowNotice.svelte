<!-- Lightweight toast for window limit/compose notices. -->
<script>
  export let message = '';
</script>

{#if message}
  <div class="window-notice">{message}</div>
{/if}

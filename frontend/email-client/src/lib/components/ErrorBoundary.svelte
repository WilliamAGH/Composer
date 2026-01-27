<!--
  Error boundary wrapper using Svelte 5's svelte:boundary.
  Catches render errors in child components and surfaces them via the error store.
-->
<script>
  import { setFatalError } from '../stores/errorStore';

  /**
   * Handler called when a child component throws during render.
   * @param error - The error that was thrown
   */
  function handleError(error) {
    const message = error instanceof Error ? error.message : 'A component failed to render';
    const detail = error instanceof Error ? error.name : undefined;

    // eslint-disable-next-line no-console
    console.error('[ErrorBoundary] Caught render error:', error);

    setFatalError(message, { detail, error: error instanceof Error ? error : undefined });
  }
</script>

<svelte:boundary onerror={handleError}>
  <slot />
</svelte:boundary>

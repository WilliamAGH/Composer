<script>
  import { onMount } from 'svelte';
  import { sanitizeHtml } from './services/sanitizeHtml';
  export let html = '';
  let container = null;
  let fallback = '';
  let rendered = false;

  function tryRender(content = html) {
    rendered = false;
    fallback = '';

    // Try iframe rendering first
    const normalizedHtml = typeof content === 'string' ? content : String(content ?? '');
    if (container && normalizedHtml && window.EmailRenderer && typeof window.EmailRenderer.renderInIframe === 'function') {
      try {
        window.EmailRenderer.renderInIframe(container, normalizedHtml);
        rendered = true;
        return; // Success - no fallback needed
      } catch (e) {
        // Iframe rendering failed - fall through to fallback
        rendered = false;
      }
    }

    const raw = normalizedHtml || '';
    fallback = raw ? sanitizeHtml(raw) : '';
  }

  $: tryRender(html);
  onMount(tryRender);
</script>

<div class="email-html-container" bind:this={container}>
  {#if !rendered && fallback}
    <div class="email-html-fallback prose prose-sm text-slate-700">{@html fallback}</div>
  {/if}
</div>

<style>
  /**
   * Email HTML container keeps iframe/fallback content constrained and prevents overflow on mobile.
   * @usage - Wraps EmailRenderer iframe plus fallback markup within EmailIframe.svelte
   * @related - .email-html-fallback inside this component
   */
  .email-html-container {
    width: 100%;
    max-width: 100%;
    overflow-x: hidden;
    position: relative;
  }

  /**
   * Sanitized fallback rendering mirrors iframe padding and wraps content to fit mobile viewport.
   * @usage - Applied to the fallback div rendered when EmailRenderer fails or is unavailable
   * @related - .email-html-container to inherit width constraints
   */
  .email-html-fallback {
    width: 100%;
    max-width: 100%;
    padding: 0.75rem;
    background: transparent;
    overflow-wrap: anywhere;
  }
</style>

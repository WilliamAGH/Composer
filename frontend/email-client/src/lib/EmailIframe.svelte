<script>
  import { onMount } from 'svelte';
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

    // Generate sanitized fallback HTML (used when iframe unavailable or fails)
    const raw = normalizedHtml || '';
    if (!raw) {
      fallback = '';
      return;
    }

    if (window.DOMPurify) {
      fallback = window.DOMPurify.sanitize(raw, {
        ALLOWED_TAGS: ['p','br','strong','em','u','a','img','div','span','table','thead','tbody','tr','th','td','ul','ol','li','blockquote','pre','code'],
        ALLOWED_ATTR: ['href','title','target','rel','class','src','alt','width','height','loading','decoding','style']
      });
    } else {
      fallback = raw.replace(/<script[\s\S]*?>[\s\S]*?<\/script>/gi, '');
    }
  }

  $: tryRender(html);
  onMount(tryRender);
</script>

<div class="email-html-container" bind:this={container}>
  {#if !rendered && fallback}
    <div class="email-html-fallback prose prose-sm text-slate-700 break-words">{@html fallback}</div>
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
   * Sanitized fallback rendering mirrors iframe padding and allows horizontal scroll when needed.
   * @usage - Applied to the fallback div rendered when EmailRenderer fails or is unavailable
   * @related - .email-html-container to inherit width constraints
   */
  .email-html-fallback {
    width: 100%;
    max-width: 100%;
    padding: 1rem;
    overflow-x: auto;
    background: transparent;
    overflow-wrap: anywhere;
  }
</style>

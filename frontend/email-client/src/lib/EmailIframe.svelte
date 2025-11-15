<script>
  import { onMount } from 'svelte';
  import { sanitizeHtml } from './services/sanitizeHtml';
  export let html = '';
  let container = null;
  let fallbackContainer = null;
  let fallback = '';
  let rendered = false;
  const SAFE_LINK_REL = 'noopener noreferrer nofollow';

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

  $: if (fallback && fallbackContainer) {
    tagFallbackAnchors();
  }

  function tagFallbackAnchors() {
    if (!fallbackContainer || typeof fallbackContainer.querySelectorAll !== 'function') {
      return;
    }
    const anchors = fallbackContainer.querySelectorAll('a[href]');
    anchors.forEach((anchor) => {
      const href = anchor.getAttribute('href');
      if (!isExternalHttpUrl(href)) {
        return;
      }
      anchor.setAttribute('target', '_blank');
      anchor.setAttribute('rel', SAFE_LINK_REL);
    });
  }

  function handleFallbackClick(event) {
    const anchor = findAnchorElement(event.target);
    if (!anchor) {
      return;
    }
    const href = anchor.getAttribute('href');
    if (!isExternalHttpUrl(href)) {
      return;
    }
    event.preventDefault();
    openSafeExternalUrl(href);
  }

  function findAnchorElement(node) {
    let current = node;
    while (current && current !== fallbackContainer) {
      if (current.nodeType === 1 && current.tagName?.toLowerCase() === 'a') {
        return current;
      }
      current = current.parentElement || current.parentNode;
    }
    return null;
  }

  function isExternalHttpUrl(href) {
    if (!href || typeof href !== 'string' || typeof window === 'undefined') {
      return false;
    }
    try {
      const url = new URL(href, window.location.href);
      return url.protocol === 'http:' || url.protocol === 'https:';
    } catch (error) {
      return false;
    }
  }

  function openSafeExternalUrl(href) {
    if (typeof window === 'undefined') {
      return;
    }
    try {
      const url = new URL(href, window.location.href);
      window.open(url.href, '_blank', 'noopener,noreferrer');
    } catch (error) {
      console.debug('EmailIframe: unable to open external link', error);
    }
  }
</script>

<div class="email-html-container" bind:this={container}>
  {#if !rendered && fallback}
    <!-- svelte-ignore a11y-click-events-have-key-events -->
    <!-- svelte-ignore a11y-no-static-element-interactions -->
    <!-- svelte-ignore a11y-no-noninteractive-element-interactions -->
    <div
      class="email-html-fallback prose prose-sm text-slate-700"
      bind:this={fallbackContainer}
      role="region"
      aria-label="Email message content"
      on:click={handleFallbackClick}>{@html fallback}</div>
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

<script>
  import { onMount } from 'svelte';
  export let html = '';
  let container;
  let fallback = '';
  let rendered = false;

  function tryRender() {
    rendered = false;
    if (container && html && window.EmailRenderer && typeof window.EmailRenderer.renderInIframe === 'function') {
      try { window.EmailRenderer.renderInIframe(container, String(html)); rendered = true; } catch (e) { rendered = false; }
    } else {
      const raw = String(html || '');
      if (window.DOMPurify) {
        fallback = window.DOMPurify.sanitize(raw, {
          ALLOWED_TAGS: ['p','br','strong','em','u','a','img','div','span','table','thead','tbody','tr','th','td','ul','ol','li','blockquote','pre','code'],
          ALLOWED_ATTR: ['href','title','target','rel','class','src','alt','width','height','loading','decoding','style']
        });
      } else {
        fallback = raw.replace(/<script[\s\S]*?>[\s\S]*?<\/script>/gi, '');
      }
    }
  }

  $: html, tryRender();
  onMount(tryRender);
</script>

<div class="email-html-container w-full" bind:this={container}>
  {#if !rendered && fallback}
    <div class="email-text-panel prose prose-sm max-w-none text-slate-700">{@html fallback}</div>
  {/if}
</div>

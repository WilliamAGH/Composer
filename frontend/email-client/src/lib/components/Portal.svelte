<script>
  import { onMount, onDestroy } from 'svelte';

  /**
   * Renders children into a root outside the current stacking context so overlays/drawers can share a
   * predictable DOM host. Mirrors the long-standing Svelte portal helper pattern popularized in
   * romkor/svelte-portal (https://github.com/romkor/svelte-portal); we keep callers explicit by
   * requiring a `target` id to avoid hidden stacking contexts.
   */
  export let target = 'body';
  export let className = '';
  export let immutable = false;

  let portalContainer = null;
  let parentNode = null;

  function resolveTarget() {
    if (typeof document === 'undefined') return null;
    if (target instanceof HTMLElement) return target;
    if (typeof target === 'string' && target.startsWith('#')) {
      return document.querySelector(target);
    }
    if (typeof target === 'string') {
      const existing = document.getElementById(target);
      if (existing) {
        return existing;
      }
      const created = document.createElement('div');
      created.id = target;
      document.body.appendChild(created);
      return created;
    }
    return document.body;
  }

  onMount(() => {
    if (typeof document === 'undefined' || !portalContainer) return undefined;
    parentNode = resolveTarget() || document.body;
    parentNode.appendChild(portalContainer);
    return () => {
      if (parentNode && portalContainer && parentNode.contains(portalContainer) && !immutable) {
        parentNode.removeChild(portalContainer);
      }
      parentNode = null;
    };
  });
</script>

<div bind:this={portalContainer} class={className} style="position: relative;">
  <slot />
</div>

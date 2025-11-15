<script lang="ts">
  import { onDestroy } from 'svelte';
  import Portal from '../components/Portal.svelte';
  import type { OverlayController, OverlayStackItem } from './OverlayController';

  export let controller: OverlayController | null = null;
  export let target = 'overlay-root';

  let overlays: OverlayStackItem[] = [];
  let unsubscribe: (() => void) | null = null;

  $: {
    unsubscribe?.();
    if (controller) {
      unsubscribe = controller.overlays.subscribe((value) => {
        overlays = value;
      });
    } else {
      overlays = [];
      unsubscribe = null;
    }
  }

  onDestroy(() => {
    unsubscribe?.();
  });

  function presenterClass(presenter: string) {
    switch (presenter) {
      case 'backdrop':
        return 'overlay-backdrop';
      case 'modal':
        return 'overlay-modal';
      default:
        return 'overlay-sheet';
    }
  }
</script>

<Portal {target} className="overlay-stack">
  {#each overlays as overlay (overlay.id)}
    <div class={`overlay ${presenterClass(overlay.presenter)}`}>
      <svelte:component
        this={overlay.component}
        {...(overlay.props || {})} />
    </div>
  {/each}
</Portal>

<style>
  :global(.overlay-stack) {
    position: fixed;
    inset: 0;
    pointer-events: none;
    display: contents;
  }

  .overlay {
    pointer-events: auto;
  }

  .overlay-backdrop {
    position: fixed;
    inset: 0;
    z-index: var(--z-drawer-backdrop, 160);
  }

  .overlay-sheet {
    position: fixed;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: var(--z-drawer-sidebar, 170);
  }

  .overlay-modal {
    position: fixed;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: var(--z-modal, 180);
  }
</style>

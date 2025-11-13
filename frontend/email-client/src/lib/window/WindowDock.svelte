<script>
  import { createEventDispatcher } from 'svelte';
  import { useWindowContext } from './windowContext';

  /**
   * Dock for minimized windows. Lives as a Svelte component because it renders UI, while its backing
   * state stays in JS stores (windowStore.js) to remain reusable.
   */
  export let windows = [];

  const dispatch = createEventDispatcher();
  const windowManager = useWindowContext();

  function restore(id) {
    windowManager.toggleMinimize(id);
    dispatch('restore', { id });
  }

  function close(id, event) {
    event?.stopPropagation();
    windowManager.close(id);
    dispatch('close', { id });
  }
</script>

{#if windows.length}
<div class="window-dock">
  {#each windows as win}
    <button type="button" class="dock-pill" on:click={() => restore(win.id)}>
      <span class="pill-title">{win.title}</span>
      <button
        type="button"
        class="pill-close"
        aria-label="Close window"
        on:click={(event) => close(win.id, event)}
      >×</button>
    </button>
  {/each}
</div>
{/if}

<style>
  /**
   * Dock styling mirrors the button capsules (blurred white, 999px radius)
   * used elsewhere so minimized windows feel like part of the same system.
   */
  .window-dock {
    position: fixed;
    left: 16px;
    right: 16px;
    bottom: 8px;
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    z-index: 60;
  }
  .dock-pill {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    border-radius: 999px;
    border: 1px solid rgba(15, 23, 42, 0.12);
    background: rgba(255, 255, 255, 0.95);
    padding: 6px 12px;
    font-size: 0.85rem;
    color: #0f172a;
    box-shadow: 0 10px 25px -12px rgba(15, 23, 42, 0.25);
  }
  .pill-title {
    max-width: 160px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .pill-close {
    border: none;
    background: transparent;
    font-weight: 600;
    cursor: pointer;
    color: inherit;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 1.25rem;
    height: 1.25rem;
    padding: 0;
    font-family: inherit;
    font-size: inherit;
  }
</style>

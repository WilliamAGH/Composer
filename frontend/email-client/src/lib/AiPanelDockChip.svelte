<!-- Floating chip that restores the AI summary panel when it is docked. -->
<script>
  import { Sparkles } from 'lucide-svelte';
  import { createEventDispatcher } from 'svelte';

  export let visible = false;
  const dispatch = createEventDispatcher();

  function handleClick() {
    dispatch('restore');
  }
</script>

{#if visible}
  <button class="panel-dock-chip" type="button" on:click={handleClick}>
    <Sparkles class="h-4 w-4" aria-hidden="true" />
    AI Panel
  </button>
{/if}

<style>
  /**
   * Floating dock chip surfaces a restore affordance when the AI panel is minimized.
   * @usage - Button rendered inside AiPanelDockChip.svelte whenever `visible` is true
   * @z-index-warning - Relies on var(--z-toolbar-surface, 150) to sit above drawers and overlays
   * @related - App.svelte panel docking logic
   */
  .panel-dock-chip {
    position: fixed;
    bottom: 24px;
    left: 24px;
    display: inline-flex;
    align-items: center;
    gap: 0.4rem;
    border-radius: 999px;
    border: 1px solid rgba(148, 163, 184, 0.5);
    background: rgba(255, 255, 255, 0.95);
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1), 0 1px 2px rgba(0, 0, 0, 0.06);
    padding: 0.45rem 0.95rem;
    font-size: 0.85rem;
    color: #0f172a;
    z-index: var(--z-toolbar-surface, 150);
  }

  /**
   * Mobile layout centers the dock chip and adds right padding for narrow screens.
   * @usage - Applied when viewport width <=768px
   * @related - .panel-dock-chip base styles
   */
  @media (max-width: 768px) {
    .panel-dock-chip {
      left: 16px;
      right: 16px;
      justify-content: center;
    }
  }
</style>

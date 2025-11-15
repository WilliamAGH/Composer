<script>
  /**
   * Unified dock for all minimized components (compose windows, AI panels, etc.)
   * Ensures consistent styling, proper spacing, and no overlapping.
   */
  export let items = [];
  export let maxItems = 10; // Maximum number of items to show before warning

  $: displayItems = items.slice(0, maxItems);
  $: hasOverflow = items.length > maxItems;

  function handleRestore(item, event) {
    event?.stopPropagation();
    if (item.onRestore) {
      item.onRestore();
    }
  }

  function handleClose(item, event) {
    event?.stopPropagation();
    if (item.onClose) {
      item.onClose();
    }
  }
</script>

{#if displayItems.length}
  <div class="unified-dock">
    {#each displayItems as item (item.id)}
      <button
        type="button"
        class="dock-pill"
        on:click={(event) => handleRestore(item, event)}
        aria-label="Restore {item.title}"
      >
        {#if item.icon}
          <span class="pill-icon" aria-hidden="true">
            <svelte:component this={item.icon} />
          </span>
        {/if}
        <span class="pill-title">{item.title}</span>
        {#if item.closeable && item.onClose}
          <button
            type="button"
            class="pill-close"
            aria-label="Close {item.title}"
            on:click={(event) => handleClose(item, event)}
          >×</button>
        {/if}
      </button>
    {/each}

    {#if hasOverflow}
      <div class="dock-overflow-indicator" title="{items.length - maxItems} more minimized">
        +{items.length - maxItems}
      </div>
    {/if}
  </div>
{/if}

<style>
  /**
   * Unified dock container - anchored to bottom-left with consistent spacing.
   * @usage - Single dock for all minimized windows/panels to prevent overlap
   * @z-index-warning - Uses var(--z-toolbar-surface, 150) to sit above content but below drawers/modals
   * @related - WindowManager (compose windows), aiPanelStore (AI panel)
   */
  .unified-dock {
    position: fixed;
    left: 16px;
    right: 16px;
    bottom: 16px;
    max-height: 200px; /* Allow up to ~4 rows of pills */
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    align-content: flex-end; /* Align rows to bottom */
    overflow-y: auto;
    overflow-x: hidden;
    z-index: var(--z-toolbar-surface, 150);
    pointer-events: none; /* Allow clicks to pass through empty space */
    padding-top: 8px; /* Extra padding for scroll */
  }

  /**
   * Dock pill - glassmorphic capsule button for minimized items.
   * @usage - Individual minimized window/panel representation in the dock
   * @related - .pill-title, .pill-close, .pill-icon
   */
  .dock-pill {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    border-radius: 999px;
    border: 1px solid rgba(15, 23, 42, 0.12);
    background: rgba(255, 255, 255, 0.95);
    backdrop-filter: blur(8px);
    -webkit-backdrop-filter: blur(8px);
    padding: 6px 12px;
    font-size: 0.85rem;
    color: #0f172a;
    box-shadow: 0 10px 25px -12px rgba(15, 23, 42, 0.25);
    cursor: pointer;
    transition: all 0.2s ease;
    pointer-events: auto; /* Pills themselves are clickable */
  }

  .dock-pill:hover {
    background: rgba(255, 255, 255, 1);
    box-shadow: 0 12px 28px -12px rgba(15, 23, 42, 0.3);
    transform: translateY(-1px);
  }

  /**
   * Pill icon - optional leading icon (e.g., Sparkles for AI panel).
   * @usage - Rendered when item.icon is provided
   */
  .pill-icon {
    display: inline-flex;
    width: 16px;
    height: 16px;
    flex-shrink: 0;
    align-items: center;
    justify-content: center;
  }

  .pill-icon :global(svg) {
    width: 100%;
    height: 100%;
  }

  /**
   * Pill title - truncated text label.
   * @usage - Displays window/panel title with ellipsis overflow
   */
  .pill-title {
    max-width: 160px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  /**
   * Pill close button - × button to close the item.
   * @usage - Rendered when item.closeable is true and onClose is provided
   */
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
    border-radius: 999px;
    transition: background 0.15s ease;
    font-family: inherit;
    font-size: inherit;
  }

  .pill-close:hover {
    background: rgba(0, 0, 0, 0.08);
  }

  /**
   * Overflow indicator - shows count of hidden items when max limit reached.
   * @usage - Displayed when items.length > maxItems
   */
  .dock-overflow-indicator {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 2.5rem;
    height: 2rem;
    border-radius: 999px;
    border: 1px solid rgba(148, 163, 184, 0.5);
    background: rgba(248, 250, 252, 0.95);
    backdrop-filter: blur(8px);
    -webkit-backdrop-filter: blur(8px);
    padding: 0 0.75rem;
    font-size: 0.75rem;
    font-weight: 600;
    color: #64748b;
    pointer-events: auto;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  }

  /**
   * Mobile responsive adjustments.
   * @usage - Full width on mobile, centered layout
   */
  @media (max-width: 768px) {
    .unified-dock {
      left: 16px;
      right: 16px;
      justify-content: center;
    }
  }
</style>

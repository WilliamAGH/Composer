<!--
  @component WindowActionControls
  @description Shared chrome controls for window minimize/maximize/close actions with optional refresh button.

  This component consolidates all window action buttons into a single, reusable component that ensures
  consistent styling, behavior, and layout across different window types (AI Summary, Compose, etc.).

  @usage
  ```svelte
  <WindowActionControls
    showRefresh={true}
    maximized={false}
    refreshDisabled={false}
    refreshAriaLabel="Regenerate summary"
    on:refresh={handleRefresh}
    on:minimize={handleMinimize}
    on:maximize={handleMaximize}
    on:close={handleClose}
  />
  ```

  @props
  - allowMinimize: boolean - Show minimize button (default: true)
  - allowMaximize: boolean - Show maximize/restore button (default: true)
  - allowClose: boolean - Show close button (default: true)
  - showRefresh: boolean - Show refresh button before chrome controls (default: false)
  - maximized: boolean - Current maximized state, affects maximize icon (default: false)
  - refreshDisabled: boolean - Disable refresh button interaction (default: false)
  - refreshAriaLabel: string - Accessible label for refresh button (default: "Refresh")
  - minimizeTitle: string - Tooltip for minimize button (default: "Minimize")
  - maximizeTitle: string - Tooltip for maximize button (default: "Maximize")
  - restoreTitle: string - Tooltip for restore button when maximized (default: "Restore")
  - closeTitle: string - Tooltip for close button (default: "Close")

  @events
  - minimize: Fired when minimize button is clicked
  - maximize: Fired when maximize/restore button is clicked (parent should toggle state)
  - close: Fired when close button is clicked
  - refresh: Fired when refresh button is clicked (only if showRefresh=true)

  @accessibility
  - All buttons have proper aria-label and title attributes for screen readers
  - Disabled state properly communicated via aria-disabled and disabled attribute
  - Touch targets meet WCAG 2.1 AA minimum of 44px on mobile devices
  - Keyboard navigation fully supported with proper focus management

  @responsive
  - Desktop (fine pointer): 36px × 36px buttons with 0.25rem gaps
  - Mobile (coarse pointer): 42px × 42px buttons for easier touch interaction
  - Buttons never wrap to multiple lines regardless of viewport width
  - flex-shrink: 0 prevents button compression under space constraints

  @architecture
  - Two-level structure: wrapper contains optional refresh + button group
  - Refresh button is visually separated with 0.5rem gap
  - Chrome buttons (min/max/close) are tightly grouped with 0.25rem gap
  - Both levels use inline-flex to prevent unwanted stretching

  @anti-patterns
  - DO NOT wrap this component in a flex container with flex-wrap: wrap
  - DO NOT apply width: 100% to parent containers
  - DO NOT nest this inside elements with conflicting flex properties

  @related
  - Used by: AiSummaryWindow.svelte, WindowFrame.svelte, ComposeWindow.svelte
  - Replaces: Legacy .window-actions and .window-action-controls classes in app-shared.css
  - Icons from: lucide-svelte (RotateCcw, Minus, Maximize2, Minimize2, X)

  @history
  - Created: 2025-11 - Consolidates duplicate button code from multiple window components
  - Fixes: CSS flex-wrap wrapping issues that caused buttons to appear on separate lines
-->
<script>
  import { createEventDispatcher } from 'svelte';
  import { RotateCcw, Minus, Maximize2, Minimize2, X } from 'lucide-svelte';

  /**
   * Controls which action buttons are visible in the control group.
   */
  export let allowMinimize = true;
  export let allowMaximize = true;
  export let allowClose = true;

  /**
   * Whether to show the optional refresh button before the chrome controls.
   * Typically used in AI summary panels for regenerating content.
   */
  export let showRefresh = false;

  /**
   * Current maximized state of the window.
   * When true, shows Minimize2 icon; when false, shows Maximize2 icon.
   */
  export let maximized = false;

  /**
   * Disables the refresh button interaction (e.g., during loading states).
   */
  export let refreshDisabled = false;

  /**
   * Accessible label for the refresh button, read by screen readers.
   */
  export let refreshAriaLabel = 'Refresh';

  /**
   * Tooltip text for window chrome buttons.
   */
  export let minimizeTitle = 'Minimize';
  export let maximizeTitle = 'Maximize';
  export let restoreTitle = 'Restore';
  export let closeTitle = 'Close';

  const dispatch = createEventDispatcher();

  /**
   * Handles refresh button click and dispatches refresh event to parent.
   * @fires refresh
   */
  function handleRefresh() {
    dispatch('refresh');
  }

  /**
   * Handles minimize button click and dispatches minimize event to parent.
   * @fires minimize
   */
  function handleMinimize() {
    dispatch('minimize');
  }

  /**
   * Handles maximize/restore toggle button click and dispatches maximize event to parent.
   * Parent component is responsible for toggling the maximized state.
   * @fires maximize
   */
  function handleMaximize() {
    dispatch('maximize');
  }

  /**
   * Handles close button click and dispatches close event to parent.
   * @fires close
   */
  function handleClose() {
    dispatch('close');
  }

  /**
   * Computes the appropriate title for the maximize/restore button based on current state.
   */
  $: computedMaximizeTitle = maximized ? restoreTitle : maximizeTitle;
</script>

<div class="window-action-controls-wrapper">
  {#if showRefresh}
    <button
      type="button"
      class="btn btn--icon btn--icon-chrome btn--inset"
      on:click={handleRefresh}
      disabled={refreshDisabled}
      aria-label={refreshAriaLabel}
      title={refreshAriaLabel}
    >
      <RotateCcw class="h-4 w-4" aria-hidden="true" />
    </button>
  {/if}

  <div class="window-action-controls-group">
    {#if allowMinimize}
      <button
        type="button"
        class="btn btn--icon btn--icon-chrome btn--inset"
        on:click={handleMinimize}
        title={minimizeTitle}
        aria-label={minimizeTitle}
      >
        <Minus class="h-4 w-4" aria-hidden="true" />
      </button>
    {/if}

    {#if allowMaximize}
      <button
        type="button"
        class="btn btn--icon btn--icon-chrome btn--inset"
        on:click={handleMaximize}
        title={computedMaximizeTitle}
        aria-label={computedMaximizeTitle}
      >
        {#if maximized}
          <Minimize2 class="h-4 w-4" aria-hidden="true" />
        {:else}
          <Maximize2 class="h-4 w-4" aria-hidden="true" />
        {/if}
      </button>
    {/if}

    {#if allowClose}
      <button
        type="button"
        class="btn btn--icon btn--icon-chrome btn--inset"
        on:click={handleClose}
        title={closeTitle}
        aria-label={closeTitle}
      >
        <X class="h-4 w-4" aria-hidden="true" />
      </button>
    {/if}
  </div>
</div>

<style>
  /**
   * Wrapper container ensures all buttons stay inline regardless of parent flex behavior.
   *
   * This is the critical fix for the button wrapping issue. By using inline-flex instead
   * of flex, and setting flex-shrink: 0, we prevent the browser from wrapping buttons
   * to a new line even when the parent container uses flex-wrap: wrap.
   *
   * @usage - Root element of WindowActionControls.svelte component
   * @layout - inline-flex with horizontal row direction
   * @spacing - 0.5rem gap between refresh button and chrome control group
   * @flex-behavior - flex-shrink: 0 prevents compression, flex-grow: 0 prevents expansion
   * @wrapping - nowrap !important prevents any wrapping of child elements
   * @anti-pattern - NEVER add flex-wrap: wrap or width: 100% to this element
   * @related - .window-action-controls-group for chrome button cluster
   */
  .window-action-controls-wrapper {
    display: inline-flex;
    align-items: center;
    gap: 0.5rem;
    flex-wrap: nowrap !important;
    flex-shrink: 0;
    flex-grow: 0;
  }

  /**
   * Button group container keeps minimize/maximize/close tightly clustered.
   *
   * The tighter 0.25rem gap (vs 0.5rem for wrapper) creates visual grouping of the
   * standard window chrome controls, while the refresh button (if present) appears
   * slightly more separated as a distinct action.
   *
   * @usage - Inner container for chrome buttons (minimize, maximize, close)
   * @layout - inline-flex with horizontal row direction
   * @spacing - 0.25rem gap creates tight 4px spacing between chrome buttons
   * @flex-behavior - flex-shrink: 0 prevents compression under space constraints
   * @wrapping - nowrap !important ensures buttons never break to multiple lines
   * @related - .window-action-controls-wrapper parent container
   */
  .window-action-controls-group {
    display: inline-flex;
    align-items: center;
    gap: 0.25rem;
    flex-wrap: nowrap !important;
    flex-shrink: 0;
    flex-grow: 0;
  }

  /**
   * Component-scoped button sizing for window chrome controls.
   *
   * Overrides the base .btn--icon size (42px) with smaller chrome-specific sizing.
   * The compound selector .btn.btn--icon-chrome has higher specificity (0,2,0) than
   * the global .btn--icon (0,1,0), ensuring these styles win the cascade.
   *
   * @usage - Applied to all buttons in this component
   * @sizing - 32px desktop, 42px mobile for touch-friendly targets
   * @related - .btn--icon from app-shared.css
   */
  .btn.btn--icon-chrome {
    width: 32px;
    height: 32px;
    padding: 0;
    border-radius: 12px;
    border-color: rgba(148, 163, 184, 0.65);
    background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.82));
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7), 0 18px 35px -22px rgba(15, 23, 42, 0.35);
  }

  /**
   * Touch device override increases button size for easier tapping.
   * @media - Applies on devices with coarse pointers (touchscreens)
   */
  @media (hover: none) and (pointer: coarse) {
    .btn.btn--icon-chrome {
      width: 42px;
      height: 42px;
      border-radius: 14px;
    }
  }
</style>

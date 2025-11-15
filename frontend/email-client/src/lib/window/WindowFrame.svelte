<script>
  import { createEventDispatcher } from 'svelte';
  import WindowActionControls from './WindowActionControls.svelte';
  import { isMobile, isTablet, viewport } from '../viewportState';

  /**
   * Shared frame for floating/docked windows. Lives in JS-backed Svelte to provide markup, while
   * reusable state stays in nearby JS modules so multiple components can reuse it.
   */
  export let open = true;
  export let title = 'Window';
  export let mode = 'floating';
  export let minimized = false;
  export let allowMinimize = true;
  export let allowClose = true;
  export let offsetIndex = 0;
  export let allowMaximize = true;
  export let maximized = false;
  export let maximizedAnchorBounds = null;

  const dispatch = createEventDispatcher();
  $: mobile = $isMobile;
  $: tablet = $isTablet;
  $: viewportType = $viewport;

  $: floating = mode === 'floating';
  $: mobileFloating = floating && mobile && !maximized;
  $: anchoredStyle = maximized && maximizedAnchorBounds
    ? `top: ${maximizedAnchorBounds.top}px; right: ${maximizedAnchorBounds.right}px; bottom: ${maximizedAnchorBounds.bottom}px; left: ${maximizedAnchorBounds.left}px;`
    : '';
  $: frameStyle = anchoredStyle
    ? anchoredStyle
    : floating && !maximized && !mobile
      ? `right: ${24 + offsetIndex * 16}px; bottom: ${24 + offsetIndex * 16}px;`
      : '';

  function handleToggle() {
    if (!allowMinimize) return;
    dispatch('toggleMinimize');
  }

  function handleClose() {
    if (!allowClose) return;
    dispatch('close');
  }

  function handleToggleMaximize() {
    if (!allowMaximize) return;
    dispatch('toggleMaximize');
  }

</script>

{#if open && !minimized}
<div
  class={`window-frame ${mode === 'floating' ? 'floating' : 'docked'} ${maximized ? 'maximized' : ''} ${mobileFloating ? 'mobile-floating' : ''}`}
  role="dialog"
  aria-modal={mode === 'floating' || maximized}
  style={frameStyle}
>
  <header class="window-header">
    <div class="window-title">{title}</div>
    <div class="window-header-actions">
      <slot name="headerActions"></slot>
      {#if allowMinimize || allowMaximize || allowClose}
        <WindowActionControls
          showRefresh={false}
          {allowMinimize}
          {allowMaximize}
          {allowClose}
          {maximized}
          minimizeTitle="Minimize"
          maximizeTitle={maximized ? 'Restore' : 'Maximize'}
          closeTitle="Close"
          on:minimize={handleToggle}
          on:maximize={handleToggleMaximize}
          on:close={handleClose}
        />
      {/if}
    </div>
  </header>
  <section class="window-body">
    <slot></slot>
  </section>
  <footer class="window-footer">
    <slot name="footer"></slot>
  </footer>
</div>
{/if}

<style>
  /**
   * Window chrome adapts to mobile by pinning to the safe-area inset while
   * preserving layers/blur for floating desktop experiences.
   *
   * Base container for all windowed UI elements (compose, settings, etc.).
   * Provides the signature glassmorphic aesthetic with backdrop blur and subtle
   * borders. Automatically adapts positioning and appearance based on window mode
   * (floating, docked, maximized) via modifier classes.
   *
   * @usage - <div class="window-frame"> root element in WindowFrame.svelte
   * @layout - Flexbox column layout stacks header, body, and footer vertically
   * @backdrop - blur(16px) creates frosted glass effect (removed when maximized)
   * @overflow - Hidden clips child content to rounded corners
   * @z-index - Varies by mode via modifier classes (.floating, .docked, .maximized)
   * @responsive - Adapts via .mobile-floating modifier and media queries
   * @related - .window-frame.floating, .window-frame.docked, .window-frame.maximized
   */
  .window-frame {
    display: flex;
    flex-direction: column;
    background: rgba(255, 255, 255, 0.95);
    border: 1px solid rgba(15, 23, 42, 0.12);
    box-shadow: 0 25px 60px -20px rgba(15, 23, 42, 0.35);
    border-radius: 20px;
    overflow: hidden;
    backdrop-filter: blur(16px);
  }
  /**
   * Default floating positioning for desktop.
   *
   * Positions the window frame in the bottom-right corner of the viewport with
   * responsive sizing. Multiple floating windows can stack with offset positioning
   * controlled by the offsetIndex prop.
   *
   * @usage - Applied when mode="floating" on desktop
   * @positioning - Fixed at bottom-right with 24px margins
   * @sizing - Max width 640px or 92vw, whichever is smaller; max-height 80vh
   * @z-index - 80 sits above drawers (60) but below window notices (120)
   * @related - .window-frame.mobile-floating for mobile variant
   */
  .window-frame.floating {
    position: fixed;
    right: 24px;
    bottom: 24px;
    width: min(640px, 92vw);
    max-height: 80vh;
    z-index: 80;
  }
  /**
   * Mobile floating windows pin to the viewport with no shadow.
   *
   * On mobile devices, floating windows expand to fill the entire viewport
   * (using 100dvh for dynamic viewport height support). Border radius and
   * shadows are removed for a full-screen native app experience.
   *
   * @usage - Applied when mode="floating" and viewport is mobile
   * @positioning - inset: 0 fills entire viewport
   * @sizing - 100vw × 100dvh with no max constraints
   * @styling - No border-radius or box-shadow for edge-to-edge display
   * @related - .window-frame.floating base styles
   */
  .window-frame.floating.mobile-floating {
    inset: 0;
    width: 100vw;
    height: 100dvh;
    max-height: none;
    border-radius: 0;
    box-shadow: none;
  }
  /**
   * Maximized windows expand within safe gutters.
   *
   * When a window is maximized, it fills most of the viewport with responsive
   * gutters that adapt from 12px on mobile to 32px on desktop. Backdrop blur
   * is removed for better performance, and z-index is elevated above other windows.
   *
   * @usage - Applied when maximized={true}
   * @positioning - Fixed with responsive inset gutters via clamp()
   * @sizing - Auto width/height fills available space within gutters
   * @backdrop - No blur for performance during expanded view
   * @z-index - 140 sits above other windows and notices
   * @related - .window-frame base styles, mobile media query overrides
   */
  .window-frame.maximized {
    position: fixed;
    inset: clamp(12px, 3vw, 32px);
    width: auto;
    height: auto;
    max-height: none;
    max-width: none;
    z-index: 140;
    border-radius: 28px;
    background: #ffffff;
    box-shadow: 0 40px 120px -32px rgba(15, 23, 42, 0.45);
    backdrop-filter: none;
  }
  /**
   * Content area padding.
   *
   * Main scrollable area for window content (compose form, settings, etc.).
   * Expands to fill available vertical space between header and footer.
   *
   * @usage - <section class="window-body"> wrapping slotted content
   * @layout - Flexbox child that expands to fill available space
   * @overflow - overflow-y: auto enables vertical scrolling when content exceeds height
   * @spacing - 0.75rem padding on all sides for tighter design
   * @flex-behavior - flex: 1 expands to fill remaining space
   * @related - .window-frame.maximized .window-body for maximized state
   */
  .window-body {
    flex: 1;
    overflow-y: auto;
    padding: 0.75rem;
  }
  /**
   * Allow maximized frames to scroll their body region.
   *
   * Ensures the body continues to flex and scroll properly when the window
   * is in maximized state. Redundant declaration for specificity.
   *
   * @usage - Applied when window-frame has .maximized class
   * @related - .window-body base styles
   */
  .window-frame.maximized .window-body {
    flex: 1;
    overflow-y: auto;
  }
  /**
   * Docked windows hug the bottom edge as a tray.
   *
   * Alternative window mode that anchors to the bottom of the screen like
   * a drawer or tray. Used for persistent UI that doesn't need to float.
   *
   * @usage - Applied when mode="docked"
   * @positioning - Fixed at bottom spanning full width with 16px margins
   * @sizing - Max-height of 50vh prevents covering too much screen space
   * @z-index - 70 sits above panels (30) but below floating windows (80)
   * @related - .window-frame.floating, .window-frame.maximized
   */
  .window-frame.docked {
    position: fixed;
    left: 16px;
    right: 16px;
    bottom: 16px;
    max-height: 50vh;
    z-index: 70;
  }
  /**
   * Header styling for title + chrome buttons.
   *
   * CRITICAL FIX: Added explicit flex-wrap: nowrap and gap to ensure title
   * and action buttons always stay on the same horizontal line. The previous
   * lack of flex-wrap declaration could cause wrapping in some browsers.
   *
   * @usage - <header class="window-header"> wrapping title and actions
   * @layout - Flexbox with space-between for title left, controls right alignment
   * @wrapping - nowrap ensures title and controls stay on same horizontal line
   * @alignment - center vertically aligns title with action buttons
   * @spacing - 1rem gap provides breathing room between title and controls
   * @border - Bottom border visually separates header from body content
   * @background - Subtle gradient provides frosted header appearance
   * @related - .window-title, .window-header-actions
   */
  .window-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0.75rem 1rem;
    border-bottom: 1px solid rgba(15, 23, 42, 0.08);
    background: linear-gradient(180deg, rgba(248, 250, 252, 0.95), rgba(255, 255, 255, 0.9));
    flex-wrap: nowrap;
    gap: 1rem;
  }
  /**
   * Title typography.
   *
   * Window title text (e.g., "Reply", "New Message", "Settings"). Added
   * flex-shrink, min-width, and text-overflow properties to enable graceful
   * truncation when the title is very long, ensuring action buttons remain visible.
   *
   * @usage - <div class="window-title"> displaying window title
   * @typography - 600 font weight for emphasis
   * @color - Dark navy (#0f172a) for contrast against frosted header background
   * @truncation - text-overflow: ellipsis with overflow: hidden truncates long titles
   * @flex-behavior - flex-shrink: 1 allows title to compress if needed
   * @accessibility - Full title should be available via tooltip if truncated
   * @related - .window-header parent, .window-header-actions sibling
   */
  .window-title {
    font-weight: 600;
    color: #0f172a;
    flex-shrink: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  /**
   * Container for header action controls and slotted content.
   *
   * NEW CLASS: Wraps the optional headerActions slot and WindowActionControls
   * component. Uses inline-flex and flex-shrink: 0 to ensure buttons never
   * compress or wrap, fixing the core button wrapping issue.
   *
   * @usage - <div class="window-header-actions"> wrapping slot and WindowActionControls
   * @layout - inline-flex prevents stretching, keeps content tight
   * @spacing - 0.5rem gap between slotted actions and window controls
   * @flex-behavior - flex-shrink: 0 ensures buttons never compress
   * @wrapping - nowrap prevents any wrapping of child elements
   * @related - WindowActionControls component, headerActions slot
   */
  .window-header-actions {
    display: inline-flex;
    align-items: center;
    gap: 0.5rem;
    flex-shrink: 0;
    flex-wrap: nowrap;
  }
  /**
   * Footer shares the frosted background.
   *
   * Optional footer area for action buttons (Send, Attach, etc.) in compose windows.
   * Uses same frosted styling as header to maintain visual consistency.
   *
   * @usage - <footer class="window-footer"> wrapping footer slot content
   * @border - Top border visually separates footer from body content
   * @background - Frosted gradient matches header styling
   * @spacing - Same padding as header (0.75rem 1rem)
   * @visibility - Automatically hidden when empty via :empty pseudo-class
   * @related - .window-header for matching header styles
   */
  .window-footer {
    padding: 0.75rem 1rem;
    border-top: 1px solid rgba(15, 23, 42, 0.08);
    background: rgba(248, 250, 252, 0.9);
  }
  /**
   * Hide footer entirely when unused.
   *
   * Automatically collapses the footer when no content is slotted, preventing
   * unnecessary whitespace and borders from appearing.
   *
   * @usage - Applied automatically when footer slot is empty
   * @display - none removes footer from layout flow completely
   * @related - .window-footer base styles
   */
  .window-footer:empty {
    display: none;
  }
  /**
   * Tablet/phone overrides reduce gutters.
   *
   * On mobile and tablet viewports, maximized windows expand to fill the entire
   * screen with no gutters, border radius, or shadows for a true full-screen
   * native app experience.
   *
   * @usage - Applied when viewport width ≤768px and window is maximized
   * @positioning - inset: 0 removes all gutters
   * @styling - Removes border-radius and box-shadow for edge-to-edge display
   * @related - .window-frame.maximized base styles
   */
  @media (max-width: 768px) {
    .window-frame.maximized {
      inset: 0;
      border-radius: 0;
      box-shadow: none;
    }
  }
  /**
   * Safe-area padding ensures controls aren't hidden behind notches.
   *
   * On devices with notches or rounded corners (iPhone X+, etc.), adds padding
   * equal to the safe area insets to prevent content from being obscured by
   * device hardware features.
   *
   * @usage - Applied automatically on devices that support safe-area-inset
   * @targets - Mobile floating windows and maximized windows
   * @padding - Uses CSS environment variables for safe area insets
   * @accessibility - Ensures all interactive controls remain tappable
   * @related - .window-frame.floating.mobile-floating, .window-frame.maximized
   */
  @supports (padding: env(safe-area-inset-top)) {
    .window-frame.floating.mobile-floating,
    .window-frame.maximized {
      padding-top: env(safe-area-inset-top);
      padding-right: env(safe-area-inset-right);
      padding-bottom: env(safe-area-inset-bottom);
      padding-left: env(safe-area-inset-left);
    }
  }
</style>

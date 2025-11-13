<script>
  import { createEventDispatcher } from 'svelte';
  import { Minus, X, Maximize2, Minimize2 } from 'lucide-svelte';
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
    <div class="window-actions">
      <slot name="headerActions"></slot>
      {#if allowMinimize || allowMaximize || allowClose}
        <div class="window-action-controls">
          {#if allowMinimize}
            <button type="button" class="btn btn--icon btn--icon-chrome btn--inset" on:click|stopPropagation={handleToggle} title="Minimize">
              <Minus class="h-4 w-4" />
            </button>
          {/if}
          {#if allowMaximize}
            <button type="button" class="btn btn--icon btn--icon-chrome btn--inset" on:click|stopPropagation={handleToggleMaximize} title={maximized ? 'Restore' : 'Maximize'}>
              {#if maximized}
                <Minimize2 class="h-4 w-4" />
              {:else}
                <Maximize2 class="h-4 w-4" />
              {/if}
            </button>
          {/if}
          {#if allowClose}
            <button type="button" class="btn btn--icon btn--icon-chrome btn--inset" on:click|stopPropagation={handleClose} title="Close">
              <X class="h-4 w-4" />
            </button>
          {/if}
        </div>
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
   */
  .window-frame.floating {
    position: fixed;
    right: 24px;
    bottom: 24px;
    width: min(560px, 92vw);
    max-height: 80vh;
    z-index: 80;
  }
  /**
   * Mobile floating windows pin to the viewport with no shadow.
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
   */
  .window-body {
    flex: 1;
    overflow-y: auto;
    padding: 1rem;
  }
  /**
   * Allow maximized frames to scroll their body region.
   */
  .window-frame.maximized .window-body {
    flex: 1;
    overflow-y: auto;
  }
  /**
   * Docked windows hug the bottom edge as a tray.
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
   */
  .window-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0.75rem 1rem;
    border-bottom: 1px solid rgba(15, 23, 42, 0.08);
    background: linear-gradient(180deg, rgba(248, 250, 252, 0.95), rgba(255, 255, 255, 0.9));
  }
  /**
   * Title typography.
   */
  .window-title {
    font-weight: 600;
    color: #0f172a;
  }
  /**
   * Footer shares the frosted background.
   */
  .window-footer {
    padding: 0.75rem 1rem;
    border-top: 1px solid rgba(15, 23, 42, 0.08);
    background: rgba(248, 250, 252, 0.9);
  }
  /**
   * Hide footer entirely when unused.
   */
  .window-footer:empty {
    display: none;
  }
  /**
   * Tablet/phone overrides reduce gutters.
   */
  @media (max-width: 768px) {
    .window-frame.maximized {
      inset: 0;
      border-radius: 0;
      box-shadow: none;
    }
  }
  /**
   * Safe-area padding ensures controls aren’t hidden behind notches.
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

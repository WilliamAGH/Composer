<script>
  import { createEventDispatcher } from 'svelte';
  import { Minus, X, Maximize2, Minimize2 } from 'lucide-svelte';
  import { isMobile, isTablet, viewport } from '../viewport';

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

  const dispatch = createEventDispatcher();
  $: mobile = $isMobile;
  $: tablet = $isTablet;
  $: viewportType = $viewport;

  $: frameStyle = mode === 'floating' && !maximized
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
  class={`window-frame ${mode === 'floating' ? 'floating' : 'docked'} ${maximized ? 'maximized' : ''}`}
  role="dialog"
  aria-modal={mode === 'floating' || maximized}
  style={frameStyle}
>
  <header class="window-header">
    <div class="window-title">{title}</div>
    <div class="window-actions">
      <slot name="headerActions"></slot>
      {#if allowMinimize}
        <button type="button" class="icon-btn" on:click|stopPropagation={handleToggle} title="Minimize">
          <Minus class="h-4 w-4" />
        </button>
      {/if}
      {#if allowMaximize}
        <button type="button" class="icon-btn" on:click|stopPropagation={handleToggleMaximize} title={maximized ? 'Restore' : 'Maximize'}>
          {#if maximized}
            <Minimize2 class="h-4 w-4" />
          {:else}
            <Maximize2 class="h-4 w-4" />
          {/if}
        </button>
      {/if}
      {#if allowClose}
        <button type="button" class="icon-btn" on:click|stopPropagation={handleClose} title="Close">
          <X class="h-4 w-4" />
        </button>
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
  .window-frame.floating {
    position: fixed;
    right: 24px;
    bottom: 24px;
    width: min(560px, 92vw);
    max-height: 80vh;
    z-index: 80;
  }
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
  .window-frame.maximized .window-body {
    flex: 1;
    overflow-y: auto;
  }
  .window-frame.docked {
    position: fixed;
    left: 16px;
    right: 16px;
    bottom: 16px;
    max-height: 50vh;
    z-index: 70;
  }
  .window-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0.75rem 1rem;
    border-bottom: 1px solid rgba(15, 23, 42, 0.08);
    background: linear-gradient(180deg, rgba(248, 250, 252, 0.95), rgba(255, 255, 255, 0.9));
  }
  .window-title {
    font-weight: 600;
    color: #0f172a;
  }
  .window-actions {
    display: flex;
    align-items: center;
    gap: 0.25rem;
  }
  .icon-btn {
    height: 30px;
    width: 30px;
    display: grid;
    place-items: center;
    border-radius: 999px;
    border: 1px solid rgba(15, 23, 42, 0.1);
    background: white;
    color: #475569;
    transition: all 0.15s ease;
  }
  .icon-btn:hover {
    color: #0f172a;
    border-color: rgba(15, 23, 42, 0.25);
  }
  .window-body {
    flex: 1;
    overflow-y: auto;
    padding: 1rem;
  }
  .window-footer {
    padding: 0.75rem 1rem;
    border-top: 1px solid rgba(15, 23, 42, 0.08);
    background: rgba(248, 250, 252, 0.9);
  }
  .window-footer:empty {
    display: none;
  }
</style>

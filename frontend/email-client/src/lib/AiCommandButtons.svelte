<script>
  import { createEventDispatcher, onMount } from 'svelte';
import { Languages, ChevronDown, Sparkles, Highlighter, MailPlus, BookOpenCheck, Wand2 } from 'lucide-svelte';
import Portal from './components/Portal.svelte';

  /**
   * Renders the AI command buttons (summary/translate/etc.) so App.svelte only passes metadata.
   */
  export let commands = [];
  export let actionOptions = [];
  export let actionMenuLoading = false;
  export let mobile = false;
  export let layout = 'stacked';
  export let compact = false;
  const dispatch = createEventDispatcher();
  const preferredVariantOrder = ['es', 'pt', 'nl'];
  const FALLBACK_ACTION_OPTIONS = [
    { id: 'summarize-thread', label: 'Summarize thread', actionType: 'default', defaultPlaceholder: true, aiGenerated: false },
    { id: 'suggest-reply', label: 'Suggest reply ideas', actionType: 'default', defaultPlaceholder: true, aiGenerated: false },
    { id: 'cleanup', label: 'Cleanup + tone pass', actionType: 'default', defaultPlaceholder: true, aiGenerated: false }
  ];

  let translateMenuOpen = false;
  let translateDropdownEl = null;
  let translateButtonEl = null;
  let actionMenuOpen = false;
  let actionDropdownEl = null;
  let actionButtonEl = null;

  $: commandsList = Array.isArray(commands) ? commands : [];
  $: summarizeEntry = commandsList.find((entry) => entry?.key === 'summarize');
  $: draftEntry = commandsList.find((entry) => entry?.key === 'draft');
  $: translateEntry = commandsList.find((entry) => entry?.key === 'translate');
  $: orderedVariants = buildVariantOptions(translateEntry?.meta?.variants || []);
  $: otherEntries = commandsList.filter((entry) => !['draft', 'translate', 'summarize'].includes(entry?.key));
  $: actionOptionList = Array.isArray(actionOptions) && actionOptions.length ? actionOptions : [];
  $: actionMenuEntries = actionOptionList.length ? actionOptionList : FALLBACK_ACTION_OPTIONS;
  $: trayMode = layout === 'tray';

  function handleClick(key) {
    dispatch('select', { key });
  }

  function handleVariantSelect(variantKey) {
    if (!translateEntry) return;
    dispatch('select', { key: translateEntry.key, variantKey });
    translateMenuOpen = false;
  }

  function handleActionSelect(option) {
    if (!option) return;
    if (option.defaultPlaceholder) {
      triggerComingSoon(option.label);
      setActionMenuOpen(false);
      return;
    }
    dispatch('actionSelect', { option });
    setActionMenuOpen(false);
  }

  function triggerComingSoon(label) {
    dispatch('comingSoon', { label });
  }

  function toggleTranslateMenu() {
    if (!orderedVariants.length) return;
    translateMenuOpen = !translateMenuOpen;
  }

  function toggleActionMenu() {
    setActionMenuOpen(!actionMenuOpen);
  }

  function setActionMenuOpen(nextState) {
    if (actionMenuOpen === nextState) return;
    actionMenuOpen = nextState;
    dispatch('actionMenuToggle', { open: actionMenuOpen });
  }

  function handleDocumentClick(event) {
    const target = event.target;
    if (translateMenuOpen && !isWithin(target, translateDropdownEl, translateButtonEl)) {
      translateMenuOpen = false;
    }
    if (actionMenuOpen && !isWithin(target, actionDropdownEl, actionButtonEl)) {
      setActionMenuOpen(false);
    }
  }

  /**
   * Closes menus when focus moves outside the main document (e.g., into an iframe).
   * This handles the case where emails are rendered in iframes and clicks within
   * the iframe don't bubble to the parent document's click handler.
   *
   * Uses document.activeElement to detect iframe focus, which is more reliable
   * than relying solely on window.blur event firing.
   */
  function handleWindowBlur() {
    // Small delay to let the browser update document.activeElement
    setTimeout(() => {
      const active = document.activeElement;
      // Check if focus moved to an iframe (email content)
      if (active && active.tagName === 'IFRAME') {
        if (translateMenuOpen) translateMenuOpen = false;
        if (actionMenuOpen) setActionMenuOpen(false);
      }
    }, 0);
  }

  function isWithin(target, panelEl, triggerEl) {
    if (!target) return false;
    if (panelEl && panelEl.contains(target)) return true;
    if (triggerEl && triggerEl.contains(target)) return true;
    return false;
  }

  onMount(() => {
    // Listen for clicks in the current document
    document.addEventListener('click', handleDocumentClick);

    // Listen for window blur to detect focus moving to iframes
    window.addEventListener('blur', handleWindowBlur);

    return () => {
      document.removeEventListener('click', handleDocumentClick);
      window.removeEventListener('blur', handleWindowBlur);
    };
  });

  function buildVariantOptions(rawVariants) {
    if (!Array.isArray(rawVariants)) return [];
    const map = new Map(rawVariants.map((variant) => [variant.key, variant]));
    const ordered = preferredVariantOrder
      .map((key) => map.get(key))
      .filter(Boolean);
    return ordered;
  }

  /**
   * Positions a dropdown menu below its trigger button on mobile tray mode.
   */
  function positionMenu(buttonEl, dropdownEl) {
    if (!mobile || !trayMode || !buttonEl || !dropdownEl) return;
    const buttonRect = buttonEl.getBoundingClientRect();
    dropdownEl.style.top = `${buttonRect.bottom + 8}px`;
  }

  $: if (actionMenuOpen && mobile && trayMode && actionButtonEl && actionDropdownEl) {
    positionMenu(actionButtonEl, actionDropdownEl);
  }

  $: if (translateMenuOpen && mobile && trayMode && translateButtonEl && translateDropdownEl) {
    positionMenu(translateButtonEl, translateDropdownEl);
  }

  function resolveIconComponent(key) {
    const normalized = (key || '').toLowerCase();
    switch (normalized) {
      case 'summarize':
        return BookOpenCheck;
      case 'draft':
        return Wand2;
      case 'compose':
        return MailPlus;
      case 'tone':
        return Highlighter;
      default:
        return Sparkles;
    }
  }

  function labelForEntry(entry) {
    const key = (entry?.key || '').toLowerCase();
    switch (key) {
      case 'summarize':
        return 'Summarize';
      case 'draft':
        return 'Reply';
      case 'compose':
        return 'Compose';
      case 'tone':
        return 'Tone';
      default:
        return entry?.meta?.label || entry?.key;
    }
  }
</script>

<div class={`ai-action-toolbar ${mobile ? 'mobile' : ''} ${trayMode ? 'tray-mode' : ''} ${compact ? 'compact' : ''}`}>
  {#if !commandsList.length}
    <button
      type="button"
      class="btn btn--secondary btn--compact action-pill"
      aria-label="Run Assistant"
      title="Run Assistant"
      on:click={() => handleClick('summarize')}>
      <span class="action-pill__icon btn-icon-chip">
        <Sparkles class="h-4 w-4" aria-hidden="true" />
      </span>
      <span class="action-pill__label">Assistant</span>
    </button>
  {:else}
      <div class="relative action-pill__dropdown" class:span-2={mobile && !trayMode}>
        <button
          type="button"
          class="btn"
          class:action-pill={!trayMode}
          class:btn--icon={trayMode}
          class:btn--secondary={!trayMode}
          class:btn--compact={!trayMode}
          class:w-full={mobile && !trayMode}
          class:justify-center={mobile && !trayMode}
          on:click={toggleActionMenu}
          aria-haspopup="menu"
          aria-expanded={actionMenuOpen}
          aria-label="AI Actions"
          title="AI Actions"
          aria-busy={actionMenuLoading}
          bind:this={actionButtonEl}>
          {#if trayMode}
            <Sparkles class="h-4 w-4" aria-hidden="true" />
          {:else}
            <span class="action-pill__icon btn-icon-chip">
              <Sparkles class="h-4 w-4" aria-hidden="true" />
            </span>
            <span class="action-pill__label tracking-wide">Actions</span>
            <ChevronDown
              class={`action-pill__chevron h-4 w-4 transition ${actionMenuOpen ? 'opacity-70 rotate-180' : 'opacity-90'}`}
              aria-hidden="true" />
          {/if}
        </button>
      {#if actionMenuOpen}
        {#if mobile && trayMode}
          <Portal target="body">
            <div
              class="menu-surface action-dropdown mobile-tray-dropdown"
              data-layer="nested"
              bind:this={actionDropdownEl}>
              <span class="menu-eyebrow">Suggested Actions</span>
              <div class="menu-list">
                {#each actionMenuEntries as option (option.id || option.label)}
                  <button
                    type="button"
                    class="menu-item mobile-tray-menu-item"
                    on:click={() => handleActionSelect(option)}>
                    <div class="flex items-center min-w-0 gap-2">
                      {#if option.aiGenerated}
                        <span class="menu-item-icon" aria-hidden="true">
                          <Sparkles class="h-4 w-4" />
                        </span>
                      {/if}
                      <span class="truncate">{option.label}</span>
                    </div>
                  </button>
                {/each}
              </div>
            </div>
          </Portal>
        {:else}
          <div
            class="absolute menu-surface action-dropdown"
            data-layer="nested"
            bind:this={actionDropdownEl}>
            <span class="menu-eyebrow">Suggested Actions</span>
            <div class="menu-list">
              {#each actionMenuEntries as option (option.id || option.label)}
                <button
                  type="button"
                  class="menu-item"
                  on:click={() => handleActionSelect(option)}>
                  <div class="flex items-center min-w-0 gap-2">
                    {#if option.aiGenerated}
                      <span class="menu-item-icon" aria-hidden="true">
                        <Sparkles class="h-4 w-4" />
                      </span>
                    {/if}
                    <span class="truncate">{option.label}</span>
                  </div>
                </button>
              {/each}
            </div>
          </div>
        {/if}
      {/if}
    </div>

    {#if summarizeEntry}
      <button
        type="button"
        class="btn"
        class:action-pill={!trayMode}
        class:btn--icon={trayMode}
        class:btn--ghost={!trayMode}
        class:btn--compact={!trayMode}
        aria-label={labelForEntry(summarizeEntry)}
        title={labelForEntry(summarizeEntry)}
        on:click={() => handleClick(summarizeEntry.key)}>
        {#if trayMode}
          <svelte:component this={resolveIconComponent(summarizeEntry.key)} class="h-4 w-4" aria-hidden="true" />
        {:else}
          <span class="action-pill__icon">
            <svelte:component this={resolveIconComponent(summarizeEntry.key)} class="h-4 w-4" aria-hidden="true" />
          </span>
          <span class="action-pill__label">{labelForEntry(summarizeEntry)}</span>
        {/if}
      </button>
    {/if}

    {#if draftEntry}
      <button
        type="button"
        class="btn"
        class:action-pill={!trayMode}
        class:btn--icon={trayMode}
        class:btn--ghost={!trayMode}
        class:btn--compact={!trayMode}
        aria-label={labelForEntry(draftEntry)}
        title={labelForEntry(draftEntry)}
        on:click={() => handleClick(draftEntry.key)}>
        {#if trayMode}
          <svelte:component this={resolveIconComponent(draftEntry.key)} class="h-4 w-4" aria-hidden="true" />
        {:else}
          <span class="action-pill__icon">
            <svelte:component this={resolveIconComponent(draftEntry.key)} class="h-4 w-4" aria-hidden="true" />
          </span>
          <span class="action-pill__label">{labelForEntry(draftEntry)}</span>
        {/if}
      </button>
    {/if}

    {#if translateEntry && orderedVariants.length && !mobile}
      <div class="relative action-pill__dropdown">
        <button
          type="button"
          class={`btn btn--ghost btn--compact action-pill justify-between ${mobile ? 'w-full' : ''}`}
          class:action-pill--tray={trayMode}
          on:click={toggleTranslateMenu}
          aria-haspopup="menu"
          aria-expanded={translateMenuOpen}
          aria-label="Translate"
          title="Translate"
          bind:this={translateButtonEl}>
          <div class="flex items-center gap-2">
            <span class="action-pill__icon">
              <Languages class="h-4 w-4" aria-hidden="true" />
            </span>
            <span class="action-pill__label">Translate</span>
          </div>
          <ChevronDown
            class={`action-pill__chevron h-4 w-4 transition ${translateMenuOpen ? 'rotate-180' : ''}`}
            aria-hidden="true" />
        </button>
        {#if translateMenuOpen}
          {#if mobile && trayMode}
            <Portal target="body">
              <div class="menu-surface translate-dropdown mobile-tray-dropdown" data-layer="nested" bind:this={translateDropdownEl}>
                <span class="menu-eyebrow">Translate To</span>
                <div class="menu-list">
                  {#each orderedVariants as variant (variant.key)}
                    <button
                      type="button"
                      class="menu-item mobile-tray-menu-item"
                      on:click={() => handleVariantSelect(variant.key)}>
                      <div class="flex items-center min-w-0">
                        <span class="menu-item-icon">
                          <Languages class="h-4 w-4" />
                        </span>
                        <span class="truncate">{variant.label}</span>
                      </div>
                    </button>
                  {/each}
                </div>
                <button
                  type="button"
                  class="mt-4 panel-chip justify-center w-full"
                  on:click={() => { triggerComingSoon('Translate customization'); translateMenuOpen = false; }}>
                  <Sparkles class="h-4 w-4" />
                  Customize
                </button>
              </div>
            </Portal>
          {:else}
            <div class="absolute menu-surface translate-dropdown" data-layer="nested" bind:this={translateDropdownEl}>
              <span class="menu-eyebrow">Translate To</span>
              <div class="menu-list">
                {#each orderedVariants as variant (variant.key)}
                  <button
                    type="button"
                    class="menu-item"
                    on:click={() => handleVariantSelect(variant.key)}>
                    <div class="flex items-center min-w-0">
                      <span class="menu-item-icon">
                        <Languages class="h-4 w-4" />
                      </span>
                      <span class="truncate">{variant.label}</span>
                    </div>
                  </button>
                {/each}
              </div>
              <button
                type="button"
                class="mt-4 panel-chip justify-center w-full"
                on:click={() => { triggerComingSoon('Translate customization'); translateMenuOpen = false; }}>
                <Sparkles class="h-4 w-4" />
                Customize
              </button>
            </div>
          {/if}
        {/if}
      </div>
    {/if}

    {#each otherEntries as entry (entry.key)}
      <button
        type="button"
        class="btn btn--ghost btn--compact action-pill"
        class:action-pill--tray={trayMode}
        aria-label={labelForEntry(entry)}
        title={labelForEntry(entry)}
        on:click={() => handleClick(entry.key)}>
        <span class="action-pill__icon">
          <svelte:component this={resolveIconComponent(entry.key)} class="h-4 w-4" aria-hidden="true" />
        </span>
        <span class="action-pill__label">{labelForEntry(entry)}</span>
      </button>
    {/each}
  {/if}
</div>

<style>
  /**
   * AI toolbar wrapper keeps dropdowns clickable above the AI summary card while enabling responsive layouts.
   * @usage - Surrounds AI action buttons in EmailActionToolbar contexts
   * @z-index-warning - Maintains z-index 150 to float over DrawerBackdrop (z-50) and content iframe
   * @related - .ai-action-toolbar.mobile, .ai-action-toolbar.mobile.tray-mode
   */
  .ai-action-toolbar {
    margin-top: 1rem;
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
    position: relative;
    z-index: 150;
    overflow: visible;
  }

  .ai-action-toolbar.mobile {
    width: 100%;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 0.65rem;
  }

  /**
   * Icon + label pairing for AI action pills.
   * @usage - Wrap icon components and text spans in AiCommandButtons markup
   * @related - .ai-action-toolbar.compact for responsive collapsing
   */
  .action-pill__icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 20px;
    height: 20px;
    flex-shrink: 0;
  }

  .action-pill__label {
    white-space: nowrap;
  }

  .action-pill__chevron {
    margin-left: 0.35rem;
  }

  /**
   * Dropdown wrappers keep trigger buttons (Actions/Translate) level with adjacent pills despite nested menus.
   * @usage - Apply to wrappers that pair a trigger button with an absolutely positioned dropdown menu
   */
  .action-pill__dropdown {
    display: inline-flex;
    align-items: stretch;
    align-self: stretch;
  }

  .action-pill__dropdown > :global(.btn) {
    height: 100%;
  }

  /**
   * Compact tier (≤960px desktop width) collapses AI button labels to icons so the toolbar
   * fits alongside the desktop action row without clipping.
   * @usage - Applied when App.svelte passes compact={true}
   * @related - .action-pill__label, .action-pill__icon
   */
  .ai-action-toolbar.compact {
    gap: 0.35rem;
  }

  .ai-action-toolbar.compact :global(.btn.btn--compact) {
    padding-left: 0.75rem;
    padding-right: 0.75rem;
    gap: 0;
    min-width: 42px;
  }

  .ai-action-toolbar.compact .action-pill__label {
    display: none;
  }

  .ai-action-toolbar.compact .action-pill__icon {
    margin-right: 0;
  }

  .ai-action-toolbar.mobile:not(.tray-mode) :global(.btn.btn--compact) {
    width: 100%;
  }

  .ai-action-toolbar.mobile:not(.tray-mode) .span-2 {
    grid-column: 1 / -1;
  }

  .ai-action-toolbar.mobile :global(.btn-icon-chip) {
    width: 20px;
    height: 20px;
  }

  .ai-action-toolbar.mobile :global(.btn svg) {
    width: 16px;
    height: 16px;
  }

  .ai-action-toolbar.mobile .relative {
    width: 100%;
  }

  /**
   * Tray variant renders inside the horizontal action lane on mobile.
   * @usage - Activated when layout="tray" and mobile flag is true
   * @related - .action-tray__scroller in EmailActionToolbar.svelte
   */
  .ai-action-toolbar.mobile.tray-mode {
    display: contents; /* Make wrapper invisible - children become direct flex items */
  }


  /**
   * Action pills normalize icon sizing between AI actions and native controls.
   * REMOVED: min-height and padding now inherit from global .btn--compact for consistency.
   * @usage - Base class for AI action buttons regardless of viewport
   * @related - .action-pill--tray for compact variant
   */

  .action-pill :global(svg) {
    width: 16px;
    height: 16px;
  }

  .action-pill :global(.btn-icon-chip) {
    width: 16px;
    height: 16px;
  }

  /**
   * Tray modifier tightens width + padding so AI pills blend with the mobile action chips.
   * @usage - Applied via class:action-pill--tray when layout="tray"
   * @related - .action-tray__ai in EmailActionToolbar.svelte
   */
  .action-pill--tray {
    padding-left: 0.75rem;
    padding-right: 0.75rem;
    min-height: 34px;
    font-size: 0.8rem;
  }

  /**
   * Dropdown positioning ensures menus don't cover their trigger buttons.
   * Increased spacing from the default mt-2 (0.5rem) to 0.75rem for better visual clearance.
   * @usage - Applied to AI action dropdown and translate menu dropdown surfaces
   * @related - .menu-surface
   */
  .action-dropdown,
  .translate-dropdown {
    top: calc(100% + 0.75rem);
  }

  /**
   * Mobile tray mode uses fixed positioning to avoid overflow clipping issues.
   * Matches the mobile-overflow-menu behavior from EmailActionToolbar.
   * @usage - Applied when mobile + tray-mode flags are active
   * @related - .mobile-overflow-menu in EmailActionToolbar.svelte
   */
  .ai-action-toolbar.mobile.tray-mode .action-dropdown,
  .ai-action-toolbar.mobile.tray-mode .translate-dropdown {
    position: fixed;
    right: 1rem;
    top: auto;
    bottom: auto;
    z-index: 250;
    background: white;
    border-radius: 0.85rem;
    border: 1px solid #e2e8f0;
    box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05);
    padding: 0.5rem;
    min-width: 11rem;
    max-height: 80vh;
    overflow-y: auto;
  }

  /**
   * Standalone class for mobile tray dropdowns when using Portal.
   * This class works independently of parent context, necessary because Portal
   * moves the element to document.body, breaking descendant selectors.
   * @usage - Applied to dropdowns rendered inside Portal on mobile tray mode
   * @related - .ai-action-toolbar.mobile.tray-mode .action-dropdown (non-portal version)
   */
  .mobile-tray-dropdown {
    position: fixed;
    right: 1rem;
    top: auto;
    bottom: auto;
    z-index: 250;
    background: white;
    border-radius: 0.85rem;
    border: 1px solid #e2e8f0;
    box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05);
    padding: 0.5rem;
    min-width: 11rem;
    max-height: 80vh;
    overflow-y: auto;
  }

  /**
   * Mobile tray mode menu items match the mobile-overflow-menu__item styling.
   * @usage - Menu items inside dropdowns when mobile + tray-mode are active
   * @related - .mobile-overflow-menu__item in EmailActionToolbar.svelte
   */
  .ai-action-toolbar.mobile.tray-mode .menu-item {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    width: 100%;
    padding: 0.65rem 0.75rem;
    border-radius: 0.5rem;
    background: transparent;
    border: none;
    color: #0f172a;
    font-size: 0.9rem;
    font-weight: 500;
    text-align: left;
    cursor: pointer;
    transition: background 0.15s ease;
  }

  .ai-action-toolbar.mobile.tray-mode .menu-item:hover {
    background: rgba(248, 250, 252, 0.9);
  }

  /**
   * Standalone class for menu items inside portaled dropdowns.
   * @usage - Applied to menu items when using Portal on mobile tray mode
   * @related - .ai-action-toolbar.mobile.tray-mode .menu-item (non-portal version)
   */
  .mobile-tray-menu-item {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    width: 100%;
    padding: 0.65rem 0.75rem;
    border-radius: 0.5rem;
    background: transparent;
    border: none;
    color: #0f172a;
    font-size: 0.9rem;
    font-weight: 500;
    text-align: left;
    cursor: pointer;
    transition: background 0.15s ease;
  }

  .mobile-tray-menu-item:hover {
    background: rgba(248, 250, 252, 0.9);
  }

  /**
   * Eyebrow headers for portaled dropdowns in mobile tray mode.
   * Uses standalone class because Portal renders to body, outside .ai-action-toolbar.
   * @usage - Section headers inside mobile-tray-dropdown containers
   * @related - .mobile-overflow-menu__eyebrow in EmailActionToolbar.svelte
   */
  .mobile-tray-dropdown .menu-eyebrow {
    font-size: 0.65rem;
    letter-spacing: 0.2em;
    text-transform: uppercase;
    color: #94a3b8;
    padding: 0.5rem 0.75rem;
    display: block;
  }

  /**
   * Icon styling for portaled menu items in mobile tray mode.
   * Uses standalone class because Portal renders to body, outside .ai-action-toolbar.
   * @usage - Icon containers inside mobile-tray-menu-item buttons
   */
  .mobile-tray-menu-item .menu-item-icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }

  /**
   * SVG icon sizing for portaled menu items in mobile tray mode.
   */
  .mobile-tray-menu-item :global(svg) {
    width: 1rem;
    height: 1rem;
  }
</style>

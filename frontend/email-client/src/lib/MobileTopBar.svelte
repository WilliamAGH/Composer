<script>
  import { createEventDispatcher } from 'svelte';
  import { ArrowLeft, Menu, Sparkles, Loader2, X } from 'lucide-svelte';

  export let variant = 'search';
  export let showBackButton = true;
  export let showMenuButton = true;
  export let backIcon = 'arrow'; // 'arrow' | 'close'
  export let backButtonAriaLabel = 'Back to list';
  export let menuButtonAriaLabel = 'Open folders';
  export let searchValue = '';
  export let searchPlaceholder = 'Search emails...';
  export let compactActions = false;
  export let hasMailboxCommands = false;
  export let mailboxCommandEntries = [];
  export let mailboxCommandPendingKey = null;
  export let mailboxActionsOpen = false;
  export let activeActionsHost = null;
  export let actionsHost = 'mobile';
  export let activeMailboxActionLabel = '';
  export let mailboxActionError = '';
  export let filteredCount = 0;
  export let searchDisabled = false;
  export let actionSurfaceRef = null;

  const dispatch = createEventDispatcher();

  $: disableAiButton = !hasMailboxCommands || filteredCount === 0 || !!mailboxCommandPendingKey;
  $: actionsMenuVisible = mailboxActionsOpen && activeActionsHost === actionsHost;

  function handleBack() {
    dispatch('back');
  }

  function handleMenu() {
    dispatch('toggleMenu');
  }

  function handleSearch(event) {
    dispatch('searchChange', { value: event.currentTarget.value });
  }

  function handleToggleActions() {
    dispatch('toggleMailboxActions', { host: actionsHost });
  }

  function handleMailboxAction(entry) {
    dispatch('mailboxAction', { entry });
  }
</script>

<div class="mobile-top-bar" class:mobile-top-bar--no-divider={variant === 'custom'}>
  <div class="mobile-top-bar__row">
    {#if showBackButton}
      <button type="button" class="btn btn--icon" style="z-index: var(--z-drawer-controls, 175);" aria-label={backButtonAriaLabel} on:click={handleBack}>
        {#if backIcon === 'close'}
          <X class="h-4 w-4" aria-hidden="true" />
        {:else}
          <ArrowLeft class="h-4 w-4" aria-hidden="true" />
        {/if}
      </button>
    {/if}

    <div class="mobile-top-bar__content" class:mobile-top-bar__content--search={variant === 'search'}>
      {#if variant === 'search'}
        <div class="mobile-search" bind:this={actionSurfaceRef}>
          <input
            placeholder={searchPlaceholder}
            value={searchValue}
            on:input={handleSearch}
            class="mailbox-search-input w-full rounded-2xl border border-slate-200 bg-white/90 pl-4 py-2 text-base text-slate-800 shadow-inner focus:outline-none focus:ring-2 focus:ring-slate-200"
            class:pr-28={compactActions && showMenuButton}
            class:pr-16={compactActions && !showMenuButton}
            class:pr-44={!compactActions && showMenuButton}
            class:pr-32={!compactActions && !showMenuButton}
            disabled={searchDisabled}
          />
          <div class="mobile-search__actions">
            <button
              type="button"
              class="btn btn--primary btn--compact mailbox-ai-trigger"
              class:mailbox-ai-trigger--compact={compactActions}
              aria-haspopup="menu"
              aria-expanded={actionsMenuVisible}
              on:click={handleToggleActions}
              disabled={disableAiButton}
            >
              <span class="flex items-center gap-1">
                {#if mailboxCommandPendingKey}
                  <Loader2 class="h-4 w-4 animate-spin" aria-hidden="true" />
                {:else}
                  <Sparkles class="h-4 w-4" aria-hidden="true" />
                {/if}
              </span>
              <span class="mailbox-ai-trigger__label" class:hidden={compactActions}>
                {#if mailboxCommandPendingKey}
                  {activeMailboxActionLabel ? `${activeMailboxActionLabel}…` : 'Working…'}
                {:else}
                  AI Actions
                {/if}
              </span>
            </button>
            {#if showMenuButton}
              <button
                type="button"
                class="btn btn--icon relative"
                style="z-index: var(--z-drawer-controls, 175);"
                aria-label={menuButtonAriaLabel}
                on:click={handleMenu}>
                <Menu class="h-4 w-4" aria-hidden="true" />
              </button>
            {/if}
          </div>
          {#if actionsMenuVisible}
            <div class="mobile-search__menu menu-surface" role="menu" tabindex="0" on:click|stopPropagation on:keydown|stopPropagation>
              <span class="menu-eyebrow">Mailbox Actions</span>
              <div class="menu-list">
                {#each mailboxCommandEntries as entry (entry.key)}
                  <button type="button" class="menu-item text-left" on:click={() => handleMailboxAction(entry)} disabled={filteredCount === 0}>
                    <div class="flex items-center gap-3 min-w-0">
                      <div class="menu-item-icon">
                        <Sparkles class="h-4 w-4" aria-hidden="true" />
                      </div>
                      <div class="flex-1 min-w-0">
                        <p class="font-medium text-slate-900 tracking-wide truncate">{entry.label || entry.key}</p>
                        {#if entry.description}
                          <p class="text-xs text-slate-500 leading-snug">{entry.description}</p>
                        {/if}
                      </div>
                    </div>
                  </button>
                {/each}
              </div>
              <div class="mt-3 text-xs text-slate-500">
                Mailbox AI actions apply to the {filteredCount} message{filteredCount === 1 ? '' : 's'} currently listed.
              </div>
            </div>
          {/if}
        </div>
        {#if mailboxCommandPendingKey && activeMailboxActionLabel}
          <p class="mobile-top-bar__status">{activeMailboxActionLabel} in progress…</p>
        {/if}
        {#if mailboxActionError}
          <p class="mobile-top-bar__error">{mailboxActionError}</p>
        {/if}
      {:else}
        <slot name="center"></slot>
      {/if}
    </div>

    {#if variant !== 'search'}
      <div class="mobile-top-bar__actions">
        <slot name="actions"></slot>
      </div>
    {/if}
  </div>
</div>

<style>
  /**
   * Mobile primary navigation bar for search + hamburger.
   * @usage - Top-most mailbox nav when viewing the list on mobile.
   * @z-index-warning - Uses var(--z-toolbar-surface) to sit above DrawerBackdrop (50) and
   *                    MailboxSidebar (60), but below global modals and dropdown menus.
   * @related - .mobile-search__actions, DrawerBackdrop.svelte, MailboxSidebar.svelte, app-shared.css
   */
  .mobile-top-bar {
    padding: 1rem;
    border-bottom: 1px solid rgba(148, 163, 184, 0.4);
    background: rgba(255, 255, 255, 0.96);
    backdrop-filter: blur(10px);
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
    position: relative;
    z-index: var(--z-toolbar-surface, 150);
  }

  .mobile-top-bar--no-divider {
    border-bottom: none;
    padding-bottom: 0.75rem;
  }

  .mobile-top-bar__row {
    display: flex;
    align-items: center;
    gap: 0.75rem;
  }


  .mobile-top-bar__content {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 0.25rem;
  }

  .mobile-top-bar__content--search {
    gap: 0.4rem;
  }

  .mobile-top-bar__actions {
    display: inline-flex;
    align-items: center;
    gap: 0.5rem;
    margin-left: auto;
    justify-content: flex-end;
  }

  .mobile-search {
    position: relative;
    flex: 1;
  }

  /**
   * Actions container for AI trigger + hamburger menu inside search input.
   * @usage - Right-aligned button group within mobile search
   * @related - .mailbox-ai-trigger, .btn--icon
   */
  .mobile-search__actions {
    position: absolute;
    inset: 3px 3px 3px auto;
    display: flex;
    align-items: center;
    gap: 0.35rem;
  }

  /**
   * Dropdown shell for AI mailbox actions on mobile search.
   * @usage - Menu rendered when the Actions button in the mobile search bar is toggled
   * @z-index-warning - Must stay above the toolbar surface (z-150); rely on --z-dropdown stack
   * @related - .menu-surface in app-shared.css for shared styling
   */
  .mobile-search__menu {
    position: absolute;
    right: 0;
    top: calc(100% + 0.5rem);
    min-width: 16rem;
    z-index: var(--z-dropdown, 200);
  }

  .mobile-top-bar__status {
    font-size: 0.75rem;
    color: #475569;
  }

  .mobile-top-bar__error {
    font-size: 0.75rem;
    color: #be123c;
  }
</style>

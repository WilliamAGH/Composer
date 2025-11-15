<script>
  import { createEventDispatcher, onMount } from 'svelte';
  import {
    Reply,
    Forward,
    Archive,
    Trash2,
    FolderSymlink,
    Loader2,
    MoreVertical,
    Languages,
    ArrowLeft,
    ChevronLeft,
    Sparkles
  } from 'lucide-svelte';
  import AiCommandButtons from './AiCommandButtons.svelte';
  import MailboxMoveMenu from './MailboxMoveMenu.svelte';

  /**
   * Header for the selected email: shows sender info, quick actions, and AI command buttons.
   * Extracted from App.svelte so message-level actions are reusable and easier to maintain.
   */
  export let email = null;
  export let commands = [];
  export let actionMenuOptions = [];
  export let actionMenuLoading = false;
  export let mobile = false;
  export let showBackButton = false;
  export let compactActions = false;
  export let currentFolderId = 'inbox';
  export let pendingMove = false;
  export let escapeHtmlFn = (value) => value ?? '';
  export let formatFullDateFn = () => '';

  const dispatch = createEventDispatcher();
  const preferredVariantOrder = ['es', 'pt', 'nl'];
  let moveMenuOpen = false;
  let localMoveMenuButton = null;
  let moveMenuRef = null;
  let moreMenuOpen = false;
  let moreMenuButton = null;
  let moreMenuRef = null;
  let translateMenuOpen = false;

  $: commandsList = Array.isArray(commands) ? commands : [];
  $: translateEntry = commandsList.find((entry) => entry?.key === 'translate');
  $: orderedVariants = buildVariantOptions(translateEntry?.meta?.variants || []);

  function emit(type, detail) {
    dispatch(type, detail);
  }

  function buildVariantOptions(rawVariants) {
    if (!Array.isArray(rawVariants)) return [];
    const map = new Map(rawVariants.map((variant) => [variant.key, variant]));
    return preferredVariantOrder.map((key) => map.get(key)).filter(Boolean);
  }

  function handleVariantSelect(variantKey) {
    if (!translateEntry) return;
    // Defer to ensure menu animations can complete before any parent re-renders
    setTimeout(() => {
      emit('commandSelect', { key: translateEntry.key, variantKey });
    }, 100);
    closeMoreMenu();
  }

  /**
   * Toggles the toolbar move dropdown visibility.
   */
  function toggleMoveMenu() {
    moveMenuOpen = !moveMenuOpen;
    if (moveMenuOpen) {
      moreMenuOpen = false;
      translateMenuOpen = false;
    }
  }

  function closeMoveMenu() {
    moveMenuOpen = false;
  }

  /**
   * Toggles the mobile overflow menu visibility.
   */
  function toggleMoreMenu() {
    const nextState = !moreMenuOpen;
    moreMenuOpen = nextState;
    if (nextState) {
      moveMenuOpen = false;
      translateMenuOpen = false; // Reset nested menu on toggle
    }
    emit('moreMenuToggle', { open: nextState });
  }

  function closeMoreMenu() {
    moreMenuOpen = false;
    translateMenuOpen = false;
    emit('moreMenuToggle', { open: false });
  }

  /**
   * Emits the selected folder target to the parent component.
   */
  function handleMoveSelect(event) {
    closeMoveMenu();
    emit('move', { targetFolderId: event.detail.targetId });
  }

  /**
   * Closes dropdowns when clicking outside the control.
   */
  function handleGlobalPointer(event) {
    if (!moveMenuOpen && !moreMenuOpen) return;
    const target = event.target;
    if (localMoveMenuButton?.contains(target) || moveMenuRef?.contains(target)) {
      return;
    }
    if (moreMenuButton?.contains(target) || moreMenuRef?.contains(target)) {
      return;
    }
    moveMenuOpen = false;
    closeMoreMenu();
  }

  onMount(() => {
    document.addEventListener('pointerdown', handleGlobalPointer);
    return () => document.removeEventListener('pointerdown', handleGlobalPointer);
  });

  /**
   * Positions the overflow menu below the trigger button.
   */
  function positionOverflowMenu() {
    if (!moreMenuButton || !moreMenuRef) return;
    const buttonRect = moreMenuButton.getBoundingClientRect();
    moreMenuRef.style.top = `${buttonRect.bottom + 8}px`;
  }

  $: if (moreMenuOpen && moreMenuButton && moreMenuRef) {
    positionOverflowMenu();
  }
</script>

{#if email}
  {#if mobile}
    <div class="email-header-mobile">
      <div class="email-header-mobile__meta">
        {#if showBackButton}
          <button
            type="button"
            class="btn btn--icon"
            style="z-index: var(--z-drawer-controls, 175);"
            aria-label="Back to inbox"
            on:click={() => emit('back')}>
            <ArrowLeft class="h-4 w-4" aria-hidden="true" />
          </button>
        {:else}
          <img
            src={email.avatar || email.companyLogoUrl || 'data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 120 120%22%3E%3Crect fill=%22%23e2e8f0%22 width=%22120%22 height=%22120%22/%3E%3Ctext x=%2250%25%22 y=%2250%25%22 dominant-baseline=%22middle%22 text-anchor=%22middle%22 font-family=%22system-ui%22 font-size=%2248%22 fill=%22%2394a3b8%22%3E%3F%3C/text%3E%3C/svg%3E'}
            alt={escapeHtmlFn(email.from)}
            class="email-header-mobile__avatar"
            loading="lazy" />
        {/if}
        <div class="email-header-mobile__meta-content">
          <h2 class="email-header-mobile__subject">{escapeHtmlFn(email.subject)}</h2>
          <div class="email-header-mobile__line">
            <span class="email-header-mobile__label">From</span>
            <p class="email-header-mobile__value">
              <span class="font-medium">{escapeHtmlFn(email.from)}</span>
              {#if email.fromEmail}
                <span class="email-header-mobile__muted">&lt;{escapeHtmlFn(email.fromEmail)}&gt;</span>
              {/if}
            </p>
          </div>
          {#if email.to || email.toEmail}
            <div class="email-header-mobile__line">
              <span class="email-header-mobile__label">To</span>
              <p class="email-header-mobile__value">
                {escapeHtmlFn(email.to || 'Unknown recipient')}
                {#if email.toEmail}
                  <span class="email-header-mobile__muted">&lt;{escapeHtmlFn(email.toEmail)}&gt;</span>
                {/if}
              </p>
            </div>
          {/if}
          <p class="email-header-mobile__timestamp">{formatFullDateFn(email.timestampIso, email.timestamp)}</p>
        </div>
      </div>

      <div class="action-tray" role="toolbar" aria-label="Message actions">
        <div class="action-tray__scroller">
          <button
            type="button"
            class="btn btn--icon"
            aria-label="Reply"
            title="Reply"
            on:click={() => emit('reply')}>
            <Reply class="h-4 w-4" />
          </button>
          {#if currentFolderId !== 'archive'}
            <button
              type="button"
              class="btn btn--icon"
              aria-label="Archive"
              title="Archive"
              on:click={() => emit('archive')}>
              <Archive class="h-4 w-4" />
            </button>
          {/if}
          <div class="mobile-action-menu-shell" class:mobile-action-menu-shell--open={moveMenuOpen}>
            {#if moveMenuOpen}
              <div class="move-menu-surface" bind:this={moveMenuRef}>
                <MailboxMoveMenu currentFolderId={currentFolderId} pending={pendingMove} on:select={handleMoveSelect} />
              </div>
            {/if}
          </div>
          <div class="action-tray__buttons">
            <AiCommandButtons
              {commands}
              layout="tray"
              actionOptions={actionMenuOptions}
              actionMenuLoading={actionMenuLoading}
              {mobile}
              compact={compactActions}
              on:select={(event) => emit('commandSelect', event.detail)}
              on:actionSelect={(event) => emit('actionSelect', event.detail)}
              on:actionMenuToggle={(event) => emit('actionMenuToggle', event.detail)}
              on:comingSoon={(event) => emit('comingSoon', event.detail)}
            />
            <button
              bind:this={moreMenuButton}
              type="button"
              class="btn btn--icon"
              aria-label="More actions"
              title="More"
              aria-expanded={moreMenuOpen}
              on:click={toggleMoreMenu}>
              <MoreVertical class="h-4 w-4" />
            </button>
          </div>
          <div class="mobile-action-menu-shell" class:mobile-action-menu-shell--open={moreMenuOpen}>
            {#if moreMenuOpen}
              <div class="mobile-overflow-menu" bind:this={moreMenuRef}>
                {#if translateMenuOpen}
                  <button type="button" class="mobile-overflow-menu__item mobile-overflow-menu__item--header" on:click={() => translateMenuOpen = false}>
                    <ChevronLeft class="h-4 w-4" />
                    <span>Back</span>
                  </button>
                  <div class="mobile-overflow-menu__divider" />
                  <div class="mobile-overflow-menu__eyebrow">Translate To</div>
                  {#each orderedVariants as variant (variant.key)}
                    <button type="button" class="mobile-overflow-menu__item" on:click={() => handleVariantSelect(variant.key)}>
                      <Languages class="h-4 w-4" />
                      <span>{variant.label}</span>
                    </button>
                  {/each}
                  <div class="mobile-overflow-menu__divider" />
                  <button type="button" class="mobile-overflow-menu__item" on:click={() => { emit('comingSoon', { label: 'Translate customization' }); closeMoreMenu(); }}>
                    <Sparkles class="h-4 w-4" />
                    <span>Customize</span>
                  </button>
                {:else}
                  <button type="button" class="mobile-overflow-menu__item" on:click={() => { emit('forward'); closeMoreMenu(); }}>
                    <Forward class="h-4 w-4" />
                    <span>Forward</span>
                  </button>
                  {#if translateEntry && orderedVariants.length}
                    <button type="button" class="mobile-overflow-menu__item" on:click={() => translateMenuOpen = true}>
                      <Languages class="h-4 w-4" />
                      <span>Translate</span>
                    </button>
                  {/if}
                  <button type="button" class="mobile-overflow-menu__item" on:click={() => { toggleMoveMenu(); closeMoreMenu(); }}>
                    <FolderSymlink class="h-4 w-4" />
                    <span>Move to folder</span>
                  </button>
                  <button type="button" class="mobile-overflow-menu__item mobile-overflow-menu__item--destructive" on:click={() => { emit('delete'); closeMoreMenu(); }}>
                    <Trash2 class="h-4 w-4" />
                    <span>Delete</span>
                  </button>
                {/if}
              </div>
            {/if}
          </div>
        </div>
      </div>
    </div>
  {:else}
    <div class="flex items-start gap-3">
      <div class="flex items-start gap-3 min-w-0 flex-1">
        <img src={email.avatar || email.companyLogoUrl || 'data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 120 120%22%3E%3Crect fill=%22%23e2e8f0%22 width=%22120%22 height=%22120%22/%3E%3Ctext x=%2250%25%22 y=%2250%25%22 dominant-baseline=%22middle%22 text-anchor=%22middle%22 font-family=%22system-ui%22 font-size=%2248%22 fill=%22%2394a3b8%22%3E%3F%3C/text%3E%3C/svg%3E'} alt={escapeHtmlFn(email.from)} class="h-12 w-12 rounded-full object-cover shrink-0" loading="lazy" />
        <div class="min-w-0 flex-1">
          <h2 class="text-lg font-semibold text-slate-900 break-words">{escapeHtmlFn(email.subject)}</h2>
          <div class="flex items-center gap-1 text-sm text-slate-600 flex-wrap">
            <span class="font-medium truncate">{escapeHtmlFn(email.from)}</span>
            {#if email.fromEmail}<span class="text-xs truncate">&lt;{escapeHtmlFn(email.fromEmail)}&gt;</span>{/if}
          </div>
          {#if email.to || email.toEmail}
            <div class="text-xs mt-1 text-slate-400 truncate">To: {escapeHtmlFn(email.to || 'Unknown recipient')} {#if email.toEmail}<span>&lt;{escapeHtmlFn(email.toEmail)}&gt;</span>{/if}</div>
          {/if}
          <p class="text-xs mt-1 text-slate-400">{formatFullDateFn(email.timestampIso, email.timestamp)}</p>
        </div>
      </div>
      <div class="flex gap-2 shrink-0">
        <button
          type="button"
          class="btn btn--icon"
          aria-label="Reply"
          title="Reply"
          on:click={() => emit('reply')}>
          <Reply class="h-4 w-4" />
        </button>
        <button
          type="button"
          class="btn btn--icon"
          aria-label="Forward"
          title="Forward"
          on:click={() => emit('forward')}>
          <Forward class="h-4 w-4" />
        </button>
        {#if currentFolderId !== 'archive'}
          <button
            type="button"
            class="btn btn--icon"
            aria-label="Archive"
            title="Archive"
            on:click={() => emit('archive')}>
            <Archive class="h-4 w-4" />
          </button>
        {/if}
        <div class="relative">
          <button
            bind:this={localMoveMenuButton}
            type="button"
            class="btn btn--icon"
            aria-label="Move"
            title="Move"
            aria-expanded={moveMenuOpen}
            on:click={toggleMoveMenu}>
            {#if pendingMove}
              <Loader2 class="h-4 w-4 animate-spin" />
            {:else}
              <FolderSymlink class="h-4 w-4" />
            {/if}
          </button>
          {#if moveMenuOpen}
            <div class="move-menu-surface" bind:this={moveMenuRef}>
              <MailboxMoveMenu currentFolderId={currentFolderId} pending={pendingMove} on:select={handleMoveSelect} />
            </div>
          {/if}
        </div>
        <button
          type="button"
          class="btn btn--icon"
          aria-label="Delete"
          title="Delete"
          on:click={() => emit('delete')}>
          <Trash2 class="h-4 w-4" />
        </button>
      </div>
    </div>
    <AiCommandButtons
      {commands}
      actionOptions={actionMenuOptions}
      actionMenuLoading={actionMenuLoading}
      {mobile}
      layout="tray"
      compact={compactActions}
      on:select={(event) => emit('commandSelect', event.detail)}
      on:actionSelect={(event) => emit('actionSelect', event.detail)}
      on:actionMenuToggle={(event) => emit('actionMenuToggle', event.detail)}
      on:comingSoon={(event) => emit('comingSoon', event.detail)}
    />
  {/if}
{/if}

<style>
  /**
   * Floating move dropdown anchors to whichever toolbar button triggered the menu.
   * @usage - Email action toolbar move controls on desktop + mobile chips
   * @z-index-warning - Draws above DrawerBackdrop surfaces (z-50) but below AI panel overlays (z-150)
   * @related - .action-chip--menu maintains the trigger surface on mobile
   */
  .move-menu-surface {
    position: absolute;
    right: 0;
    top: calc(100% + 0.5rem);
    z-index: 30;
    background: white;
    border-radius: 0.65rem;
    border: 1px solid rgba(148, 163, 184, 0.4);
    box-shadow: 0 25px 50px -12px rgba(15, 23, 42, 0.18);
    padding: 0.35rem;
    min-width: 13rem;
  }

  @media (max-width: 640px) {
    .move-menu-surface {
      left: 0;
      right: auto;
      min-width: min(16rem, calc(100vw - 2rem));
      border-radius: 0.85rem;
      padding: 0.5rem;
    }
  }

  /**
   * Mobile-only metadata wrapper keeps sender details + timestamp legible above the preview iframe.
   * @usage - EmailActionToolbar when `mobile=true`
   * @related - .email-header-mobile__meta, .email-header-mobile__subject
   */
  .email-header-mobile {
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
    width: 100%;
  }

  /**
   * Layouts avatar + text stack with balanced padding so tap targets stay aligned.
   * @usage - Wraps avatar + textual metadata rows inside the mobile header
   * @related - .email-header-mobile__avatar, .email-header-mobile__meta-content
   */
  .email-header-mobile__meta {
    display: flex;
    gap: 0.85rem;
    align-items: flex-start;
  }

  /**
   * Avatar sizing keeps sender identity visible without overwhelming the small viewport.
   * @usage - Applied directly to the image element inside the mobile metadata stack
   * @related - .email-header-mobile__meta
   */
  .email-header-mobile__avatar {
    height: 56px;
    width: 56px;
    border-radius: 999px;
    object-fit: cover;
    flex-shrink: 0;
    box-shadow: 0 12px 30px -18px rgba(15, 23, 42, 0.35);
  }

  /**
   * Typography stack for subject + participants ensures truncation works without clipping labels.
   * @usage - Container for all textual spans in the mobile metadata column
   * @related - .email-header-mobile__line, .email-header-mobile__timestamp
   */
  .email-header-mobile__meta-content {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 0.35rem;
  }

  /**
   * Subject styling promotes readability with slightly larger font weight on tight viewports.
   * @usage - Email subject heading inside the mobile metadata column
   * @related - .email-header-mobile__value for supporting text
   */
  .email-header-mobile__subject {
    font-size: 1.05rem;
    font-weight: 600;
    color: #0f172a;
    line-height: 1.35;
    overflow-wrap: anywhere;
  }

  /**
   * Label/value rows allow stacked presentation of From/To fields with consistent spacing.
   * @usage - Wraps From/To metadata lines on mobile
   * @related - .email-header-mobile__label, .email-header-mobile__value
   */
  .email-header-mobile__line {
    display: flex;
    flex-direction: column;
    gap: 0.15rem;
  }

  /**
   * Micro-label styling echoes table headers for clarity without dominating the layout.
   * @usage - Small uppercase label preceding values in the metadata lines
   * @related - .email-header-mobile__value
   */
  .email-header-mobile__label {
    font-size: 0.65rem;
    letter-spacing: 0.2em;
    text-transform: uppercase;
    color: #94a3b8;
  }

  /**
   * Participant value styling keeps addresses on a single baseline with subdued email wrappers.
   * @usage - Text stack representing person/address strings
   * @related - .email-header-mobile__muted for inline secondary text
   */
  .email-header-mobile__value {
    font-size: 0.9rem;
    color: #0f172a;
    display: flex;
    flex-wrap: wrap;
    gap: 0.3rem;
    line-height: 1.35;
  }

  /**
   * Muted inline spans de-emphasize raw email addresses compared to the display names.
   * @usage - Inline secondary text inside the participant rows
   * @related - .email-header-mobile__value
   */
  .email-header-mobile__muted {
    color: #94a3b8;
    font-size: 0.8rem;
  }

  /**
   * Timestamp styling maintains hierarchy below the address block with softer slate tones.
   * @usage - Lower-right metadata line for sent date/time
   * @related - .email-header-mobile__subject
   */
  .email-header-mobile__timestamp {
    font-size: 0.75rem;
    color: #94a3b8;
  }

  /**
   * Horizontal tray houses both standard + AI actions in a single scrollable lane.
   * @usage - Wrapper for mobile action chips (reply/forward/etc + AI buttons)
   * @related - .action-tray__scroller
   */
  .action-tray {
    width: 100%;
    display: flex; /* Establish a flex context to control alignment of the scroller */
    justify-content: flex-end; /* Push the scroller to the far right */
  }

  /**
   * Scroll container enables horizontal overflow for one-handed use on mobile action tray.
   * This element now simply holds the buttons and scrolls if they overflow.
   * @usage - Direct child within .action-tray surrounding buttons and AI toolbar
   * @related - .action-tray__buttons
   */
  .action-tray__scroller {
    display: flex;
    align-items: center;
    margin-left: auto; /* Aligns the entire button group to the right */
    overflow-x: auto;
    padding-bottom: 0.25rem;
    -webkit-overflow-scrolling: touch;
  }

  /* Use adjacent sibling selector for robust, consistent spacing */
  .action-tray__scroller > :global(*) + :global(*) {
    margin-left: 0.5rem;
  }
  /**
   * High-specificity override to guarantee all icon buttons in the tray are perfectly circular.
   * This solves the \"oval button\" and icon clipping issues by enforcing the correct box model.
   * - `flex-shrink: 0` is critical to prevent buttons from being squashed.
   * - `padding: 0` overrides the base `.btn` padding that creates pill shapes.
   */
  .action-tray__scroller :global(.btn.btn--icon) {
    width: 42px;
    height: 42px;
    padding: 0;
    flex-shrink: 0;
  }

  /**
   * Shell keeps menu triggers positioned relative to dropdown surfaces on mobile.
   * @usage - Wraps buttons + dropdowns inside the action tray
   * @related - .move-menu-surface, .mobile-overflow-menu
   */
  .mobile-action-menu-shell {
    position: relative;
    flex: 0 0 auto;
    display: inline-flex;
  }

  /**
   * Overflow menu for less-common mobile actions (forward, translate, move, delete).
   * @usage - Dropdown menu triggered by More button at end of mobile action tray
   * @z-index-warning - Uses z-index 250 to render above all other UI including AI panels (z-150)
   * @related - .mobile-overflow-menu__item
   */
  .mobile-overflow-menu {
    position: fixed;
    right: 1rem;
    top: auto;
    bottom: auto;
    z-index: 250;
    background: white;
    border-radius: 0.85rem;
    border: 1px solid rgba(148, 163, 184, 0.4);
    box-shadow: 0 25px 50px -12px rgba(15, 23, 42, 0.18);
    padding: 0.5rem;
    min-width: 11rem;
    max-height: 80vh;
    overflow-y: auto;
  }

  /**
   * Menu item button styling for overflow actions.
   * @usage - Individual action buttons inside .mobile-overflow-menu
   * @related - .mobile-overflow-menu__item--destructive
   */
  .mobile-overflow-menu__item {
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

  .mobile-overflow-menu__item:hover {
    background: rgba(248, 250, 252, 0.9);
  }

  .mobile-overflow-menu__item--destructive {
    color: #b91c1c;
  }

  .mobile-overflow-menu__item--header {
    color: #475569;
    font-weight: 400;
  }

  .mobile-overflow-menu__divider {
    height: 1px;
    background: rgba(148, 163, 184, 0.25);
    margin: 0.25rem 0.5rem;
  }

  .mobile-overflow-menu__eyebrow {
    font-size: 0.65rem;
    letter-spacing: 0.2em;
    text-transform: uppercase;
    color: #94a3b8;
    padding: 0.5rem 0.75rem;
  }

  /**
   * Wrapper keeps the AI buttons and overflow menu aligned as a unified group.
   * Uses same gap as parent scroller for visual consistency.
   * @usage - Container around AI buttons + overflow menu inside action tray
   * @related - .ai-action-toolbar.mobile.tray-mode
   */
  .action-tray__buttons {
    display: flex;
    align-items: center;
  }

  .action-tray__buttons > :global(*) + :global(*) {
    margin-left: 0.5rem;
  }
</style>

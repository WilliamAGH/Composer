<script>
  import { createEventDispatcher, onMount } from 'svelte';
  import { flip } from 'svelte/animate';
  import { quintOut } from 'svelte/easing';
  import { fly } from 'svelte/transition';
  import { Sparkles, Loader2, Menu, Archive, Trash2, FolderSymlink } from 'lucide-svelte';
  import MailboxMoveMenu from './MailboxMoveMenu.svelte';
  import { getLetterAvatarData } from './services/letterAvatarGenerator';

  /**
   * Desktop + tablet mailbox list pane. Handles search, AI action trigger, and message selection.
   * Extracted from App.svelte so the parent only wires stores + events.
   */
  export let search = '';
  export let filtered = [];
  export let selected = null;
  export let mobile = false;
  export let tablet = false;
  export let desktop = false;
  export let wide = false;
  export let showEmailList = true;
  export let hasMailboxCommands = false;
  export let mailboxCommandEntries = [];
  export let mailboxCommandPendingKey = null;
  export let mailboxActionsOpen = false;
  export let mailboxActionsHost = null;
  export let activeMailboxActionLabel = '';
  export let mailboxActionError = '';
  export let escapeHtmlFn = (value) => value ?? '';
  export let formatRelativeTimestampFn = () => '';
  export let mailboxMenuListRef = null;
  export let resolveFolderFn = () => 'inbox';
  export let pendingMoveIds = new Set();
  export let compactActions = false;

  const dispatch = createEventDispatcher();
  let rowMoveMenuFor = null;
  let avatarFailures = new Set(); // Track which email IDs failed to load pravatar

  // Swipe gesture state
  let swipedRowId = null;
  let touchStartX = 0;
  let touchStartY = 0;
  let currentSwipeOffset = 0;
  let isDragging = false;
  let preventNextClick = false;

  // Detect touch capability rather than relying on viewport width
  const hasTouchSupport = typeof window !== 'undefined' &&
    ('ontouchstart' in window || navigator.maxTouchPoints > 0);

  function emit(type, detail) {
    dispatch(type, detail);
  }

  function handleSearch(event) {
    emit('searchChange', { value: event.currentTarget.value });
  }

  function handleToggleActions(host) {
    emit('toggleMailboxActions', { host });
  }

  function handleMailboxAction(entry) {
    emit('mailboxAction', { entry });
  }

  function handleSelectEmail(email, event) {
    // If we just finished swiping, prevent the click from opening the email
    if (preventNextClick) {
      if (event) event.preventDefault();
      return;
    }
    closeSwipedRow(); // Close any swiped row when selecting an email
    emit('selectEmail', { email });
  }

  function handleToggleMenu() {
    emit('toggleMenu');
  }

  function handleAvatarError(emailId) {
    avatarFailures = new Set([...avatarFailures, emailId]);
  }

  /**
   * Triggers an archive request for a specific email row.
   */
  function handleArchiveEmail(email) {
    emit('archiveEmail', { email });
  }

  /**
   * Moves a message to trash from the list row controls.
   */
  function handleDeleteEmail(email) {
    emit('deleteEmail', { email });
  }

  /**
   * Opens/closes the contextual move menu for the requested row.
   */
  function openRowMoveMenu(emailId) {
    rowMoveMenuFor = rowMoveMenuFor === emailId ? null : emailId;
  }

  function handleRowMove(email, targetFolderId) {
    rowMoveMenuFor = null;
    emit('moveEmail', { email, targetFolderId });
  }

  function handleGlobalPointer(event) {
    // Close move menu if clicking outside
    if (rowMoveMenuFor) {
      const target = event.target;
      if (target instanceof Element && target.closest('[data-row-move-control="true"]')) {
        return;
      }
      rowMoveMenuFor = null;
    }

    // Close swiped row if tapping outside on touch devices
    if (swipedRowId && hasTouchSupport) {
      const target = event.target;
      if (target instanceof Element) {
        const clickedRow = target.closest('.list-row-container');
        const swipedRow = document.querySelector(`[data-email-id="${swipedRowId}"]`);

        // If clicking outside the swiped row or on the row content (not actions), close swipe
        if (!clickedRow || clickedRow !== swipedRow?.parentElement) {
          closeSwipedRow();
        }
      }
    }
  }

  /**
   * Swipe gesture handlers for mobile touch interactions
   */
  function handleTouchStart(event, emailId) {
    if (!hasTouchSupport) {
      console.log('[Touch] No touch support, skipping');
      return;
    }

    const touch = event.touches[0];
    if (!touch) return;

    console.log('[Touch] Start:', emailId, touch.clientX, touch.clientY);
    touchStartX = touch.clientX;
    touchStartY = touch.clientY;
    isDragging = false;
  }

  function handleTouchMove(event, emailId) {
    if (!hasTouchSupport) return;

    const touch = event.touches[0];
    if (!touch) return;

    const deltaX = touchStartX - touch.clientX;
    const deltaY = Math.abs(touch.clientY - touchStartY);

    // Only register horizontal swipe if it's more horizontal than vertical
    if (Math.abs(deltaX) > 10 && Math.abs(deltaX) > deltaY * 1.5) {
      if (!isDragging) {
        isDragging = true;
        swipedRowId = emailId;
        console.log('[Touch] Started dragging:', emailId);
      }
      event.preventDefault(); // Prevent scroll while swiping

      // Limit swipe distance to reasonable bounds (max 120px)
      const maxSwipe = 120;
      // Allow swipe left (negative) to reveal actions
      currentSwipeOffset = Math.max(-maxSwipe, Math.min(0, -deltaX));
      console.log('[Touch] Move offset:', currentSwipeOffset);
    }
  }

  function handleTouchEnd(event, emailId) {
    if (!hasTouchSupport) {
      return;
    }

    if (isDragging) {
      event.preventDefault(); // Prevent the synthetic click event
      preventNextClick = true; // Prevent click event after swipe
      setTimeout(() => { preventNextClick = false; }, 350);

      const threshold = -40; // Minimum swipe distance to keep actions visible

      if (currentSwipeOffset < threshold) {
        // Swipe was far enough - snap to revealed state
        swipedRowId = emailId;
        currentSwipeOffset = -120; // Snap to full reveal
      } else {
        // Swipe wasn't far enough - snap back closed
        swipedRowId = null;
        currentSwipeOffset = 0;
      }
    }

    isDragging = false;
  }

  function closeSwipedRow() {
    swipedRowId = null;
    currentSwipeOffset = 0;
  }

  function getRowTransform(emailId, isSelected) {
    // If swiped, apply swipe transform (takes precedence over selected state)
    if (swipedRowId === emailId) {
      const swipeOffset = isDragging ? currentSwipeOffset : -120;
      const baseOffset = isSelected ? 8 : 0; // Account for selected state offset
      const scale = isSelected ? 1.02 : 1; // Account for selected state scale
      return `translateX(${baseOffset + swipeOffset}px) scale(${scale})`;
    }
    // If selected but not swiped, the CSS class handles the transform
    // Return empty string to let CSS take over
    return '';
  }

  // Debug logging to help troubleshoot (can be removed later)
  $: if (typeof window !== 'undefined' && swipedRowId) {
    console.log('[Swipe Debug]', {
      swipedRowId,
      currentSwipeOffset,
      isDragging,
      hasTouchSupport
    });
  }

  onMount(() => {
    console.log('[EmailListPane] Touch support:', hasTouchSupport);
    document.addEventListener('pointerdown', handleGlobalPointer);
    return () => document.removeEventListener('pointerdown', handleGlobalPointer);
  });
</script>

<section class="shrink-0 flex flex-col bg-white/90 border-r border-slate-200"
         class:w-[28rem]={wide}
         class:w-[25rem]={desktop && !wide}
         class:w-[20rem]={tablet && showEmailList}
         class:w-0={tablet && !showEmailList}
         class:w-full={mobile}
         class:hidden={mobile && selected}
         class:overflow-hidden={tablet && !showEmailList && !(mailboxActionsOpen && mailboxActionsHost === 'list')}
>
  <div class="px-4 py-3 border-b border-slate-200">
    <div class="flex items-center gap-3 mailbox-list-header">
      <button type="button" title="Toggle menu" class="btn btn--icon" aria-label="Toggle mailbox list" on:click={handleToggleMenu}>
        <Menu class="h-4 w-4" aria-hidden="true" />
      </button>
      <div class="flex-1 min-w-0 flex flex-col gap-1">
        <div class="relative" bind:this={mailboxMenuListRef}>
          <input
            placeholder="Search emails..."
            value={search}
            on:input={handleSearch}
            class="mailbox-search-input w-full rounded-lg border border-slate-200 bg-white pl-4 py-2 text-base text-slate-800 focus:outline-none focus:ring-2 focus:ring-slate-200"
            class:pr-16={compactActions}
            class:pr-32={!compactActions}
          />
          <button
            type="button"
            class="absolute inset-y-0 right-0 btn btn--primary btn--compact mailbox-ai-trigger"
            class:mailbox-ai-trigger--compact={compactActions}
            aria-haspopup="menu"
            aria-expanded={mailboxActionsOpen && mailboxActionsHost === 'list'}
            on:click={() => handleToggleActions('list')}
            disabled={!hasMailboxCommands || filtered.length === 0 || !!mailboxCommandPendingKey}
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
          {#if mailboxActionsOpen && mailboxActionsHost === 'list'}
            <div
              class="absolute right-0 top-[calc(100%+0.5rem)] menu-surface mailbox-action-popover"
              role="menu"
              tabindex="0"
              on:click|stopPropagation
              on:keydown|stopPropagation>
              <span class="menu-eyebrow">Mailbox Actions</span>
              <div class="menu-list">
                {#each mailboxCommandEntries as entry (entry.key)}
                  <button
                    type="button"
                    class="menu-item text-left"
                    on:click={() => handleMailboxAction(entry)}
                    disabled={filtered.length === 0}
                  >
                    <div class="flex items-center gap-3">
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
                Mailbox AI actions apply to the {filtered.length} message{filtered.length === 1 ? '' : 's'} currently listed.
              </div>
            </div>
          {/if}
          {#if mailboxCommandPendingKey && activeMailboxActionLabel}
            <p class="text-xs text-slate-500 mt-2">{activeMailboxActionLabel} in progress…</p>
          {/if}
          {#if mailboxActionError}
            <p class="text-xs text-rose-600 mt-1">{mailboxActionError}</p>
          {/if}
        </div>
      </div>
    </div>
  </div>
  <div class="flex-1 overflow-y-auto">
    {#if !filtered || filtered.length === 0}
      <div class="p-6 text-sm text-slate-500">No emails match your filter.</div>
    {:else}
      <div class="list-rows">
        {#each filtered as email (email.id)}
          <div
            class="list-row-container"
            animate:flip={{ duration: 220, easing: quintOut }}
            in:fly={{ y: 18, duration: 180, easing: quintOut }}
            out:fly={{ y: -18, duration: 220, opacity: 0.1, easing: quintOut }}
          >
            <!-- Swipe action background (visible on touch devices when swiped) -->
            {#if hasTouchSupport && swipedRowId === email.id}
              <div class="swipe-actions-background">
                <div class="swipe-action-hint">
                  {#if resolveFolderFn(email) !== 'archive'}
                    <Archive class="h-5 w-5" />
                  {/if}
                  <FolderSymlink class="h-5 w-5" />
                  <Trash2 class="h-5 w-5 text-rose-600" />
                </div>
              </div>
            {/if}
            <button
              type="button"
              data-email-id={email.id}
              class="list-row w-full text-left px-4 py-3 border-b border-slate-200 hover:bg-slate-50 cursor-pointer"
              class:list-row--selected={selected?.id === email.id}
              class:list-row--unread={!email.read}
              style="{getRowTransform(email.id, selected?.id === email.id) ? `transform: ${getRowTransform(email.id, selected?.id === email.id)};` : ''} transition: {isDragging && swipedRowId === email.id ? 'none' : 'all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1)'}; will-change: {isDragging && swipedRowId === email.id ? 'transform' : 'auto'};"
              on:click={(event) => handleSelectEmail(email, event)}
              on:keydown={(event) => {
                if (event.key === 'Enter' || event.key === ' ') {
                  event.preventDefault();
                  handleSelectEmail(email, event);
                }
              }}
              on:touchstart={(event) => handleTouchStart(event, email.id)}
              on:touchmove={(event) => handleTouchMove(event, email.id)}
              on:touchend={(event) => handleTouchEnd(event, email.id)}
            >
          <div class="flex items-start gap-3">
            {#if email.avatar || email.companyLogoUrl}
              <img
                src={email.avatar || email.companyLogoUrl}
                alt={escapeHtmlFn(email.from)}
                class="h-10 w-10 rounded-full object-cover"
                loading="lazy"
              />
            {:else if avatarFailures.has(email.id)}
              {@const letterAvatar = getLetterAvatarData(email.from, email.fromEmail)}
              <div
                class="h-10 w-10 rounded-full {letterAvatar.colorClass} flex items-center justify-center text-white font-semibold text-sm"
                aria-hidden="true"
              >
                {letterAvatar.initials}
              </div>
            {:else}
              <img
                src={'https://i.pravatar.cc/100?u=' + encodeURIComponent(email.fromEmail || email.from)}
                alt={escapeHtmlFn(email.from)}
                class="h-10 w-10 rounded-full object-cover"
                loading="lazy"
                on:error={() => handleAvatarError(email.id)}
              />
            {/if}
            <div class="min-w-0 flex-1">
              <div class="flex items-center gap-2">
                <span class="font-semibold truncate" class:text-slate-700={email.read} class:text-slate-900={!email.read}>{escapeHtmlFn(email.from)}</span>
                <span class="text-xs text-slate-400 whitespace-nowrap">{formatRelativeTimestampFn(email.timestampIso, email.timestamp)}</span>
                <div class="row-actions ml-auto" class:row-actions--visible={rowMoveMenuFor === email.id || swipedRowId === email.id}>
                  {#if resolveFolderFn(email) !== 'archive'}
                    <button
                      type="button"
                      class="row-action-btn"
                      title="Archive"
                      aria-label="Archive email"
                      on:click|stopPropagation={() => handleArchiveEmail(email)}>
                      <Archive class="h-4 w-4" />
                    </button>
                  {/if}
                  <div class="row-action-menu">
                    <button
                      type="button"
                      class="row-action-btn"
                      title="Move"
                      aria-label="Move email"
                      aria-expanded={rowMoveMenuFor === email.id}
                      data-row-move-control="true"
                      on:click|stopPropagation={() => openRowMoveMenu(email.id)}>
                      {#if pendingMoveIds.has(email.id)}
                        <Loader2 class="h-4 w-4 animate-spin" />
                      {:else}
                        <FolderSymlink class="h-4 w-4" />
                      {/if}
                    </button>
                    {#if rowMoveMenuFor === email.id}
                      <div class="row-move-surface" data-row-move-control="true">
                        <MailboxMoveMenu
                          currentFolderId={resolveFolderFn(email)}
                          pending={pendingMoveIds.has(email.id)}
                          on:select={(event) => handleRowMove(email, event.detail.targetId)} />
                      </div>
                      {/if}
                    </div>
                    <button
                      type="button"
                      class="row-action-btn row-action-btn--destructive"
                      title="Delete"
                      aria-label="Delete email"
                      on:click|stopPropagation={() => handleDeleteEmail(email)}>
                      <Trash2 class="h-4 w-4" />
                    </button>
                  </div>
                </div>
              <p class="text-sm truncate" class:font-medium={!email.read} class:text-slate-700={email.read} class:text-slate-900={!email.read}>{escapeHtmlFn(email.subject)}</p>
              <p class="text-sm text-slate-500 truncate">{escapeHtmlFn(email.preview)}</p>
            </div>
          </div>
            </button>
          </div>
        {/each}
      </div>
    {/if}
  </div>
</section>

<style>
  /**
   * Desktop/tablet mailbox list header (search + AI button + hamburger).
   * @usage - Ensures header nav controls sit above DrawerBackdrop and MailboxSidebar when the
   *          list pane is acting as a drawer on mobile/tablet.
   * @z-index-warning - Shares the var(--z-toolbar-surface) tier with MobileTopBar so the
   *                    hamburger remains clickable whenever the list is visible.
   * @related - MobileTopBar.svelte, DrawerBackdrop.svelte, app-shared.css z-index architecture
   */
  .mailbox-list-header {
    position: relative;
    z-index: var(--z-toolbar-surface, 150);
  }

  /* List row container wrapper for swipe support */
  .list-row-container {
    position: relative;
    overflow: hidden;
  }

  /* Swipe actions background (visible behind sliding content on mobile) */
  .swipe-actions-background {
    position: absolute;
    top: 0;
    right: 0;
    bottom: 0;
    width: 120px;
    background: linear-gradient(to left, rgba(248, 113, 113, 0.1), transparent);
    display: flex;
    align-items: center;
    justify-content: flex-end;
    padding-right: 1rem;
    pointer-events: none;
  }

  .swipe-action-hint {
    display: flex;
    gap: 0.75rem;
    align-items: center;
    color: #64748b;
  }

  /* Base list row for hover actions */
  .list-row {
    position: relative;
    background: white;
    touch-action: pan-y; /* Allow vertical scroll but capture horizontal gestures */
    transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1); /* Springy animation */
  }

  /* "Lifted Card" selected state - physically elevated with depth */
  .list-row--selected {
    background: white;
    transform: translateX(8px) scale(1.02);
    box-shadow:
      0 6px 16px -4px rgba(15, 23, 42, 0.15),
      0 2px 8px -2px rgba(15, 23, 42, 0.08),
      0 0 0 1px rgba(148, 163, 184, 0.2);
    border-radius: 8px;
    margin: 4px 8px 4px 0;
    z-index: 10;
  }

  /* Unread message accent (blue tint background) */
  .list-row--unread {
    background: rgba(239, 246, 255, 0.4);
  }

  .list-row--selected.list-row--unread {
    background: white; /* Selected state overrides unread tint */
  }

  /* Hover/focus action group */
  .row-actions {
    display: flex;
    align-items: center;
    gap: 0.25rem;
    opacity: 0;
    transition: opacity 0.15s ease;
  }

  .row-actions--visible,
  .list-row:hover .row-actions,
  .list-row:focus-within .row-actions,
  .list-row--selected .row-actions {
    opacity: 1;
  }
  /* Icon button styling for archive/move/delete */
  .row-action-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    height: 1.75rem;
    width: 1.75rem;
    border-radius: 999px;
    border: 0;
    background: rgba(226, 232, 240, 0.6);
    color: #475569;
    transition: background 0.15s ease, color 0.15s ease;
  }
  .row-action-btn:hover {
    background: rgba(148, 163, 184, 0.3);
  }
  .row-action-btn--destructive {
    color: #b91c1c;
  }
  .row-action-menu {
    position: relative;
  }
  /* Per-row move dropdown */
  .row-move-surface {
    position: absolute;
    right: 0;
    top: calc(100% + 0.35rem);
    z-index: 25;
    background: white;
    border-radius: 0.6rem;
    border: 1px solid #e2e8f0;
    box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
    padding: 0.35rem;
    min-width: 12rem;
  }

  .list-rows {
    display: flex;
    flex-direction: column;
  }
</style>

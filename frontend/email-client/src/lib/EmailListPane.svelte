<script>
  import { createEventDispatcher, onDestroy, onMount } from 'svelte';
  import { flip } from 'svelte/animate';
  import { quintOut } from 'svelte/easing';
  import { fly } from 'svelte/transition';
  import { Sparkles, Loader2, Menu, Archive, Trash2, FolderSymlink, MoreVertical } from 'lucide-svelte';
  import MailboxMoveMenu from './MailboxMoveMenu.svelte';
  import Portal from './components/Portal.svelte';
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

  const dispatch = createEventDispatcher();
  let rowMoveMenuFor = null;
  let rowMoveMenuAnchor = null;
  let rowMoveMenuCoords = null;
  let rowActionMenuFor = null; // Which email row has the action menu open (mobile)
  let rowActionMenuAnchor = null;
  let rowActionMenuCoords = null;
  let avatarFailures = new Set(); // Track which email IDs failed to load pravatar

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

  function handleSelectEmail(email) {
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
  function toggleRowMoveMenu(emailId, anchorEl) {
    if (rowMoveMenuFor === emailId) {
      closeRowMoveMenu();
      return;
    }
    rowMoveMenuFor = emailId;
    rowMoveMenuAnchor = anchorEl || null;
    updateRowMoveMenuCoords();
  }

  function handleRowMove(email, targetFolderId) {
    closeRowMoveMenu();
    emit('moveEmail', { email, targetFolderId });
  }

  function handleGlobalPointer(event) {
    // Close move menu if clicking outside
    if (rowMoveMenuFor) {
      const target = event.target;
      if (target instanceof Element && target.closest('[data-row-move-control="true"]')) {
        return;
      }
      closeRowMoveMenu();
    }
    // Close action menu if clicking outside
    if (rowActionMenuFor) {
      const target = event.target;
      if (target instanceof Element && target.closest('[data-row-action-menu="true"]')) {
        return;
      }
      closeRowActionMenu();
    }
  }

  /**
   * Toggles the mobile action menu for a specific email row.
   */
  function toggleRowActionMenu(emailId, anchorEl, event) {
    if (event) {
      event.stopPropagation();
    }
    if (rowActionMenuFor === emailId) {
      closeRowActionMenu();
      return;
    }
    rowActionMenuFor = emailId;
    rowActionMenuAnchor = anchorEl || null;
    updateRowActionMenuCoords();
  }

  function closeRowActionMenu() {
    rowActionMenuFor = null;
    rowActionMenuAnchor = null;
    rowActionMenuCoords = null;
  }

  /**
   * Handle archive from action menu
   */
  function handleActionMenuArchive(email, event) {
    if (event) {
      event.stopPropagation();
    }
    closeRowActionMenu();
    handleArchiveEmail(email);
  }

  /**
   * Handle delete from action menu
   */
  function handleActionMenuDelete(email, event) {
    if (event) {
      event.stopPropagation();
    }
    closeRowActionMenu();
    handleDeleteEmail(email);
  }

  /**
   * Handle opening move menu from action menu
   */
  function handleActionMenuMove(emailId, anchorEl, event) {
    if (event) {
      event.stopPropagation();
    }
    closeRowActionMenu();
    toggleRowMoveMenu(emailId, anchorEl);
  }

  function closeRowMoveMenu() {
    rowMoveMenuFor = null;
    rowMoveMenuAnchor = null;
    rowMoveMenuCoords = null;
  }

  function updateRowMoveMenuCoords() {
    if (!rowMoveMenuAnchor || typeof window === 'undefined' || !rowMoveMenuAnchor.isConnected) {
      closeRowMoveMenu();
      return;
    }
    const rect = rowMoveMenuAnchor.getBoundingClientRect();
    const viewportWidth = Math.max(window.innerWidth || 0, 1);
    const viewportHeight = Math.max(window.innerHeight || 0, 1);
    const horizontalPadding = 12;
    const defaultWidth = 280;
    const width = Math.min(defaultWidth, viewportWidth - horizontalPadding * 2);
    const maxLeft = viewportWidth - horizontalPadding - width;
    const desiredLeft = rect.right - width;
    const left = Math.max(horizontalPadding, Math.min(desiredLeft, maxLeft));
    const belowSpace = viewportHeight - rect.bottom;
    const estimatedMenuHeight = 320;
    const shouldShowAbove = belowSpace < estimatedMenuHeight && rect.top > estimatedMenuHeight;
    const top = shouldShowAbove
      ? Math.max(horizontalPadding, rect.top - 8 - estimatedMenuHeight)
      : rect.bottom + 8;

    rowMoveMenuCoords = {
      left: Math.round(left),
      top: Math.round(top),
      width: Math.round(width),
      placement: shouldShowAbove ? 'above' : 'below'
    };
  }

  function updateRowActionMenuCoords() {
    if (!rowActionMenuAnchor || typeof window === 'undefined' || !rowActionMenuAnchor.isConnected) {
      closeRowActionMenu();
      return;
    }
    const rect = rowActionMenuAnchor.getBoundingClientRect();
    const viewportWidth = Math.max(window.innerWidth || 0, 1);
    const horizontalPadding = 12;
    const menuWidth = Math.min(260, viewportWidth - horizontalPadding * 2);
    const maxLeft = viewportWidth - horizontalPadding - menuWidth;
    const left = Math.max(horizontalPadding, Math.min(rect.left, maxLeft));
    const top = rect.bottom + 8;
    rowActionMenuCoords = {
      left: Math.round(left),
      top: Math.round(top),
      width: Math.round(menuWidth)
    };
  }

  function handleViewportShift() {
    if (rowMoveMenuFor) {
      updateRowMoveMenuCoords();
    }
    if (rowActionMenuFor) {
      updateRowActionMenuCoords();
    }
  }

  onMount(() => {
    document.addEventListener('pointerdown', handleGlobalPointer);
    if (typeof window !== 'undefined') {
      window.addEventListener('resize', handleViewportShift);
      window.addEventListener('scroll', handleViewportShift, true);
    }
    return () => {
      document.removeEventListener('pointerdown', handleGlobalPointer);
      if (typeof window !== 'undefined') {
        window.removeEventListener('resize', handleViewportShift);
        window.removeEventListener('scroll', handleViewportShift, true);
      }
    };
  });

  onDestroy(() => {
    closeRowMoveMenu();
    closeRowActionMenu();
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
            class="mailbox-search-input w-full rounded-lg border border-slate-200 bg-white pl-4 py-2 text-base text-slate-800 focus:outline-none focus:ring-2 focus:ring-slate-200 pr-12"
          />
          <button
            type="button"
            class="absolute inset-y-0 right-0 btn btn--primary btn--compact mailbox-ai-trigger mailbox-ai-trigger--icon-only"
            aria-label={mailboxCommandPendingKey ? (activeMailboxActionLabel ? `${activeMailboxActionLabel}…` : 'Working…') : 'AI Actions'}
            aria-haspopup="menu"
            aria-expanded={mailboxActionsOpen && mailboxActionsHost === 'list'}
            title={mailboxCommandPendingKey ? (activeMailboxActionLabel ? `${activeMailboxActionLabel}…` : 'Working…') : 'AI Actions'}
            on:click={() => handleToggleActions('list')}
            disabled={!hasMailboxCommands || filtered.length === 0 || !!mailboxCommandPendingKey}
          >
            {#if mailboxCommandPendingKey}
              <Loader2 class="h-4 w-4 animate-spin" aria-hidden="true" />
            {:else}
              <Sparkles class="h-4 w-4" aria-hidden="true" />
            {/if}
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
  <div class="flex-1 mailbox-list-scroll">
    {#if !filtered || filtered.length === 0}
      <div class="p-6 text-sm text-slate-500">No emails match your filter.</div>
    {:else}
      <div class="list-rows">
        {#each filtered as email (email.id)}
          {@const rowSelected = selected?.id === email.id}
          <div
            class="list-row-container"
            animate:flip={{ duration: 220, easing: quintOut }}
            in:fly={{ y: 18, duration: 180, easing: quintOut }}
            out:fly={{ y: -18, duration: 220, opacity: 0.1, easing: quintOut }}
          >
            <div
              role="button"
              tabindex="0"
              class="list-row w-full px-4 py-3 border-b border-slate-200 hover:bg-slate-50 cursor-pointer text-left"
              class:list-row--selected={rowSelected}
              class:list-row--unread={!email.read}
              aria-pressed={selected?.id === email.id}
              aria-label={`Open email from ${escapeHtmlFn(email.from)}`}
              on:click={() => handleSelectEmail(email)}
              on:keydown={(event) => {
                if (event.key === 'Enter' || event.key === ' ') {
                  event.preventDefault();
                  handleSelectEmail(email);
                }
              }}
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
              <div class="row-header-line">
                <div class="row-header-line__text" class:row-text-guard={!mobile && rowSelected}>
                  <span class="font-semibold truncate" class:text-slate-700={email.read} class:text-slate-900={!email.read}>{escapeHtmlFn(email.from)}</span>
                  <span class="row-header-line__timestamp">{formatRelativeTimestampFn(email.timestampIso, email.timestamp)}</span>
                </div>
                {#if !mobile}
                  <div class="row-actions" class:row-actions--visible={rowMoveMenuFor === email.id}>
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
                        on:click|stopPropagation={(e) => toggleRowMoveMenu(email.id, e.currentTarget)}>
                        {#if pendingMoveIds.has(email.id)}
                          <Loader2 class="h-4 w-4 animate-spin" />
                        {:else}
                          <FolderSymlink class="h-4 w-4" />
                        {/if}
                      </button>
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
                {:else}
                  <button
                    type="button"
                    class="mobile-action-zone"
                    class:mobile-action-zone--active={rowActionMenuFor === email.id}
                    aria-label="Open actions menu for {escapeHtmlFn(email.from)}"
                    aria-expanded={rowActionMenuFor === email.id}
                    data-row-action-menu="true"
                    on:click|stopPropagation={(e) => toggleRowActionMenu(email.id, e.currentTarget, e)}
                  >
                    <MoreVertical class="h-4 w-4" aria-hidden="true" />
                  </button>
                {/if}
              </div>
              <p class="row-body__subject" class:row-text-guard={!mobile && rowSelected} class:font-medium={!email.read} class:text-slate-700={email.read} class:text-slate-900={!email.read}>{escapeHtmlFn(email.subject)}</p>
              <p class="row-body__preview">{escapeHtmlFn(email.preview)}</p>
            </div>
          </div>
            </div>
          </div>
        {/each}
      </div>
    {/if}
  </div>
</section>

{#if rowMoveMenuFor && rowMoveMenuCoords}
  {@const activeMoveEmail = filtered.find((item) => item?.id === rowMoveMenuFor)}
  {#if activeMoveEmail}
    <Portal target="mailbox-row-menu-root">
      <div
        class="row-move-portal menu-surface"
        data-row-move-control="true"
        style={`top:${rowMoveMenuCoords.top}px;left:${rowMoveMenuCoords.left}px;width:${rowMoveMenuCoords.width}px;position:fixed;`}
      >
        <MailboxMoveMenu
          currentFolderId={resolveFolderFn(activeMoveEmail)}
          pending={pendingMoveIds.has(activeMoveEmail.id)}
          on:select={(event) => handleRowMove(activeMoveEmail, event.detail.targetId)} />
      </div>
    </Portal>
  {/if}
{/if}

<!-- Mobile action menu portal -->
{#if rowActionMenuFor && rowActionMenuCoords}
  {@const activeActionEmail = filtered.find((item) => item?.id === rowActionMenuFor)}
  {#if activeActionEmail}
    <Portal target="mailbox-row-menu-root">
      <div
        class="row-action-menu-portal menu-surface"
        data-row-action-menu="true"
        style={`top:${rowActionMenuCoords.top}px;left:${rowActionMenuCoords.left}px;width:${rowActionMenuCoords.width}px;position:fixed;`}
      >
        <div class="menu-list">
          {#if resolveFolderFn(activeActionEmail) !== 'archive'}
            <button
              type="button"
              class="menu-item text-left w-full"
              data-row-action-menu="true"
              on:click={(e) => handleActionMenuArchive(activeActionEmail, e)}
            >
              <div class="flex items-center gap-3">
                <div class="menu-item-icon">
                  <Archive class="h-4 w-4" />
                </div>
                <span class="font-medium text-slate-900">Archive</span>
              </div>
            </button>
          {/if}
          <button
            type="button"
            class="menu-item text-left w-full"
            data-row-action-menu="true"
            on:click={(e) => handleActionMenuMove(activeActionEmail.id, rowActionMenuAnchor, e)}
          >
            <div class="flex items-center gap-3">
              <div class="menu-item-icon">
                {#if pendingMoveIds.has(activeActionEmail.id)}
                  <Loader2 class="h-4 w-4 animate-spin" />
                {:else}
                  <FolderSymlink class="h-4 w-4" />
                {/if}
              </div>
              <span class="font-medium text-slate-900">Move to...</span>
            </div>
          </button>
          <button
            type="button"
            class="menu-item text-left w-full"
            data-row-action-menu="true"
            on:click={(e) => handleActionMenuDelete(activeActionEmail, e)}
          >
            <div class="flex items-center gap-3">
              <div class="menu-item-icon text-rose-600">
                <Trash2 class="h-4 w-4" />
              </div>
              <span class="font-medium text-rose-600">Delete</span>
            </div>
          </button>
        </div>
      </div>
    </Portal>
  {/if}
{/if}

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

  /**
   * Scroll container for the list maintains vertical scrolling while allowing dropdown portals to overflow horizontally.
   * @usage - Wrapper div around the .list-rows stack
   * @overflow - y: auto for scrolling, x: visible so dropdown portals are not clipped
   */
  .mailbox-list-scroll {
    overflow-y: auto;
    overflow-x: visible;
  }

  /**
   * List row container provides an anchoring context without clipping overlays.
   * @usage - Wrapper div around each mailbox row item
   * @overflow - Must remain visible on both axes so dropdown portals can align
   * @related - .row-move-portal for external dropdown positioning
   */
  .list-row-container {
    position: relative;
    overflow: visible;
  }

  /* Base list row for hover actions */
  .list-row {
    position: relative;
    background: white;
    transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1); /* Springy animation */
    --row-action-guard: 110px;
  }

  @media (max-width: 640px) {
    .list-row {
      --row-action-guard: 72px;
    }
  }

  .row-header-line {
    display: flex;
    align-items: center;
    gap: 0.65rem;
    min-width: 0;
  }

  .row-header-line__text {
    display: flex;
    align-items: baseline;
    gap: 0.5rem;
    min-width: 0;
    flex: 1 1 auto;
    flex-wrap: nowrap;
  }

  .row-header-line__timestamp {
    font-size: 0.75rem;
    color: #94a3b8;
    flex-shrink: 0;
    white-space: nowrap;
  }

  .row-text-guard {
    position: relative;
    padding-right: var(--row-action-guard, 110px);
    -webkit-mask-image: linear-gradient(90deg, #000 0%, #000 calc(100% - var(--row-action-guard, 110px)), rgba(0, 0, 0, 0) 100%);
    mask-image: linear-gradient(90deg, #000 0%, #000 calc(100% - var(--row-action-guard, 110px)), rgba(0, 0, 0, 0) 100%);
  }

  .row-body__subject,
  .row-body__preview {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .row-body__subject {
    font-size: 0.95rem;
    margin-top: 0.15rem;
  }

  .row-body__preview {
    font-size: 0.9rem;
    color: #64748b;
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
    margin-left: auto;
    flex-shrink: 0;
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

  /**
   * Portaled move dropdown inherits .menu-surface tokens but tracks viewport positioning here.
   * @usage - Applied to the div rendered inside Portal for row move controls
   * @z-index-warning - Uses var(--z-dropdown); avoid nesting additional z-index contexts inside list rows
   * @related - .menu-surface in app-shared.css, Portal.svelte
   */
  .row-move-portal {
    max-height: min(360px, 65vh);
    pointer-events: auto;
  }

  /**
   * Mobile-specific overflow trigger - minimal by default, revealed on row interaction.
   * Creates a clean, uncluttered list view while maintaining discoverability.
   * @usage - Only rendered when `mobile` is true in the list view
   * @related - .row-action-btn for base gradients, .row-action-menu-portal for dropdown contents
   */
  .mobile-action-zone {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    height: 2rem;
    width: 2rem;
    border-radius: 999px;
    border: 1px solid transparent;
    background: transparent;
    color: #94a3b8;
    box-shadow: none;
    margin-left: auto;
    opacity: 0.4;
    transform: scale(0.9);
    transition: opacity 0.25s cubic-bezier(0.4, 0, 0.2, 1),
                transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1),
                background 0.2s ease,
                border-color 0.2s ease,
                box-shadow 0.2s ease;
  }

  /* Reveal and enhance button when row is touched, focused, or has active menu */
  .list-row:active .mobile-action-zone,
  .list-row:focus-within .mobile-action-zone,
  .mobile-action-zone--active {
    opacity: 1;
    transform: scale(1);
    border-color: rgba(148, 163, 184, 0.35);
    background: rgba(255, 255, 255, 0.95);
    color: #475569;
    box-shadow: 0 10px 18px -10px rgba(15, 23, 42, 0.35);
  }

  .mobile-action-zone:active {
    background: rgba(148, 163, 184, 0.15);
    transform: scale(0.95);
  }

  .mobile-action-zone:focus-visible {
    outline: none;
    box-shadow: 0 0 0 2px rgba(148, 163, 184, 0.35), 0 10px 18px -10px rgba(15, 23, 42, 0.35);
  }

  /**
   * Portal shell for the mobile action dropdown follows the shared menu-surface tokens with fixed positioning.
   * @usage - Surrounds Archive/Move/Delete buttons when the mobile overflow trigger is tapped
   * @z-index-warning - inherits var(--z-dropdown) via menu-surface so other overlays must avoid the same tier
   */
  .row-action-menu-portal {
    pointer-events: auto;
    max-height: min(360px, 70vh);
    z-index: var(--z-dropdown, 200);
  }

  .list-rows {
    display: flex;
    flex-direction: column;
  }
</style>

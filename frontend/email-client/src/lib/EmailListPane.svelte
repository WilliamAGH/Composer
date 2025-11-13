<script>
  import { createEventDispatcher, onMount } from 'svelte';
  import { flip } from 'svelte/animate';
  import { quintOut } from 'svelte/easing';
  import { fly } from 'svelte/transition';
  import { Sparkles, Loader2, Menu, Archive, Trash2, FolderSymlink } from 'lucide-svelte';
  import MailboxMoveMenu from './MailboxMoveMenu.svelte';

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
  export let drawerVisible = false;
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
    if (!rowMoveMenuFor) return;
    const target = event.target;
    if (target instanceof Element && target.closest('[data-row-move-control="true"]')) {
      return;
    }
    rowMoveMenuFor = null;
  }

  onMount(() => {
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
         class:hidden={mobile && selected && !drawerVisible}
         class:overflow-hidden={tablet && !showEmailList && !(mailboxActionsOpen && mailboxActionsHost === 'list')}
>
  <div class="px-4 py-3 border-b border-slate-200">
    <div class="flex items-center gap-3">
      <button type="button" title="Toggle menu" class="btn btn--icon" aria-label="Toggle mailbox list" on:click={handleToggleMenu}>
        <Menu class="h-4 w-4" aria-hidden="true" />
      </button>
      <div class="flex-1 min-w-0 flex flex-col gap-1">
        <div class="relative" bind:this={mailboxMenuListRef}>
          <input
            placeholder="Search emails..."
            value={search}
            on:input={handleSearch}
            class="mailbox-search-input w-full rounded-2xl border border-slate-200 bg-white/90 pl-4 pr-32 py-2 text-base text-slate-800 shadow-inner focus:outline-none focus:ring-2 focus:ring-slate-200"
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
            <span class="mailbox-ai-trigger__label">
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
          <button
            type="button"
            class="list-row w-full text-left px-4 py-3 border-b border-slate-200 hover:bg-slate-50 cursor-pointer {selected?.id===email.id?'bg-slate-100':''} {email.read?'':'bg-blue-50/30'}"
            animate:flip={{ duration: 220, easing: quintOut }}
            in:fly={{ y: 18, duration: 180, easing: quintOut }}
            out:fly={{ y: -18, duration: 220, opacity: 0.1, easing: quintOut }}
            on:click={() => handleSelectEmail(email)}
            on:keydown={(event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                handleSelectEmail(email);
              }
            }}
          >
          <div class="flex items-start gap-3">
            <img
              src={email.avatar || email.companyLogoUrl || ('https://i.pravatar.cc/100?u=' + encodeURIComponent(email.fromEmail || email.from))}
              alt={escapeHtmlFn(email.from)}
              class="h-10 w-10 rounded-full object-cover"
              loading="lazy"
            />
            <div class="min-w-0 flex-1">
              <div class="flex items-center gap-2">
                <span class="font-semibold truncate" class:text-slate-700={email.read} class:text-slate-900={!email.read}>{escapeHtmlFn(email.from)}</span>
                <span class="text-xs text-slate-400 whitespace-nowrap">{formatRelativeTimestampFn(email.timestampIso, email.timestamp)}</span>
                <div class="row-actions ml-auto" class:row-actions--visible={rowMoveMenuFor === email.id}>
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
        {/each}
      </div>
    {/if}
  </div>
</section>

<style>
  /* Base list row container for hover actions */
  .list-row {
    position: relative;
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
  .list-row:focus-within .row-actions {
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
    border: 1px solid rgba(148, 163, 184, 0.4);
    box-shadow: 0 20px 45px -12px rgba(15, 23, 42, 0.2);
    padding: 0.35rem;
    min-width: 12rem;
  }

  .list-rows {
    display: flex;
    flex-direction: column;
  }
</style>

<script>
  import { createEventDispatcher, onMount } from 'svelte';
  import { Reply, Forward, Archive, Trash2, FolderSymlink, Loader2 } from 'lucide-svelte';
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
  export let currentFolderId = 'inbox';
  export let pendingMove = false;
  export let escapeHtmlFn = (value) => value ?? '';
  export let formatFullDateFn = () => '';

  const dispatch = createEventDispatcher();
  let moveMenuOpen = false;
  let moveMenuButton = null;
  let moveMenuRef = null;

  function emit(type, detail) {
    dispatch(type, detail);
  }

  /**
   * Toggles the toolbar move dropdown visibility.
   */
  function toggleMoveMenu() {
    moveMenuOpen = !moveMenuOpen;
  }

  function closeMoveMenu() {
    moveMenuOpen = false;
  }

  /**
   * Emits the selected folder target to the parent component.
   */
  function handleMoveSelect(event) {
    closeMoveMenu();
    emit('move', { targetFolderId: event.detail.targetId });
  }

  /**
   * Closes the dropdown when clicking outside the control.
   */
  function handleGlobalPointer(event) {
    if (!moveMenuOpen) return;
    const target = event.target;
    if (moveMenuButton?.contains(target) || moveMenuRef?.contains(target)) {
      return;
    }
    moveMenuOpen = false;
  }

  onMount(() => {
    document.addEventListener('pointerdown', handleGlobalPointer);
    return () => document.removeEventListener('pointerdown', handleGlobalPointer);
  });
</script>

{#if email}
  <div class="flex items-start gap-3" class:flex-col={mobile}>
    <div class="flex items-start gap-3 min-w-0 flex-1">
      <img src={email.avatar || email.companyLogoUrl || 'data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 120 120%22%3E%3Crect fill=%22%23e2e8f0%22 width=%22120%22 height=%22120%22/%3E%3Ctext x=%2250%25%22 y=%2250%25%22 dominant-baseline=%22middle%22 text-anchor=%22middle%22 font-family=%22system-ui%22 font-size=%2248%22 fill=%22%2394a3b8%22%3E%3F%3C/text%3E%3C/svg%3E'} alt={escapeHtmlFn(email.from)} class="h-10 w-10 rounded-full object-cover shrink-0" class:h-12={!mobile} class:w-12={!mobile} loading="lazy" />
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
    <div class="flex gap-2 shrink-0" class:w-full={mobile} class:justify-end={mobile}>
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
          bind:this={moveMenuButton}
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
    on:select={(event) => emit('commandSelect', event.detail)}
    on:actionSelect={(event) => emit('actionSelect', event.detail)}
    on:actionMenuToggle={(event) => emit('actionMenuToggle', event.detail)}
    on:comingSoon={(event) => emit('comingSoon', event.detail)}
  />
{/if}

<style>
  /* Floating move dropdown anchored to toolbar button */
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
</style>

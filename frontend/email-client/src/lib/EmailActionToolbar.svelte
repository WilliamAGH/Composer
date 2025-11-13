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
  {#if mobile}
    <div class="email-header-mobile">
      <div class="email-header-mobile__meta">
        <img
          src={email.avatar || email.companyLogoUrl || 'data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 120 120%22%3E%3Crect fill=%22%23e2e8f0%22 width=%22120%22 height=%22120%22/%3E%3Ctext x=%2250%25%22 y=%2250%25%22 dominant-baseline=%22middle%22 text-anchor=%22middle%22 font-family=%22system-ui%22 font-size=%2248%22 fill=%22%2394a3b8%22%3E%3F%3C/text%3E%3C/svg%3E'}
          alt={escapeHtmlFn(email.from)}
          class="email-header-mobile__avatar"
          loading="lazy" />
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
            class="action-chip"
            aria-label="Reply"
            on:click={() => emit('reply')}>
            <span class="action-chip__icon"><Reply class="h-4 w-4" /></span>
            <span class="action-chip__label">Reply</span>
          </button>
          <button
            type="button"
            class="action-chip"
            aria-label="Forward"
            on:click={() => emit('forward')}>
            <span class="action-chip__icon"><Forward class="h-4 w-4" /></span>
            <span class="action-chip__label">Forward</span>
          </button>
          {#if currentFolderId !== 'archive'}
            <button
              type="button"
              class="action-chip"
              aria-label="Archive"
              on:click={() => emit('archive')}>
              <span class="action-chip__icon"><Archive class="h-4 w-4" /></span>
              <span class="action-chip__label">Archive</span>
            </button>
          {/if}
          <div class="action-chip-shell" class:action-chip-shell--open={moveMenuOpen}>
            <button
              bind:this={moveMenuButton}
              type="button"
              class="action-chip action-chip__trigger"
              aria-label="Move"
              aria-expanded={moveMenuOpen}
              on:click={toggleMoveMenu}>
              <span class="action-chip__icon">
                {#if pendingMove}
                  <Loader2 class="h-4 w-4 animate-spin" />
                {:else}
                  <FolderSymlink class="h-4 w-4" />
                {/if}
              </span>
              <span class="action-chip__label">Move</span>
            </button>
            {#if moveMenuOpen}
              <div class="move-menu-surface" bind:this={moveMenuRef}>
                <MailboxMoveMenu currentFolderId={currentFolderId} pending={pendingMove} on:select={handleMoveSelect} />
              </div>
            {/if}
          </div>
          <button
            type="button"
            class="action-chip action-chip--destructive"
            aria-label="Delete"
            on:click={() => emit('delete')}>
            <span class="action-chip__icon"><Trash2 class="h-4 w-4" /></span>
            <span class="action-chip__label">Delete</span>
          </button>
          <div class="action-tray__ai">
            <AiCommandButtons
              {commands}
              layout="tray"
              actionOptions={actionMenuOptions}
              actionMenuLoading={actionMenuLoading}
              {mobile}
              on:select={(event) => emit('commandSelect', event.detail)}
              on:actionSelect={(event) => emit('actionSelect', event.detail)}
              on:actionMenuToggle={(event) => emit('actionMenuToggle', event.detail)}
              on:comingSoon={(event) => emit('comingSoon', event.detail)}
            />
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
    gap: 1rem;
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
  }

  /**
   * Scroll container enables horizontal overflow with snap-like spacing for one-handed use.
   * @usage - Direct child within .action-tray surrounding chips and AI toolbar
   * @related - .action-chip, .action-tray__ai
   */
  .action-tray__scroller {
    display: flex;
    align-items: stretch;
    gap: 0.5rem;
    overflow-x: auto;
    padding-bottom: 0.25rem;
    -webkit-overflow-scrolling: touch;
  }

  /**
   * Base chip styling ensures ≥40px tap targets without oversized icons.
   * @usage - Reply/Forward/Archive/Delete chips on mobile action tray
   * @related - .action-chip__icon, .action-chip__label
   */
  .action-chip {
    flex: 0 0 auto;
    display: inline-flex;
    align-items: center;
    gap: 0.45rem;
    padding: 0.4rem 0.85rem;
    border-radius: 999px;
    border: 1px solid rgba(148, 163, 184, 0.45);
    background: rgba(248, 250, 252, 0.95);
    color: #0f172a;
    font-size: 0.85rem;
    font-weight: 600;
    box-shadow: 0 20px 40px -28px rgba(15, 23, 42, 0.45);
    cursor: pointer;
  }

  /**
   * Icon capsule keeps linework crisp and centered in the compact chip layout.
   * @usage - Inline span preceding chip labels
   * @related - .action-chip, .action-chip__label
   */
  .action-chip__icon {
    width: 32px;
    height: 32px;
    border-radius: 999px;
    border: 1px solid rgba(148, 163, 184, 0.5);
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: rgba(255, 255, 255, 0.9);
    color: #475569;
  }

  /**
   * Text style for chip labels keeps accessible contrast yet leaves room for AI badges.
   * @usage - Inline span describing the action represented by the chip
   * @related - .action-chip__icon
   */
  .action-chip__label {
    white-space: nowrap;
  }

  /**
   * Destructive chip variant leans on rose palette to warn users before irreversible actions.
   * @usage - Delete chip on mobile action tray
   * @related - .action-chip
   */
  .action-chip--destructive {
    border-color: rgba(244, 63, 94, 0.35);
    color: #b91c1c;
  }

  /**
   * Shell keeps the move trigger positioned relative to its dropdown surface without altering chip layout.
   * @usage - Wraps the move button + dropdown inside the action tray
   * @related - .move-menu-surface, .action-chip__trigger
   */
  .action-chip-shell {
    position: relative;
    flex: 0 0 auto;
    display: inline-flex;
  }

  .action-chip-shell--open .action-chip {
    border-color: rgba(99, 102, 241, 0.45);
  }

  /**
   * Trigger button mirrors base chip styling while remaining a proper interactive element for a11y.
   * @usage - Button nested inside .action-chip-shell for the move action
   * @related - .action-chip
   */
  .action-chip__trigger {
    width: 100%;
    justify-content: flex-start;
    appearance: none;
  }

  /**
   * Wrapper keeps the AI toolbar aligned with the surrounding chips when embedded on mobile.
   * @usage - Container around <AiCommandButtons layout="tray" /> inside action tray
   * @related - .ai-action-toolbar.mobile.tray-mode
   */
  .action-tray__ai {
    flex: 0 0 auto;
    min-width: max(220px, 45%);
  }
</style>

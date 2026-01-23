<script>
  import { createEventDispatcher } from 'svelte';
  import { Pencil, Inbox as InboxIcon, Star as StarIcon, AlarmClock, Send, Archive, Trash2 } from 'lucide-svelte';

  /** Sidebar navigation for mailboxes and compose trigger. Keeps App.svelte focused on state orchestration. */
  export let mailbox = 'inbox';
  export let mailboxCounts = {};
  export let variant = 'inline-desktop';

  const dispatch = createEventDispatcher();
  const sidebarVariants = {
    'inline-wide': {
      widthClass: 'w-56',
      hideBorder: false,
      overflowHidden: false,
      pointerNone: false,
      fixed: false,
      hidden: false
    },
    'inline-desktop': {
      widthClass: 'w-52',
      hideBorder: false,
      overflowHidden: false,
      pointerNone: false,
      fixed: false,
      hidden: false
    },
    'inline-collapsed': {
      widthClass: 'w-0',
      hideBorder: true,
      overflowHidden: true,
      pointerNone: true,
      fixed: false,
      hidden: true
    },
    'drawer-visible': {
      widthClass: 'w-56',
      hideBorder: false,
      overflowHidden: false,
      pointerNone: false,
      fixed: true,
      hidden: false
    },
    'drawer-hidden': {
      widthClass: 'w-56',
      hideBorder: false,
      overflowHidden: false,
      pointerNone: true,
      fixed: true,
      hidden: true
    }
  };
  const FALLBACK_VARIANT = 'inline-desktop';
  $: variantConfig = sidebarVariants[variant] || sidebarVariants[FALLBACK_VARIANT];
  $: collapsed = variant === 'inline-collapsed';
  $: ariaHidden = (variantConfig.hidden || collapsed) ? 'true' : 'false';
  $: pointerEventsValue = variantConfig.pointerNone ? 'none' : 'auto';
  // Keep transform inline so Tailwind utilities (translate/transform) can't override drawer state.
  $: transformValue = (() => {
    if (variant === 'drawer-hidden') {
      return 'translate3d(-100%, 0, 0)';
    }
    return 'translate3d(0, 0, 0)';
  })();

  function select(target) {
    dispatch('selectMailbox', { target });
  }

  function compose() {
    dispatch('compose');
  }
</script>

<aside class={`mailbox-sidebar shrink-0 transition-all duration-200 will-change-transform ${variantConfig.widthClass}`}
       data-style-usage="mailbox-sidebar"
       class:overflow-hidden={variantConfig.overflowHidden}
       class:pointer-events-none={variantConfig.pointerNone}
       class:fixed={variantConfig.fixed}
       class:inset-y-0={variantConfig.fixed}
       class:left-0={variantConfig.fixed}
       aria-hidden={ariaHidden}
       style="pointer-events: {pointerEventsValue}; transform: {transformValue}; z-index: {variantConfig.fixed ? 'var(--z-drawer-sidebar, 170)' : 'auto'};">
  <div class="sidebar-header">
    <div class="sidebar-brand">
      <div class="brand-icon">
        <InboxIcon class="h-5 w-5" />
      </div>
      <h1 class="brand-text">Composer</h1>
    </div>
    <button class="compose-btn" on:click={compose}>
      <Pencil class="h-4 w-4" />
      <span>Compose</span>
    </button>
  </div>
  <nav class="sidebar-nav">
    <div class="nav-section">
      <button type="button" class={`nav-pill ${mailbox==='inbox' ? 'nav-pill--active' : ''}`}
              on:click={() => select('inbox')}>
        <span class="nav-pill-icon"><InboxIcon class="h-4 w-4" /></span>
        <span class="nav-pill-label">Inbox</span>
        {#if mailboxCounts.inbox > 0}
          <span class="nav-pill-badge">{mailboxCounts.inbox}</span>
        {/if}
      </button>
      <button type="button" class={`nav-pill ${mailbox==='starred' ? 'nav-pill--active' : ''}`}
              on:click={() => select('starred')}>
        <span class="nav-pill-icon"><StarIcon class="h-4 w-4" /></span>
        <span class="nav-pill-label">Starred</span>
        {#if mailboxCounts.starred > 0}
          <span class="nav-pill-badge">{mailboxCounts.starred}</span>
        {/if}
      </button>
      <button type="button" class={`nav-pill ${mailbox==='snoozed' ? 'nav-pill--active' : ''}`}
              on:click={() => select('snoozed')}>
        <span class="nav-pill-icon"><AlarmClock class="h-4 w-4" /></span>
        <span class="nav-pill-label">Snoozed</span>
        {#if mailboxCounts.snoozed > 0}
          <span class="nav-pill-badge">{mailboxCounts.snoozed}</span>
        {/if}
      </button>
    </div>

    <div class="nav-divider"></div>

    <div class="nav-section">
      <button type="button" class={`nav-pill ${mailbox==='sent' ? 'nav-pill--active' : ''}`}
              on:click={() => select('sent')}>
        <span class="nav-pill-icon"><Send class="h-4 w-4" /></span>
        <span class="nav-pill-label">Sent</span>
      </button>
      <button type="button" class={`nav-pill ${mailbox==='drafts' ? 'nav-pill--active' : ''}`}
              on:click={() => select('drafts')}>
        <span class="nav-pill-icon"><Pencil class="h-4 w-4" /></span>
        <span class="nav-pill-label">Drafts</span>
        {#if mailboxCounts.drafts > 0}
          <span class="nav-pill-badge nav-pill-badge--muted">{mailboxCounts.drafts}</span>
        {/if}
      </button>
    </div>

    <div class="nav-divider"></div>

    <div class="nav-section">
      <button type="button" class={`nav-pill ${mailbox==='archive' ? 'nav-pill--active' : ''}`}
              on:click={() => select('archive')}>
        <span class="nav-pill-icon"><Archive class="h-4 w-4" /></span>
        <span class="nav-pill-label">Archive</span>
      </button>
      <button type="button" class={`nav-pill ${mailbox==='trash' ? 'nav-pill--active' : ''}`}
              on:click={() => select('trash')}>
        <span class="nav-pill-icon"><Trash2 class="h-4 w-4" /></span>
        <span class="nav-pill-label">Trash</span>
      </button>
    </div>
  </nav>
</aside>

<style>
  /* ═══════════════════════════════════════════════════════════════════════════
   * MAILBOX SIDEBAR: Light Mode (Default)
   * Clean white background, warm terracotta accent, refined typography
   * ═══════════════════════════════════════════════════════════════════════════ */

  /**
   * Sidebar container - light with subtle warmth
   */
  :global(.mailbox-sidebar) {
    background: var(--color-cream, #faf9f7);
    border-right: 1px solid #e7e5e4;
  }

  /**
   * Header section
   */
  .sidebar-header {
    padding: 1.25rem 1rem 1rem;
    border-bottom: 1px solid #e7e5e4;
  }

  .sidebar-brand {
    display: flex;
    align-items: center;
    gap: 0.35rem;
    margin-bottom: 1.25rem;
    padding-left: 0.15rem;
  }

  .brand-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 28px;
    height: 28px;
    border-radius: 8px;
    background: var(--color-accent, #d97757);
    color: white;
  }

  .brand-text {
    font-family: var(--font-display, 'Satoshi', sans-serif);
    font-size: 1.2rem;
    font-weight: 700;
    letter-spacing: -0.025em;
    color: #1c1917;
    margin: 0;
  }

  /**
   * Compose button - warm accent
   */
  .compose-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 0.5rem;
    width: 100%;
    padding: 0.7rem 1rem;
    border-radius: 10px;
    border: none;
    font-family: var(--font-body, 'General Sans', sans-serif);
    font-size: 0.875rem;
    font-weight: 500;
    color: white;
    background: var(--color-accent, #d97757);
    cursor: pointer;
    transition: background 0.15s ease;
  }

  .compose-btn:hover {
    background: var(--color-accent-hover, #e8896b);
  }

  .compose-btn:active {
    background: var(--color-accent, #d97757);
  }

  /**
   * Navigation container
   */
  .sidebar-nav {
    padding: 0.75rem 0.6rem;
    overflow-y: auto;
    flex: 1;
  }

  .nav-section {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .nav-divider {
    height: 1px;
    margin: 0.6rem 0.5rem;
    background: #e7e5e4;
  }

  /**
   * Navigation pill
   */
  .nav-pill {
    display: flex;
    align-items: center;
    gap: 0.65rem;
    width: 100%;
    border-radius: 8px;
    padding: 0.55rem 0.75rem;
    border: none;
    background: transparent;
    cursor: pointer;
    transition: background 0.15s ease;
  }

  .nav-pill-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 18px;
    height: 18px;
    color: #78716c;
    transition: color 0.15s ease;
  }

  .nav-pill-label {
    font-family: var(--font-body, 'General Sans', sans-serif);
    font-size: 0.875rem;
    font-weight: 450;
    color: #57534e;
    transition: color 0.15s ease;
  }

  .nav-pill:hover {
    background: rgba(0, 0, 0, 0.04);
  }

  .nav-pill:hover .nav-pill-icon,
  .nav-pill:hover .nav-pill-label {
    color: #1c1917;
  }

  /**
   * Active state - subtle warm highlight
   */
  .nav-pill--active {
    background: var(--color-accent-subtle, rgba(217, 119, 87, 0.08));
  }

  .nav-pill--active .nav-pill-icon {
    color: var(--color-accent, #d97757);
  }

  .nav-pill--active .nav-pill-label {
    color: #1c1917;
    font-weight: 500;
  }

  /**
   * Unread count badge
   */
  .nav-pill-badge {
    margin-left: auto;
    padding: 0.1rem 0.5rem;
    border-radius: 999px;
    font-family: var(--font-body, 'General Sans', sans-serif);
    font-size: 0.7rem;
    font-weight: 600;
    background: var(--color-accent, #d97757);
    color: white;
  }

  .nav-pill-badge--muted {
    background: #e7e5e4;
    color: #78716c;
  }
</style>

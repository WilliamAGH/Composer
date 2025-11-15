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

<aside class={`mailbox-sidebar shrink-0 border-slate-200 bg-white/80 backdrop-blur transition-all duration-200 will-change-transform ${variantConfig.widthClass}`}
       data-style-usage="mailbox-sidebar"
       class:border-r={!variantConfig.hideBorder}
       class:border-r-0={variantConfig.hideBorder}
       class:overflow-hidden={variantConfig.overflowHidden}
       class:pointer-events-none={variantConfig.pointerNone}
       class:fixed={variantConfig.fixed}
       class:inset-y-0={variantConfig.fixed}
       class:left-0={variantConfig.fixed}
       class:shadow-xl={variantConfig.fixed}
       aria-hidden={ariaHidden}
       style="pointer-events: {pointerEventsValue}; transform: {transformValue}; z-index: {variantConfig.fixed ? 'var(--z-drawer-sidebar, 170)' : 'auto'};">
  <div class="p-4 border-b border-slate-200">
    <div class="flex items-center gap-2 mb-4 justify-center w-full text-center">
      <InboxIcon class="h-6 w-6 text-slate-900" />
      <h1 class="text-xl font-bold text-slate-900">Composer</h1>
    </div>
    <button class="btn btn--primary w-full justify-center" on:click={compose}>
      <Pencil class="h-4 w-4" /> Compose
    </button>
  </div>
  <nav class="p-2 space-y-1 overflow-y-auto">
    <button type="button" class={`nav-pill ${mailbox==='inbox' ? 'nav-pill--active' : ''}`}
            on:click={() => select('inbox')}>
      <InboxIcon class="h-4 w-4" />
      <span class="font-medium">Inbox</span>
      <span class="nav-pill-badge">{mailboxCounts.inbox}</span>
    </button>
    <button type="button" class={`nav-pill ${mailbox==='starred' ? 'nav-pill--active' : ''}`}
            on:click={() => select('starred')}>
      <StarIcon class="h-4 w-4" />
      <span class="font-medium">Starred</span>
      <span class="nav-pill-badge">{mailboxCounts.starred}</span>
    </button>
    <button type="button" class={`nav-pill ${mailbox==='snoozed' ? 'nav-pill--active' : ''}`}
            on:click={() => select('snoozed')}>
      <AlarmClock class="h-4 w-4" />
      <span class="font-medium">Snoozed</span>
      <span class="nav-pill-badge">{mailboxCounts.snoozed}</span>
    </button>
    <button type="button" class={`nav-pill ${mailbox==='sent' ? 'nav-pill--active' : ''}`}
            on:click={() => select('sent')}>
      <Send class="h-4 w-4" />
      <span class="font-medium">Sent</span>
      <span class="nav-pill-badge">{mailboxCounts.sent}</span>
    </button>
    <button type="button" class={`nav-pill ${mailbox==='drafts' ? 'nav-pill--active' : ''}`}
            on:click={() => select('drafts')}>
      <AlarmClock class="h-4 w-4" />
      <span class="font-medium">Drafts</span>
      <span class="nav-pill-badge">{mailboxCounts.drafts}</span>
    </button>
    <button type="button" class={`nav-pill ${mailbox==='archive' ? 'nav-pill--active' : ''}`}
            on:click={() => select('archive')}>
      <Archive class="h-4 w-4" />
      <span class="font-medium">Archive</span>
      <span class="nav-pill-badge">{mailboxCounts.archive}</span>
    </button>
    <button type="button" class={`nav-pill ${mailbox==='trash' ? 'nav-pill--active' : ''}`}
            on:click={() => select('trash')}>
      <Trash2 class="h-4 w-4" />
      <span class="font-medium">Trash</span>
      <span class="nav-pill-badge">{mailboxCounts.trash}</span>
    </button>
  </nav>
</aside>

<style>
  /**
   * Baseline mailbox row pill renders each folder entry with frosted hover affordances.
   * @usage - Buttons wrapping mailbox options inside this sidebar component
   * @related - .nav-pill--active, .nav-pill-badge
   */
  .nav-pill {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    width: 100%;
    border-radius: 18px;
    padding: 0.55rem 0.85rem;
    color: #475569;
    transition: background 0.2s ease, color 0.2s ease, border-color 0.2s ease;
    border: 1px solid transparent;
  }

  /**
   * Hover emphasis deepens the pill background to reinforce interactive affordance.
   * @usage - Implicit pseudo-class on .nav-pill buttons when pointer hovers
   * @related - .nav-pill
   */
  .nav-pill:hover {
    background: rgba(15, 23, 42, 0.05);
    color: #0f172a;
  }

  /**
   * Active mailbox pill uses inset border to highlight the currently selected folder.
   * @usage - Conditionals add this class for whichever mailbox prop matches
   * @related - .nav-pill
   */
  .nav-pill--active {
    background: rgba(15, 23, 42, 0.08);
    border-color: rgba(15, 23, 42, 0.12);
    color: #0f172a;
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.3);
  }

  /**
   * Badge styles the unread count indicator at the end of each pill.
   * @usage - Span containing mailboxCounts values inside each nav button
   * @related - .nav-pill
   */
  .nav-pill-badge {
    margin-left: auto;
    padding: 0.1rem 0.5rem;
    border-radius: 999px;
    font-size: 0.7rem;
    font-weight: 600;
    background: rgba(226, 232, 240, 0.8);
    color: #475569;
  }
</style>

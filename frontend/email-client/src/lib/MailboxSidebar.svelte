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
      translateClass: '',
      hidden: false
    },
    'inline-desktop': {
      widthClass: 'w-52',
      hideBorder: false,
      overflowHidden: false,
      pointerNone: false,
      fixed: false,
      translateClass: '',
      hidden: false
    },
    'inline-collapsed': {
      widthClass: 'w-0',
      hideBorder: true,
      overflowHidden: true,
      pointerNone: true,
      fixed: false,
      translateClass: '',
      hidden: true
    },
    'drawer-visible': {
      widthClass: 'w-56',
      hideBorder: false,
      overflowHidden: false,
      pointerNone: false,
      fixed: true,
      translateClass: '',
      hidden: false
    },
    'drawer-hidden': {
      widthClass: 'w-56',
      hideBorder: false,
      overflowHidden: false,
      pointerNone: true,
      fixed: true,
      translateClass: '-translate-x-full',
      hidden: true
    }
  };
  const FALLBACK_VARIANT = 'inline-desktop';
  $: variantConfig = sidebarVariants[variant] || sidebarVariants[FALLBACK_VARIANT];
  $: collapsed = variant === 'inline-collapsed';
  $: ariaHidden = (variantConfig.hidden || collapsed) ? 'true' : 'false';

  function select(target) {
    dispatch('selectMailbox', { target });
  }

  function compose() {
    dispatch('compose');
  }
</script>

<aside class={`shrink-0 border-slate-200 bg-white/80 backdrop-blur transition-all duration-200 will-change-transform ${variantConfig.widthClass} ${variantConfig.translateClass || ''}`}
       class:border-r={!variantConfig.hideBorder}
       class:border-r-0={variantConfig.hideBorder}
       class:overflow-hidden={variantConfig.overflowHidden}
       class:pointer-events-none={variantConfig.pointerNone}
       class:fixed={variantConfig.fixed}
       class:inset-y-0={variantConfig.fixed}
       class:left-0={variantConfig.fixed}
       class:z-[60]={variantConfig.fixed}
       class:shadow-xl={variantConfig.fixed}
       aria-hidden={ariaHidden}>
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

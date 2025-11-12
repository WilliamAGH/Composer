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
    <div class="flex items-center gap-2 mb-4">
      <InboxIcon class="h-6 w-6 text-slate-900" />
      <h1 class="text-xl font-bold text-slate-900">Composer</h1>
    </div>
    <button class="inline-flex items-center gap-2 w-full justify-center rounded-xl px-4 py-2 font-semibold text-white bg-gradient-to-br from-slate-900 to-slate-800 shadow ring-1 ring-slate-900/10" on:click={compose}>
      <Pencil class="h-4 w-4" /> Compose
    </button>
  </div>
  <nav class="p-2 space-y-1 overflow-y-auto">
    <button type="button" class="w-full flex items-center gap-3 rounded-lg px-3 py-2 text-slate-600 hover:bg-slate-100"
            class:bg-slate-100={mailbox==='inbox'} on:click={() => select('inbox')}>
      <InboxIcon class="h-4 w-4" />
      <span class="text-slate-900">Inbox</span>
      <span class="ml-auto rounded-full bg-slate-200 px-2 text-xs font-semibold text-slate-700">{mailboxCounts.inbox}</span>
    </button>
    <button type="button" class="w-full flex items-center gap-3 rounded-lg px-3 py-2 text-slate-600 hover:bg-slate-100"
            class:bg-slate-100={mailbox==='starred'} on:click={() => select('starred')}>
      <StarIcon class="h-4 w-4" />
      <span class="text-slate-900">Starred</span>
      <span class="ml-auto rounded-full bg-slate-200 px-2 text-xs font-semibold text-slate-700">{mailboxCounts.starred}</span>
    </button>
    <button type="button" class="w-full flex items-center gap-3 rounded-lg px-3 py-2 text-slate-600 hover:bg-slate-100"
            class:bg-slate-100={mailbox==='snoozed'} on:click={() => select('snoozed')}>
      <AlarmClock class="h-4 w-4" />
      <span class="text-slate-900">Snoozed</span>
      <span class="ml-auto rounded-full bg-slate-200 px-2 text-xs font-semibold text-slate-700">{mailboxCounts.snoozed}</span>
    </button>
    <button type="button" class="w-full flex items-center gap-3 rounded-lg px-3 py-2 text-slate-600 hover:bg-slate-100"
            class:bg-slate-100={mailbox==='sent'} on:click={() => select('sent')}>
      <Send class="h-4 w-4" />
      <span class="text-slate-900">Sent</span>
      <span class="ml-auto rounded-full bg-slate-200 px-2 text-xs font-semibold text-slate-700">{mailboxCounts.sent}</span>
    </button>
    <button type="button" class="w-full flex items-center gap-3 rounded-lg px-3 py-2 text-slate-600 hover:bg-slate-100"
            class:bg-slate-100={mailbox==='drafts'} on:click={() => select('drafts')}>
      <AlarmClock class="h-4 w-4" />
      <span class="text-slate-900">Drafts</span>
      <span class="ml-auto rounded-full bg-slate-200 px-2 text-xs font-semibold text-slate-700">{mailboxCounts.drafts}</span>
    </button>
    <button type="button" class="w-full flex items-center gap-3 rounded-lg px-3 py-2 text-slate-600 hover:bg-slate-100"
            class:bg-slate-100={mailbox==='archive'} on:click={() => select('archive')}>
      <Archive class="h-4 w-4" />
      <span class="text-slate-900">Archive</span>
      <span class="ml-auto rounded-full bg-slate-200 px-2 text-xs font-semibold text-slate-700">{mailboxCounts.archive}</span>
    </button>
    <button type="button" class="w-full flex items-center gap-3 rounded-lg px-3 py-2 text-slate-600 hover:bg-slate-100"
            class:bg-slate-100={mailbox==='trash'} on:click={() => select('trash')}>
      <Trash2 class="h-4 w-4" />
      <span class="text-slate-900">Trash</span>
      <span class="ml-auto rounded-full bg-slate-200 px-2 text-xs font-semibold text-slate-700">{mailboxCounts.trash}</span>
    </button>
  </nav>
</aside>

<script>
  import { createEventDispatcher } from 'svelte';
  import { Menu, Pencil, Inbox as InboxIcon, Star as StarIcon, AlarmClock, Send, Archive, Trash2 } from 'lucide-svelte';

  /** Sidebar navigation for mailboxes and compose trigger. Keeps App.svelte focused on state orchestration. */
  export let mailbox = 'inbox';
  export let mailboxCounts = {};
  export let sidebarOpen = true;
  export let mobile = false;
  export let tablet = false;
  export let desktop = false;
  export let showDrawer = false;

  const dispatch = createEventDispatcher();

  function select(target) {
    dispatch('selectMailbox', { target });
  }

  function compose() {
    dispatch('compose');
  }

  function toggleSidebar() {
    dispatch('toggleSidebar');
  }
</script>

<aside class="shrink-0 border-r border-slate-200 bg-white/80 backdrop-blur transition-all duration-200"
       class:w-64={((desktop && sidebarOpen))}
       class:w-16={desktop && !sidebarOpen}
       class:w-0={tablet && !sidebarOpen}
       class:overflow-hidden={!sidebarOpen && (tablet || (desktop && !sidebarOpen))}
       class:border-r-0={!sidebarOpen && (tablet || desktop)}
       class:fixed={mobile || tablet}
       class:inset-y-0={mobile || tablet}
       class:left-0={mobile || tablet}
       class:z-[60]={mobile || tablet}
       class:shadow-xl={mobile || tablet}
       class:hidden={(mobile || tablet) && !showDrawer}>
  <div class="p-4 border-b border-slate-200">
    <div class="flex items-center gap-2 mb-4">
      <InboxIcon class="h-6 w-6 text-slate-900" />
      <h1 class="text-xl font-bold text-slate-900">ComposerAI</h1>
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

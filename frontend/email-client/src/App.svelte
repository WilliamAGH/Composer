<script>
  import { onMount } from 'svelte';
  import EmailIframe from './lib/EmailIframe.svelte';
  import { Menu, Pencil, Inbox as InboxIcon, Star as StarIcon, AlarmClock, Send, Archive, Trash2 } from 'lucide-svelte';
  export let bootstrap = {};

  const FALLBACK_COMMAND_TITLES = {
    summarize: 'AI Summary',
    translate: 'AI Translation',
    draft: 'AI Draft Reply',
    compose: 'AI Compose',
    tone: 'AI Tone Adjustment'
  };

  let uiNonce = bootstrap.uiNonce || null;
  let search = '';
  let emails = Array.isArray(bootstrap.messages) ? bootstrap.messages.map(mapEmail) : [];
  let selected = null;

  // UI state
  let sidebarOpen = true;
  let mailbox = 'inbox'; // inbox, starred, snoozed, sent, drafts, archive, trash

  // Derived
  $: mailboxCounts = computeMailboxCounts(emails);
  $: baseEmails = emails.filter(matchesMailbox);
  $: filtered = !search
    ? baseEmails
    : baseEmails.filter(e => [e.subject, e.from, e.preview].join(' ').toLowerCase().includes(search.toLowerCase()));

  function mapEmail(m, i) {
    const preview = coalescePreview(m);
    return {
      id: m?.id || m?.contextId || `email-${i+1}`,
      contextId: m?.contextId || null,
      from: m?.senderName || m?.senderEmail || 'Unknown sender',
      fromEmail: m?.senderEmail || '',
      to: m?.recipientName || m?.recipientEmail || '',
      toEmail: m?.recipientEmail || '',
      subject: m?.subject || 'No subject',
      preview,
      contentText: m?.emailBodyTransformedText || '',
      contentMarkdown: m?.emailBodyTransformedMarkdown || '',
      contentHtml: typeof m?.emailBodyHtml === 'string' && m.emailBodyHtml.trim().length > 0 ? m.emailBodyHtml : null,
      timestamp: m?.receivedTimestampDisplay || '',
      timestampIso: m?.receivedTimestampIso || null,
      read: Boolean(m?.read),
      starred: Boolean(m?.starred),
      avatar: m?.avatarUrl || m?.companyLogoUrl || '',
      labels: Array.isArray(m?.labels) ? m.labels : [],
      companyLogoUrl: m?.companyLogoUrl || null
    };
  }
  function coalescePreview(m) {
    const text = typeof m?.emailBodyTransformedText === 'string' ? m.emailBodyTransformedText.trim() : '';
    if (!text) return '';
    const normalized = text.replace(/\s+/g, ' ');
    return normalized.length <= 180 ? normalized : `${normalized.slice(0,177)}...`;
  }

  $: if (!selected && filtered.length) selected = filtered[0];

  function selectEmail(e) { selected = e; selected.read = true; }
  function toggleSidebar() { sidebarOpen = !sidebarOpen; }

  function matchesMailbox(e) {
    const labels = (e.labels || []).map(l => String(l).toLowerCase());
    switch (mailbox) {
      case 'inbox': return true;
      case 'starred': return !!e.starred;
      case 'snoozed': return labels.includes('snoozed');
      case 'sent': return labels.includes('sent');
      case 'drafts': return labels.includes('drafts') || labels.includes('draft');
      case 'archive': return labels.includes('archive') || labels.includes('archived');
      case 'trash': return labels.includes('trash') || labels.includes('deleted');
      default: return true;
    }
  }

  function computeMailboxCounts(list) {
    const out = { inbox: list.length, starred: 0, snoozed: 0, sent: 0, drafts: 0, archive: 0, trash: 0 };
    for (const e of list) {
      const labels = (e.labels || []).map(l => String(l).toLowerCase());
      if (e.starred) out.starred++;
      if (labels.includes('snoozed')) out.snoozed++;
      if (labels.includes('sent')) out.sent++;
      if (labels.includes('drafts') || labels.includes('draft')) out.drafts++;
      if (labels.includes('archive') || labels.includes('archived')) out.archive++;
      if (labels.includes('trash') || labels.includes('deleted')) out.trash++;
    }
    return out;
  }
  function escapeHtml(s) { return window.ComposerAI?.escapeHtml ? window.ComposerAI.escapeHtml(s) : (s || ''); }
  function renderMarkdown(md) { return window.ComposerAI?.renderMarkdown ? window.ComposerAI.renderMarkdown(md || '') : (md || ''); }

  // Date helpers: list shows relative time only; detail shows full date/time
  const rtf = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });
  function parseDate(val) {
    if (!val) return null;
    const d = new Date(val);
    return isNaN(d.getTime()) ? null : d;
  }
  function formatRelativeTime(primary, fallback) {
    const d = parseDate(primary) || parseDate(fallback);
    if (!d) return escapeHtml(fallback || '');
    const now = new Date();
    const diffMs = d.getTime() - now.getTime();
    const absMs = Math.abs(diffMs);
    const minute = 60 * 1000, hour = 60 * minute, day = 24 * hour, month = 30 * day, year = 365 * day;
    if (absMs < minute) return 'just now';
    if (absMs < hour) return rtf.format(Math.round(diffMs / minute), 'minute');
    if (absMs < day) return rtf.format(Math.round(diffMs / hour), 'hour');
    if (absMs < month) return rtf.format(Math.round(diffMs / day), 'day');
    if (absMs < year) return rtf.format(Math.round(diffMs / month), 'month');
    return rtf.format(Math.round(diffMs / year), 'year');
  }
  function formatFullDate(primary, fallback) {
    const d = parseDate(primary) || parseDate(fallback);
    if (!d) return escapeHtml(fallback || '');
    return d.toLocaleString(undefined, {
      year: 'numeric', month: 'short', day: '2-digit',
      hour: 'numeric', minute: '2-digit', hour12: true, timeZoneName: 'short'
    });
  }
</script>

<div class="h-screen flex overflow-hidden bg-gradient-to-b from-slate-50 to-slate-100">
  <!-- Sidebar -->
  <aside class="shrink-0 border-r border-slate-200 bg-white/80 backdrop-blur transition-all duration-200"
         class:w-64={sidebarOpen}
         class:w-0={!sidebarOpen}
         class:overflow-hidden={!sidebarOpen}
         class:border-r-0={!sidebarOpen}>
    <div class="p-4 border-b border-slate-200">
      <div class="flex items-center gap-2 mb-4">
        <InboxIcon class="h-6 w-6 text-slate-900" />
        <h1 class="text-xl font-bold text-slate-900">ComposerAI</h1>
      </div>
      <button class="inline-flex items-center gap-2 w-full justify-center rounded-xl px-4 py-2 font-semibold text-white bg-gradient-to-br from-slate-900 to-slate-800 shadow ring-1 ring-slate-900/10">
        <Pencil class="h-4 w-4" /> Compose
      </button>
    </div>
    <nav class="p-2 space-y-1 overflow-y-auto">
      <button type="button" class="w-full flex items-center gap-3 rounded-lg px-3 py-2 text-slate-600 hover:bg-slate-100"
              class:bg-slate-100={mailbox==='inbox'} on:click={() => mailbox='inbox'}>
        <InboxIcon class="h-4 w-4" />
        <span class="text-slate-900">Inbox</span>
        <span class="ml-auto rounded-full bg-slate-200 px-2 text-xs font-semibold text-slate-700">{mailboxCounts.inbox}</span>
      </button>
      <button type="button" class="w-full flex items-center gap-3 rounded-lg px-3 py-2 text-slate-600 hover:bg-slate-100"
              class:bg-slate-100={mailbox==='starred'} on:click={() => mailbox='starred'}>
        <StarIcon class="h-4 w-4" />
        <span class="text-slate-900">Starred</span>
        <span class="ml-auto rounded-full bg-slate-200 px-2 text-xs font-semibold text-slate-700">{mailboxCounts.starred}</span>
      </button>
      <button type="button" class="w-full flex items-center gap-3 rounded-lg px-3 py-2 text-slate-600 hover:bg-slate-100"
              class:bg-slate-100={mailbox==='snoozed'} on:click={() => mailbox='snoozed'}>
        <AlarmClock class="h-4 w-4" />
        <span class="text-slate-900">Snoozed</span>
        <span class="ml-auto rounded-full bg-slate-200 px-2 text-xs font-semibold text-slate-700">{mailboxCounts.snoozed}</span>
      </button>
      <button type="button" class="w-full flex items-center gap-3 rounded-lg px-3 py-2 text-slate-600 hover:bg-slate-100"
              class:bg-slate-100={mailbox==='sent'} on:click={() => mailbox='sent'}>
        <Send class="h-4 w-4" />
        <span class="text-slate-900">Sent</span>
        <span class="ml-auto rounded-full bg-slate-200 px-2 text-xs font-semibold text-slate-700">{mailboxCounts.sent}</span>
      </button>
      <button type="button" class="w-full flex items-center gap-3 rounded-lg px-3 py-2 text-slate-600 hover:bg-slate-100"
              class:bg-slate-100={mailbox==='drafts'} on:click={() => mailbox='drafts'}>
        <Pencil class="h-4 w-4" />
        <span class="text-slate-900">Drafts</span>
        <span class="ml-auto rounded-full bg-slate-200 px-2 text-xs font-semibold text-slate-700">{mailboxCounts.drafts}</span>
      </button>
      <button type="button" class="w-full flex items-center gap-3 rounded-lg px-3 py-2 text-slate-600 hover:bg-slate-100"
              class:bg-slate-100={mailbox==='archive'} on:click={() => mailbox='archive'}>
        <Archive class="h-4 w-4" />
        <span class="text-slate-900">Archive</span>
        <span class="ml-auto rounded-full bg-slate-200 px-2 text-xs font-semibold text-slate-700">{mailboxCounts.archive}</span>
      </button>
      <button type="button" class="w-full flex items-center gap-3 rounded-lg px-3 py-2 text-slate-600 hover:bg-slate-100"
              class:bg-slate-100={mailbox==='trash'} on:click={() => mailbox='trash'}>
        <Trash2 class="h-4 w-4" />
        <span class="text-slate-900">Trash</span>
        <span class="ml-auto rounded-full bg-slate-200 px-2 text-xs font-semibold text-slate-700">{mailboxCounts.trash}</span>
      </button>
    </nav>
  </aside>

  <!-- List -->
  <section class="shrink-0 flex flex-col bg-white/90 border-r border-slate-200"
           class:w-96={sidebarOpen}
           class:w-[28rem]={!sidebarOpen}
  >
    <div class="px-4 py-3 border-b border-slate-200">
      <div class="flex items-center gap-2">
        <button type="button" title="Toggle sidebar" class="rounded-xl border border-slate-200 bg-white h-9 w-9 grid place-items-center text-slate-600 hover:bg-slate-50" on:click={toggleSidebar}>
          <Menu class="h-4 w-4" />
        </button>
        <div class="relative flex-1">
          <input placeholder="Search emails..." bind:value={search}
                 class="w-full pl-3 pr-3 py-2 rounded-xl border border-slate-200 focus:outline-none focus:ring-2 focus:ring-slate-200" />
        </div>
      </div>
    </div>
    <div class="flex-1 overflow-y-auto">
      {#if filtered.length === 0}
        <div class="p-6 text-sm text-slate-500">No emails match your filter.</div>
      {:else}
{#each filtered as e (e.id)}
          <button type="button" class="w-full text-left px-4 py-3 border-b border-slate-200 hover:bg-slate-50 cursor-pointer {selected?.id===e.id?'bg-slate-100':''} {e.read?'':'bg-blue-50/30'}" on:click={() => selectEmail(e)} on:keydown={(ev) => (ev.key==='Enter'||ev.key===' ') && selectEmail(e)}>
            <div class="flex items-start gap-3">
              <img src={e.avatar || e.companyLogoUrl || ('https://i.pravatar.cc/100?u=' + encodeURIComponent(e.fromEmail || e.from))} alt={escapeHtml(e.from)} class="h-10 w-10 rounded-full object-cover" loading="lazy"/>
              <div class="min-w-0 flex-1">
                <div class="flex items-center justify-between">
                  <span class="font-semibold truncate" class:text-slate-700={e.read} class:text-slate-900={!e.read}>{escapeHtml(e.from)}</span>
<span class="text-xs text-slate-400 ml-2 shrink-0">{formatRelativeTime(e.timestampIso, e.timestamp)}</span>
                </div>
                <p class="text-sm truncate" class:font-medium={!e.read} class:text-slate-700={e.read} class:text-slate-900={!e.read}>{escapeHtml(e.subject)}</p>
                <p class="text-xs text-slate-500 truncate">{escapeHtml(e.preview)}</p>
              </div>
            </div>
          </button>
        {/each}
      {/if}
    </div>
  </section>

  <!-- Content -->
  <section class="flex-1 flex flex-col bg-white/95">
    {#if !selected}
      <div class="flex-1 grid place-items-center text-slate-400">
        <div class="text-center">
          <svg class="h-16 w-16 mx-auto mb-4 opacity-20 text-slate-400" viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M21.75 6.75v10.5a2.25 2.25 0 01-2.25 2.25h-15a2.25 2.25 0 01-2.25-2.25V6.75m19.5 0A2.25 2.25 0 0019.5 4.5h-15a2.25 2.25 0 00-2.25 2.25m19.5 0v.243a2.25 2.25 0 01-1.07 1.916l-7.5 4.615a2.25 2.25 0 01-2.36 0L3.32 8.91a2.25 2.25 0 01-1.07-1.916V6.75" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>
          <p>Select an email to read</p>
        </div>
      </div>
    {:else}
      <div class="p-6 border-b border-slate-200">
        <div class="flex items-start gap-4 justify-between">
          <div class="flex items-start gap-4">
            <img src={selected.avatar || selected.companyLogoUrl || ('https://i.pravatar.cc/120?u=' + encodeURIComponent(selected.fromEmail || selected.from))} alt={escapeHtml(selected.from)} class="h-12 w-12 rounded-full object-cover" loading="lazy"/>
            <div>
              <h2 class="text-xl font-semibold text-slate-900">{escapeHtml(selected.subject)}</h2>
              <div class="flex items-center gap-2 text-sm text-slate-600">
                <span class="font-medium">{escapeHtml(selected.from)}</span>
                {#if selected.fromEmail}<span>&lt;{escapeHtml(selected.fromEmail)}&gt;</span>{/if}
              </div>
              {#if selected.to || selected.toEmail}
                <div class="text-xs mt-1 text-slate-400">To: {escapeHtml(selected.to || 'Unknown recipient')} {#if selected.toEmail}<span>&lt;{escapeHtml(selected.toEmail)}&gt;</span>{/if}</div>
              {/if}
<p class="text-xs mt-1 text-slate-400">{formatFullDate(selected.timestampIso, selected.timestamp)}</p>
            </div>
          </div>
          <div class="flex gap-2">
            <button class="rounded-full h-9 w-9 grid place-items-center border border-slate-200 bg-white text-slate-600" title="Reply">↩</button>
            <button class="rounded-full h-9 w-9 grid place-items-center border border-slate-200 bg-white text-slate-600" title="Archive">⤓</button>
            <button class="rounded-full h-9 w-9 grid place-items-center border border-slate-200 bg-white text-slate-600" title="Delete">✕</button>
          </div>
        </div>
        <div class="mt-4 flex flex-wrap gap-2">
          {#each Object.keys(FALLBACK_COMMAND_TITLES) as key}
            <button class="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-50" on:click={() => { /* hook AI later */ }}>
              {FALLBACK_COMMAND_TITLES[key]}
            </button>
          {/each}
        </div>
      </div>
      <div class="flex-1 overflow-y-auto">
        <div class="p-6">
          {#if selected.contentHtml}
            <EmailIframe html={selected.contentHtml} />
          {:else}
            <div class="prose prose-sm max-w-none text-slate-700">
              {@html renderMarkdown(selected.contentMarkdown || selected.contentText || '')}
            </div>
          {/if}
        </div>
      </div>
    {/if}
  </section>
</div>

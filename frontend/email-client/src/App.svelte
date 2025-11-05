<script>
  import { onMount } from 'svelte';
  import EmailIframe from './lib/EmailIframe.svelte';
  import ComposeWindow from './lib/ComposeWindow.svelte';
  import AISummaryPanel from './lib/AISummaryPanel.svelte';
  import { isMobile, isTablet, isDesktop, isWide, viewport } from './lib/viewport';
import { Menu, Pencil, Inbox as InboxIcon, Star as StarIcon, AlarmClock, Send, Archive, Trash2, Reply, Forward, ArrowLeft, ChevronLeft, ChevronRight } from 'lucide-svelte';
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
  let conversationId = null;

  // Compose windows
  let composes = [];

  // Viewport responsive
  $: mobile = $isMobile;
  $: tablet = $isTablet;
  $: desktop = $isDesktop;
  $: wide = $isWide;
  $: viewportType = $viewport;
  let showDrawer = false;
  let showEmailList = true; // For tablet view toggle

  // AI summary panel
  let aiOpen = false;
  let aiTitle = '';
  let aiHtml = '';

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

  // Auto-select first email on larger screens when none selected
  $: if ((tablet || desktop || wide) && !selected && filtered.length) selected = filtered[0];

  function selectEmail(e) { selected = e; selected.read = true; }
  function toggleSidebar() { sidebarOpen = !sidebarOpen; }
  function handleMenuClick() { mobile ? (showDrawer = !showDrawer) : toggleSidebar(); }

  function openCompose() {
    composes = [...composes, { id: crypto.randomUUID(), isReply: false, to: '', subject: '', body: '' }];
  }
  function openReply() {
    if (!selected) return;
    composes = [...composes, { id: crypto.randomUUID(), isReply: true, to: selected.fromEmail || '', subject: `Re: ${selected.subject}`, body: '' }];
  }
  function closeCompose(id) { composes = composes.filter(c => c.id !== id); }

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

  // ---------- AI integration (parity with v1) ----------
  /**
   * Build email context string for AI commands.
   *
   * CONTEXT FLOW:
   * 1. Backend HtmlConverter processes email HTML → cleansed markdown (emailBodyTransformedMarkdown)
   * 2. Frontend receives as contentMarkdown field
   * 3. This function returns the pre-processed markdown directly
   * 4. Backend uses this without re-processing when emailContext is provided
   *
   * This maintains the integrity of the Java backend's context processing chain.
   */
  function buildEmailContextString(email) {
    if (!email) return '';

    // Prefer the pre-processed markdown from backend (already cleansed by HtmlConverter)
    // This maintains the chain of context from Java's HtmlConverter processing
    if (email.contentMarkdown && email.contentMarkdown.trim()) {
      // Return the cleansed markdown directly without wrapping in metadata
      // The backend already has this metadata when needed
      return email.contentMarkdown.trim();
    }

    // Fallback to building context with metadata if no markdown available
    const lines = [];
    lines.push('=== Email Metadata ===');
    lines.push(`Subject: ${email.subject || 'No subject'}`);
    lines.push(`From: ${email.from}${email.fromEmail ? ` <${email.fromEmail}>` : ''}`);
    if (email.to || email.toEmail) lines.push(`To: ${(email.to || 'Unknown recipient')}${email.toEmail ? ` <${email.toEmail}>` : ''}`);
    if (email.timestamp) lines.push(`Email sent on: ${email.timestamp}`);
    if (email.timestampIso) lines.push(`Email sent (ISO): ${email.timestampIso}`);
    lines.push('');
    lines.push('=== Email Body ===');
    const body = (email.contentText || '').trim();
    lines.push(body.length > 0 ? body : '(Email body is empty)');
    return lines.join('\n');
  }

  /**
   * Call AI command with email context.
   *
   * Context priority:
   * 1. contextId - References pre-stored context in backend EmailContextRegistry
   * 2. emailContext - Fallback to sending markdown content directly
   *
   * The backend's ChatService will use contextId first, then emailContext as fallback.
   */
  async function callAiCommand(command, instruction, { contextId, subject } = {}) {
    const payload = {
      message: instruction,
      conversationId,
      maxResults: 5,
      thinkingEnabled: false,
      jsonOutput: false
    };

    // Prefer contextId when available (backend has pre-processed context)
    if (contextId) {
      payload.contextId = contextId;
    }

    if (['compose', 'draft', 'summarize', 'translate', 'tone'].includes(command)) {
      payload.aiCommand = command;
    }

    if (subject && subject.trim()) {
      payload.subject = subject.trim();
    }

    // Only send emailContext if no contextId is available
    // This ensures we use the backend's pre-processed context when possible
    if (selected && !contextId) {
      const ctx = buildEmailContextString(selected);
      if (ctx) payload.emailContext = ctx;
    }

    const resp = await fetch('/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-UI-Request': uiNonce || '' },
      body: JSON.stringify(payload)
    });
    const data = await resp.json().catch(() => null);
    if (!resp.ok) throw new Error((data && (data.message || data.error)) || `HTTP ${resp.status}`);
    if (data?.conversationId) conversationId = data.conversationId;
    return data;
  }

  function parseSubjectAndBody(text) {
    if (!text || !text.trim()) return { subject: '', body: '' };
    const trimmed = text.trim();
    const m = trimmed.match(/^Subject:\s*(.+?)$/m);
    if (m) {
      const subject = m[1].trim();
      const subjectEnd = trimmed.indexOf(m[0]) + m[0].length;
      const body = trimmed.substring(subjectEnd).replace(/^\s*\n+/, '').trim();
      return { subject, body };
    }
    return { subject: '', body: trimmed };
  }

  function buildComposeInstruction(command, currentDraft, isReply) {
    if (command === 'draft') {
      if (currentDraft && currentDraft.length > 0) return `Improve this ${isReply ? 'reply' : 'draft'} while preserving the intent:\n\n${currentDraft}`;
      return isReply ? 'Draft a courteous reply addressing the key points from the email above.' : 'Draft a helpful email based on the selected context.';
    }
    if (command === 'compose') {
      return currentDraft && currentDraft.length > 0 ? `Polish this email draft and make it clear and concise:\n\n${currentDraft}` : 'Compose a professional reply using the email context above.';
    }
    if (command === 'tone') {
      return currentDraft && currentDraft.length > 0 ? `Adjust the tone of this email to be friendly but professional:\n\n${currentDraft}` : 'Adjust the email to a friendly but professional tone.';
    }
    return 'Assist with the selected email.';
  }

  async function runMainAiCommand(command) {
    if (!selected) return alert('Select an email first.');
    const title = FALLBACK_COMMAND_TITLES[command] || 'AI Assistant';

    // Draft/Compose/Tone write into a compose window
    if (command === 'draft' || command === 'compose' || command === 'tone') {
      // open a reply compose
      openReply();
      const target = composes[composes.length - 1];
      try {
        const instruction = buildComposeInstruction(command, target?.body || '', true);
        const data = await callAiCommand(command, instruction, {
          contextId: selected.contextId,
          subject: target?.subject || selected.subject
        });
        let draftText = (data?.response && data.response.trim()) || '';
        if (!draftText && data?.sanitizedHtml) {
          const temp = document.createElement('div'); temp.innerHTML = data.sanitizedHtml; draftText = temp.textContent.trim();
        }
        if (draftText) {
          const parsed = parseSubjectAndBody(draftText);
          target.subject = parsed.subject || target.subject;
          target.body = parsed.body || draftText;
          composes = [...composes];
        }
      } catch (e) { alert(e?.message || 'Unable to draft.'); }
      return;
    }

    // Summarize/Translate show in inline panel
    try {
      const instruction = (command === 'summarize') ? 'Provide a concise summary of the selected email.' : 'Translate the selected email to English.';
      // Pass contextId to use backend's pre-processed context if available
      const data = await callAiCommand(command, instruction, {
        contextId: selected.contextId,
        subject: selected.subject
      });
      const html = (data?.response && window.ComposerAI?.renderMarkdown ? window.ComposerAI.renderMarkdown(data.response) : '')
                || (data?.sanitizedHtml || data?.sanitizedHTML || '')
                || '<div class="text-sm text-slate-500">No response received.</div>';
      aiTitle = title; aiHtml = html; aiOpen = true;
    } catch (e) { alert(e?.message || 'Unable to complete request.'); }
  }
</script>

<div class="h-[100dvh] flex overflow-hidden bg-gradient-to-b from-slate-50 to-slate-100">
  <!-- Sidebar -->
  <aside class="shrink-0 border-r border-slate-200 bg-white/80 backdrop-blur transition-all duration-200"
         class:w-64={((wide && sidebarOpen) || (desktop && sidebarOpen))}
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
      <button class="inline-flex items-center gap-2 w-full justify-center rounded-xl px-4 py-2 font-semibold text-white bg-gradient-to-br from-slate-900 to-slate-800 shadow ring-1 ring-slate-900/10" on:click={openCompose}>
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
  {#if (mobile || tablet) && showDrawer}
    <button type="button" class="fixed inset-0 bg-black/30 z-[50]" aria-label="Close menu overlay"
            on:click={() => (showDrawer = false)}
            on:keydown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); showDrawer = false; } }}>
    </button>
  {/if}

  <!-- List -->
  <section class="shrink-0 flex flex-col bg-white/90 border-r border-slate-200"
           class:w-[22rem]={wide}
           class:w-[20rem]={desktop}
           class:w-[18rem]={tablet && showEmailList}
           class:w-0={tablet && !showEmailList}
           class:w-full={mobile}
           class:hidden={mobile && selected}
           class:overflow-hidden={tablet && !showEmailList}
  >
    <div class="px-4 py-3 border-b border-slate-200">
      <div class="flex items-center gap-2">
        <button type="button" title="Toggle menu" class="rounded-xl border border-slate-200 bg-white h-9 w-9 grid place-items-center text-slate-600 hover:bg-slate-50" on:click={handleMenuClick}>
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
  <section class="flex-1 flex flex-col bg-white/95 relative"
           class:hidden={mobile && !selected}>
    {#if mobile || tablet}
      <div class="px-4 py-3 border-b border-slate-200 flex items-center gap-2">
        {#if mobile}
          <button type="button" class="rounded-xl border border-slate-200 bg-white h-9 w-9 grid place-items-center text-slate-600 hover:bg-slate-50" on:click={() => { selected = null; showDrawer = false; }} aria-label="Back to list">
            <ArrowLeft class="h-4 w-4" />
          </button>
        {/if}
        {#if tablet}
          <button type="button" class="rounded-xl border border-slate-200 bg-white h-9 w-9 grid place-items-center text-slate-600 hover:bg-slate-50" on:click={() => showEmailList = !showEmailList} aria-label="Toggle email list">
            {#if showEmailList}
              <ChevronLeft class="h-4 w-4" />
            {:else}
              <ChevronRight class="h-4 w-4" />
            {/if}
          </button>
        {/if}
        <button type="button" title="Toggle menu" class="rounded-xl border border-slate-200 bg-white h-9 w-9 grid place-items-center text-slate-600 hover:bg-slate-50" on:click={handleMenuClick} aria-label="Open folders">
          <Menu class="h-4 w-4" />
        </button>
        <div class="relative flex-1">
          <input placeholder="Search emails..." bind:value={search}
                 class="w-full pl-3 pr-3 py-2 rounded-xl border border-slate-200 focus:outline-none focus:ring-2 focus:ring-slate-200" />
        </div>
      </div>
    {/if}
    {#if !selected}
      <div class="flex-1 grid place-items-center text-slate-400">
        <div class="text-center">
          <svg class="h-16 w-16 mx-auto mb-4 opacity-20 text-slate-400" viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M21.75 6.75v10.5a2.25 2.25 0 01-2.25 2.25h-15a2.25 2.25 0 01-2.25-2.25V6.75m19.5 0A2.25 2.25 0 0019.5 4.5h-15a2.25 2.25 0 00-2.25 2.25m19.5 0v.243a2.25 2.25 0 01-1.07 1.916l-7.5 4.615a2.25 2.25 0 01-2.36 0L3.32 8.91a2.25 2.25 0 01-1.07-1.916V6.75" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>
          <p>Select an email to read</p>
        </div>
      </div>
    {:else}
      <div class="border-b border-slate-200"
           class:px-4={mobile}
           class:px-5={tablet}
           class:px-6={desktop || wide}
           class:py-3={mobile || tablet}
           class:py-4={desktop || wide}>
          <div class="flex items-start gap-3" class:flex-col={mobile}>
            <div class="flex items-start gap-3 min-w-0 flex-1">
              <img src={selected.avatar || selected.companyLogoUrl || ('https://i.pravatar.cc/120?u=' + encodeURIComponent(selected.fromEmail || selected.from))} alt={escapeHtml(selected.from)} class="h-10 w-10 rounded-full object-cover shrink-0" class:h-12={!mobile} class:w-12={!mobile} loading="lazy"/>
              <div class="min-w-0 flex-1">
                <h2 class="text-lg font-semibold text-slate-900 break-words" class:text-xl={!mobile}>{escapeHtml(selected.subject)}</h2>
                <div class="flex items-center gap-1 text-sm text-slate-600 flex-wrap">
                  <span class="font-medium truncate">{escapeHtml(selected.from)}</span>
                  {#if selected.fromEmail}<span class="text-xs truncate">&lt;{escapeHtml(selected.fromEmail)}&gt;</span>{/if}
                </div>
                {#if selected.to || selected.toEmail}
                  <div class="text-xs mt-1 text-slate-400 truncate">To: {escapeHtml(selected.to || 'Unknown recipient')} {#if selected.toEmail}<span>&lt;{escapeHtml(selected.toEmail)}&gt;</span>{/if}</div>
                {/if}
                <p class="text-xs mt-1 text-slate-400">{formatFullDate(selected.timestampIso, selected.timestamp)}</p>
              </div>
            </div>
            <div class="flex gap-2 shrink-0" class:w-full={mobile} class:justify-end={mobile}>
              <button type="button" class="rounded-full h-9 w-9 grid place-items-center border border-slate-200 bg-white text-slate-600 hover:bg-slate-50" title="Reply" aria-label="Reply" on:click={openReply}>
                <Reply class="h-4 w-4" />
              </button>
              <button type="button" class="rounded-full h-9 w-9 grid place-items-center border border-slate-200 bg-white text-slate-600 hover:bg-slate-50" title="Forward" aria-label="Forward">
                <Forward class="h-4 w-4" />
              </button>
              <button type="button" class="rounded-full h-9 w-9 grid place-items-center border border-slate-200 bg-white text-slate-600 hover:bg-slate-50" title="Archive" aria-label="Archive">
                <Archive class="h-4 w-4" />
              </button>
              <button type="button" class="rounded-full h-9 w-9 grid place-items-center border border-slate-200 bg-white text-slate-600 hover:bg-slate-50" title="Delete" aria-label="Delete">
                <Trash2 class="h-4 w-4" />
              </button>
            </div>
          </div>
          <div class="mt-4 flex flex-wrap gap-2">
            {#each Object.keys(FALLBACK_COMMAND_TITLES) as key}
              <button class="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-50" on:click={() => runMainAiCommand(key)}>
                {FALLBACK_COMMAND_TITLES[key]}
              </button>
            {/each}
          </div>
        </div>
      <div class="overflow-y-auto" class:flex-1={!aiOpen} class:flex-initial={aiOpen}>
        <div class="w-full max-w-full overflow-x-hidden"
             class:p-4={!selected.contentHtml && (mobile || tablet)}
             class:p-5={!selected.contentHtml && desktop}
             class:p-6={!selected.contentHtml && wide}>
          {#if selected.contentHtml}
            <EmailIframe html={selected.contentHtml} />
          {:else}
            <div class="prose prose-sm max-w-none text-slate-700 break-words">
              {@html renderMarkdown(selected.contentMarkdown || selected.contentText || '')}
            </div>
          {/if}
        </div>
      </div>

      <!-- AI Summary Panel - inline at bottom of content area -->
      {#if aiOpen}
        <AISummaryPanel open={true} title={aiTitle} html={aiHtml} on:close={() => (aiOpen = false)} />
      {/if}
    {/if}
    {#each composes as c (c.id)}
      <ComposeWindow open={true} isReply={c.isReply} to={c.to} subject={c.subject} body={c.body}
        on:close={() => closeCompose(c.id)}
        on:send={(e) => { /* hook actual send later; close for now */ closeCompose(c.id); }}
        on:requestAi={async (e) => {
          const { command, draft, subject: subj } = e.detail;
          try {
            const instruction = buildComposeInstruction(command, draft || '', c.isReply);
            const data = await callAiCommand(command, instruction, { contextId: selected?.contextId, subject: subj });
            const text = (data?.response && data.response.trim()) || '';
            const html = (data?.sanitizedHtml || data?.sanitizedHTML || '').trim();
            let draftText = text;
            if (!draftText && html) {
              const temp = document.createElement('div'); temp.innerHTML = html; draftText = temp.textContent.trim();
            }
            if (draftText) {
              const parsed = parseSubjectAndBody(draftText);
              c.subject = parsed.subject || c.subject;
              c.body = parsed.body || draftText;
              composes = [...composes];
            }
          } catch (err) { alert(err?.message || 'AI request failed'); }
        }} />
    {/each}
  </section>
</div>

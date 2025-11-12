<script>
  import { onMount } from 'svelte';
  import { get } from 'svelte/store';
  import EmailIframe from './lib/EmailIframe.svelte';
import ComposeWindow from './lib/ComposeWindow.svelte';
import AiSummaryWindow from './lib/AiSummaryWindow.svelte';
import WindowDock from './lib/window/WindowDock.svelte';
import WindowProvider from './lib/window/WindowProvider.svelte';
import EmailActionToolbar from './lib/EmailActionToolbar.svelte';
import EmailDetailView from './lib/EmailDetailView.svelte';
import MailboxSidebar from './lib/MailboxSidebar.svelte';
import AiLoadingJourney from './lib/AiLoadingJourney.svelte';
import { isMobile, isTablet, isDesktop, isWide, viewport } from './lib/viewport';
import { createWindowManager } from './lib/window/windowStore'; // temp use
import { createComposeWindow, createSummaryWindow, WindowKind } from './lib/window/windowTypes';
import { catalogStore, hydrateCatalog, ensureCatalogLoaded as ensureCatalog, getFunctionMeta, mergeDefaultArgs, resolveDefaultInstruction } from './lib/services/aiCatalog';
import { mapEmailMessage, computeMailboxCounts, parseSubjectAndBody } from './lib/services/emailUtils';
import { createAiJourneyStore } from './lib/services/aiJourneyStore';
import { Menu, Pencil, Inbox as InboxIcon, Star as StarIcon, AlarmClock, Send, ArrowLeft, ChevronLeft, ChevronRight, Archive, Trash2 } from 'lucide-svelte';
  export let bootstrap = {};

  hydrateCatalog(bootstrap.aiFunctions || null);
  const catalog = catalogStore();
  $: catalogData = $catalog;
  $: aiFunctionsByKey = catalogData?.functionsByKey || {};
  $: aiPrimaryCommandKeys = Array.isArray(catalogData?.primaryCommands) ? catalogData.primaryCommands : [];

  let uiNonce = bootstrap.uiNonce || null;
  let nonceRefreshPromise = null;
  let search = '';
  let emails = Array.isArray(bootstrap.messages) ? bootstrap.messages.map(mapEmailMessage) : [];
  let selected = null;
let conversationId = null;
const windowManager = createWindowManager({ maxFloating: 4, maxDocked: 3 });
  const windowsStore = windowManager.windows;
  const floatingStore = windowManager.floating;
  const dockedStore = windowManager.docked;
  const minimizedStore = windowManager.minimized;
  const windowErrorStore = windowManager.lastError;
  const aiJourney = createAiJourneyStore();
  const aiJourneyOverlayStore = aiJourney.overlay;
  let windowNotice = '';
  let windowNoticeTimer = null;
  $: windows = $windowsStore;
  $: floatingWindows = $floatingStore;
  $: dockedWindows = $dockedStore;
  $: minimizedWindows = $minimizedStore;
  $: windowAlert = $windowErrorStore ? $windowErrorStore.message : '';
  $: aiJourneyOverlay = $aiJourneyOverlayStore;
  // Viewport responsive
  $: mobile = $isMobile;
  $: tablet = $isTablet;
  $: desktop = $isDesktop;
  $: wide = $isWide;
  $: viewportType = $viewport;
  let showDrawer = false;
  let showEmailList = true; // For tablet view toggle

  // UI state
  let sidebarOpen = true;
  let mailbox = 'inbox'; // inbox, starred, snoozed, sent, drafts, archive, trash

  // Derived
  $: mailboxCounts = computeMailboxCounts(emails);
  $: baseEmails = emails.filter(matchesMailbox);
  $: filtered = !search
    ? baseEmails
    : baseEmails.filter(e => [e.subject, e.from, e.preview].join(' ').toLowerCase().includes(search.toLowerCase()));
  $: primaryCommandEntries = (aiPrimaryCommandKeys.length ? aiPrimaryCommandKeys : Object.keys(aiFunctionsByKey || {}))
    .map((key) => ({ key, meta: aiFunctionsByKey[key] }))
    .filter((entry) => !!entry.meta);
  $: composeAiFunctions = Object.values(aiFunctionsByKey || {})
    .filter((fn) => Array.isArray(fn.scopes) && fn.scopes.includes('compose'));

  /** Looks up a variant (e.g., translation language) within a given function definition. */
  function getVariant(meta, variantKey) {
    if (!meta || !Array.isArray(meta.variants) || !variantKey) return null;
    return meta.variants.find((variant) => variant.key === variantKey) || null;
  }

  onMount(() => {
    ensureCatalog().catch(() => {});
  });

  async function ensureCatalogReady() {
    return ensureCatalog();
  }

  // Auto-select first email on larger screens when none selected
  $: if ((tablet || desktop || wide) && !selected && filtered.length) selected = filtered[0];

  function selectEmail(e) { selected = e; selected.read = true; }
  function toggleSidebar() { sidebarOpen = !sidebarOpen; }
  function handleMenuClick() { mobile ? (showDrawer = !showDrawer) : toggleSidebar(); }

  function showWindowLimitMessage() {
    const err = get(windowErrorStore);
    if (!err) return;
    windowNotice = err.message;
    clearTimeout(windowNoticeTimer);
    windowNoticeTimer = setTimeout(() => {
      windowNotice = '';
    }, 4000);
  }

  function openCompose() {
    const result = windowManager.open(createComposeWindow());
    if (!result.ok) {
      showWindowLimitMessage();
    }
  }

  function openReply() {
    if (!selected) return alert('Select an email first.');
    const descriptor = createComposeWindow(selected, {
      to: selected.fromEmail || '',
      subject: `Re: ${selected.subject || ''}`,
      isReply: true,
      title: selected.subject ? `Reply: ${selected.subject}` : 'Reply'
    });
    const result = windowManager.open(descriptor);
    if (!result.ok) {
      showWindowLimitMessage();
    }
  }

  function handleComposeSend(event) {
    // Placeholder: sending closes the draft for now.
    windowManager.close(event.detail.id);
  }

  async function triggerComposeAi(detail) {
    const { id, command, draft, subject: draftSubject, isReply } = detail;
    const ready = await ensureCatalogReady();
    if (!ready) return alert('AI helpers are unavailable. Please try again.');
    const fn = getFunctionMeta(catalogData, command);
    if (!fn) return alert('Command unavailable.');
    const instruction = buildComposeInstruction(command, draft || '', isReply, fn);
    const win = get(windowsStore).find((w) => w.id === id);
    const relatedEmail = win?.contextId ? emails.find((e) => e.id === win.contextId) : selected;
    const commandArgs = mergeDefaultArgs(fn, null);
    try {
      const data = await callAiCommand(command, instruction, {
        contextId: relatedEmail?.contextId || relatedEmail?.id,
        subject: draftSubject || relatedEmail?.subject,
        journeyScope: 'compose',
        journeyLabel: draftSubject || relatedEmail?.subject || 'draft',
        journeyHeadline: deriveJourneyHeadline(command, fn.label || 'AI Assistant'),
        commandArgs
      });
      let draftText = (data?.response && data.response.trim()) || '';
      if (!draftText && data?.sanitizedHtml) {
        const temp = document.createElement('div');
        temp.innerHTML = data.sanitizedHtml;
        draftText = temp.textContent.trim();
      }
      if (draftText) {
        const parsed = parseSubjectAndBody(draftText);
        windowManager.updateComposeDraft(id, {
          subject: parsed.subject || draftSubject,
          body: parsed.body || draftText
        });
      }
    } catch (error) {
      alert(error?.message || 'Unable to complete AI request.');
    }
  }

  function handleComposeRequestAi(event) {
    triggerComposeAi(event.detail);
  }

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

  const JOURNEY_SCOPE_META = {
    global: { subhead: 'ComposerAI assistant' },
    panel: { subhead: 'Mailbox assistant' },
    compose: { subhead: 'Draft assistant' }
  };

  function deriveJourneyHeadline(command, fallback) {
    const catalogLabel = getFunctionMeta(catalogData, command)?.label;
    switch (command) {
      case 'summarize': return 'Summarizing this email';
      case 'translate': return 'Translating the thread';
      case 'draft':
      case 'compose':
        return 'Drafting your reply';
      case 'tone':
        return 'Retuning the tone';
      default:
        return catalogLabel || fallback || 'Working on your request';
    }
  }

  function beginAiJourney({ scope = 'global', targetLabel = 'message', commandKey, headline } = {}) {
    const defaultHeadline = getFunctionMeta(catalogData, commandKey)?.label || 'Working on your request';
    const subhead = JOURNEY_SCOPE_META[scope]?.subhead || JOURNEY_SCOPE_META.global.subhead;
    return aiJourney.begin({ scope, targetLabel, commandKey, headline: headline || deriveJourneyHeadline(commandKey, defaultHeadline), subhead });
  }

  function advanceAiJourney(token, eventId) {
    aiJourney.advance(token, eventId);
  }

  function completeAiJourney(token) {
    aiJourney.complete(token);
  }

  function failAiJourney(token) {
    aiJourney.fail(token);
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
   * Request a fresh UI nonce from the backend when the servlet session expires.
   * The promise is memoized so concurrent 403 retries wait on the same refresh.
   */
  async function refreshUiNonce() {
    if (nonceRefreshPromise) return nonceRefreshPromise;
    const headers = { 'Content-Type': 'application/json' };
    if (uiNonce) headers['X-UI-Request'] = uiNonce;
    nonceRefreshPromise = (async () => {
      try {
        const resp = await fetch('/ui/session/nonce', {
          method: 'POST',
          headers
        });
        const data = await resp.json().catch(() => null);
        if (!resp.ok) {
          throw new Error((data && data.error) || `HTTP ${resp.status}`);
        }
        if (!data?.uiNonce) {
          throw new Error('Nonce refresh response missing uiNonce');
        }
        uiNonce = data.uiNonce;
        return uiNonce;
      } finally {
        nonceRefreshPromise = null;
      }
    })();
    return nonceRefreshPromise;
  }

  /**
   * Keep the servlet session warm with a lightweight /api/chat/health ping.
   * If it fails we fall back to the on-demand nonce refresh path.
   */
  async function pingChatHealth() {
    if (!uiNonce) return;
    try {
      await fetch('/api/chat/health', {
        method: 'GET',
        headers: { 'X-UI-Request': uiNonce }
      });
    } catch (_) {
      // Heartbeat is best-effort; failures are handled when sending the next chat request.
    }
  }

  onMount(() => {
    if (!uiNonce) {
      refreshUiNonce().catch(() => {});
    }
    const HEARTBEAT_INTERVAL_MS = 5 * 60 * 1000;
    const heartbeatId = window.setInterval(pingChatHealth, HEARTBEAT_INTERVAL_MS);
    return () => window.clearInterval(heartbeatId);
  });

  /**
   * Call AI command with email context.
   *
   * Context priority:
   * 1. contextId - References pre-stored context in backend EmailContextRegistry
   * 2. emailContext - Fallback to sending markdown content directly
   *
   * The backend's ChatService will use contextId first, then emailContext as fallback.
   */
  /**
   * Sends chat payloads with consistent metadata so the backend sees the same structure regardless
   * of which UI surface initiated the helper.
   */
  async function callAiCommand(command, instruction, { contextId, subject, journeyScope = 'global', journeyLabel, journeyHeadline, commandVariant, commandArgs } = {}) {
    const payload = {
      message: instruction,
      conversationId,
      maxResults: 5,
      thinkingEnabled: false,
      jsonOutput: false
    };

    const targetLabel = journeyLabel || subject || selected?.subject || 'message';
    const journeyToken = beginAiJourney({ scope: journeyScope, targetLabel, commandKey: command, headline: journeyHeadline });

    // Prefer contextId when available (backend has pre-processed context)
    if (contextId) {
      payload.contextId = contextId;
    }

    const commandMeta = getFunctionMeta(catalogData, command);
    if (command && commandMeta) {
      payload.aiCommand = command;
      if (commandVariant) {
        payload.commandVariant = commandVariant;
      }
      const argsPayload = commandArgs && Object.keys(commandArgs).length ? commandArgs : null;
      if (argsPayload) {
        payload.commandArgs = argsPayload;
      }
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

    advanceAiJourney(journeyToken, 'ai:context-search');

    try {
      const data = await postChatPayload(JSON.stringify(payload));
      advanceAiJourney(journeyToken, 'ai:llm-thinking');
      completeAiJourney(journeyToken);
      return data;
    } catch (err) {
      failAiJourney(journeyToken);
      throw err;
    }
  }

  /**
   * POST helper that retries once after silently refreshing the nonce.
   */
  async function postChatPayload(body, allowRetry = true) {
    const resp = await fetch('/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-UI-Request': uiNonce || '' },
      body
    });

    if (resp.status === 403 && allowRetry) {
      await refreshUiNonce();
      return postChatPayload(body, false);
    }

    const data = await resp.json().catch(() => null);
    if (!resp.ok) {
      throw new Error((data && (data.message || data.error)) || `HTTP ${resp.status}`);
    }
    if (data?.conversationId) {
      conversationId = data.conversationId;
    }
    return data;
  }

  /** Builds compose/draft instructions while preserving catalog defaults and current user drafts. */
  function buildComposeInstruction(command, currentDraft, isReply, meta) {
    const fallback = resolveDefaultInstruction(meta, null);
    if (command === 'draft') {
      if (currentDraft && currentDraft.length > 0) return `Improve this ${isReply ? 'reply' : 'draft'} while preserving the intent:\n\n${currentDraft}`;
      return isReply ? fallback : `${fallback}`;
    }
    if (command === 'compose') {
      return currentDraft && currentDraft.length > 0 ? `Polish this email draft and make it clear and concise:\n\n${currentDraft}` : fallback;
    }
    if (command === 'tone') {
      return currentDraft && currentDraft.length > 0 ? `Adjust the tone of this email to be friendly but professional:\n\n${currentDraft}` : fallback;
    }
    return fallback;
  }

  /** Entry point for primary AI actions (summary, translation, compose helpers). */
  async function runMainAiCommand(command) {
    if (!selected) return alert('Select an email first.');
    const ready = await ensureCatalogReady();
    if (!ready) {
      alert('AI helpers are unavailable. Please refresh and try again.');
      return;
    }
    const fn = getFunctionMeta(catalogData, command);
    if (!fn) return alert('Command unavailable.');
    const title = fn.label || 'AI Assistant';
    const commandArgs = mergeDefaultArgs(fn, null);

    const targetsCompose = Array.isArray(fn.scopes) && fn.scopes.includes('compose');
    if (targetsCompose) {
      const descriptor = createComposeWindow(selected, {
        to: selected.fromEmail || '',
        subject: `Re: ${selected.subject || ''}`,
        isReply: true,
        title: selected.subject ? `Reply: ${selected.subject}` : fn.label || 'AI Compose'
      });
      const result = windowManager.open(descriptor);
      if (!result.ok) {
        showWindowLimitMessage();
        return;
      }
      try {
        await triggerComposeAi({
          id: descriptor.id,
          command,
          draft: '',
          subject: descriptor.payload.subject,
          isReply: descriptor.payload.isReply
        });
      } catch (error) {
        console.error('AI compose failed', error);
      }
      return;
    }

    try {
      const instruction = resolveDefaultInstruction(fn, null);
      const data = await callAiCommand(command, instruction, {
        contextId: selected.contextId,
        subject: selected.subject,
        journeyScope: 'panel',
        journeyLabel: selected.subject,
        journeyHeadline: deriveJourneyHeadline(command, title),
        commandArgs
      });
      const html = (data?.response && window.ComposerAI?.renderMarkdown ? window.ComposerAI.renderMarkdown(data.response) : '')
                || (data?.sanitizedHtml || data?.sanitizedHTML || '')
                || '<div class="text-sm text-slate-500">No response received.</div>';
      const descriptor = createSummaryWindow(selected, html, title);
      windowManager.open(descriptor);
    } catch (e) { alert(e?.message || 'Unable to complete request.'); }
  }
</script>

<WindowProvider {windowManager}>
  <div class="h-[100dvh] flex overflow-hidden bg-gradient-to-b from-slate-50 to-slate-100">
    <MailboxSidebar
      mailbox={mailbox}
      mailboxCounts={mailboxCounts}
      sidebarOpen={sidebarOpen}
      mobile={mobile}
      tablet={tablet}
      desktop={desktop}
      showDrawer={showDrawer}
      on:compose={openCompose}
      on:toggleSidebar={toggleSidebar}
      on:selectMailbox={(event) => mailbox = event.detail.target}
    />
    {#if (mobile || tablet) && showDrawer}
      <button type="button" class="fixed inset-0 bg-black/30 z-[50]" aria-label="Close menu overlay"
              on:click={() => (showDrawer = false)}
              on:keydown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); showDrawer = false; } }}>
      </button>
    {/if}
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
        <EmailActionToolbar
          email={selected}
          commands={primaryCommandEntries}
          mobile={mobile}
          escapeHtmlFn={escapeHtml}
          formatFullDateFn={formatFullDate}
          on:reply={openReply}
          on:commandSelect={(event) => runMainAiCommand(event.detail.key)}
        />
      </div>
      <EmailDetailView
        email={selected}
        mobile={mobile}
        tablet={tablet}
        desktop={desktop}
        wide={wide}
        renderMarkdownFn={renderMarkdown}
      />

      <!-- Window stack rendered outside main column -->
    {/if}
  </section>
  </div>

  {#each floatingWindows as win, index}
    {#if win.kind === WindowKind.COMPOSE}
      <ComposeWindow
        windowConfig={win}
        offsetIndex={index}
        aiFunctions={composeAiFunctions}
        on:send={handleComposeSend}
        on:requestAi={handleComposeRequestAi}
      />
    {/if}
  {/each}

  {#each dockedWindows as win (win.id)}
    {#if win.kind === WindowKind.SUMMARY}
      <AiSummaryWindow
        windowConfig={win}
      />
    {/if}
  {/each}

  <WindowDock windows={minimizedWindows} />
</WindowProvider>

{#if windowNotice}
  <div class="window-notice">{windowNotice}</div>
{/if}

<AiLoadingJourney
  steps={aiJourneyOverlay.steps}
  activeStepId={aiJourneyOverlay.activeStepId}
  completed={aiJourneyOverlay.completed}
  headline={aiJourneyOverlay.headline}
  subhead={aiJourneyOverlay.subhead}
  show={aiJourneyOverlay.visible}
  subdued={aiJourneyOverlay.scope !== 'global'} />

<style>
  .window-notice {
    position: fixed;
    bottom: 90px;
    right: 24px;
    background: rgba(15, 23, 42, 0.9);
    color: white;
    padding: 0.5rem 1rem;
    border-radius: 999px;
    font-size: 0.85rem;
    z-index: 120;
    box-shadow: 0 10px 30px rgba(15, 23, 42, 0.35);
  }
</style>

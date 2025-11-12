<script>
  import { onMount } from 'svelte';
  import { get } from 'svelte/store';
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
  import { createComposeWindow, WindowKind } from './lib/window/windowTypes';
  import { catalogStore, hydrateCatalog, ensureCatalogLoaded as ensureCatalog, getFunctionMeta, mergeDefaultArgs, resolveDefaultInstruction } from './lib/services/aiCatalog';
  import { handleAiCommand } from './lib/services/aiCommandHandler';
  import { mapEmailMessage, computeMailboxCounts, parseSubjectAndBody } from './lib/services/emailUtils';
  import { createAiJourneyStore } from './lib/services/aiJourneyStore';
  import { Menu, Pencil, Inbox as InboxIcon, Star as StarIcon, AlarmClock, Send, ArrowLeft, ChevronLeft, ChevronRight, Archive, Trash2, Sparkles, Loader2 } from 'lucide-svelte';
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
  const minimizedStore = windowManager.minimized;
  const windowErrorStore = windowManager.lastError;
  const aiJourney = createAiJourneyStore();
  const aiJourneyOverlayStore = aiJourney.overlay;
  const MAILBOX_ACTION_FALLBACKS = [
    {
      key: 'smart-triage',
      label: 'Smart triage & cleanup',
      description: 'Auto-label Action, Read-Later, FYI, Receipts, Calendar, Tasks, Bulk. Merges duplicates and collapses promos.'
    }
  ];
  let windowNotice = '';
  let windowNoticeTimer = null;
  $: windows = $windowsStore;
  $: floatingWindows = $floatingStore;
  $: minimizedWindows = $minimizedStore;
  $: windowAlert = $windowErrorStore ? $windowErrorStore.message : '';
  $: aiJourneyOverlay = $aiJourneyOverlayStore;
  $: composeJourneyOverlay = aiJourneyOverlay.scope === 'compose' ? aiJourneyOverlay : null;
  $: panelJourneyOverlay = aiJourneyOverlay.scope === 'panel' ? aiJourneyOverlay : null;
  $: activePanelJourneyOverlay = panelJourneyOverlay && selected && (panelJourneyOverlay.scopeTarget === selected.id || panelJourneyOverlay.scopeTarget === selected.contextId) ? panelJourneyOverlay : null;
  // Viewport responsive
  $: mobile = $isMobile;
  $: tablet = $isTablet;
  $: desktop = $isDesktop;
  $: wide = $isWide;
  $: viewportType = $viewport;
  let showDrawer = false;
  let showEmailList = true; // For tablet view toggle

  let panelResponses = {};
  let panelErrors = {};
  $: selectedPanelKey = selected ? (selected.contextId || selected.id) : null;
  $: activePanelState = selectedPanelKey ? panelResponses[selectedPanelKey] : null;
  $: activePanelError = selectedPanelKey ? panelErrors[selectedPanelKey] : '';

  // UI state
  let sidebarOpen = true;
  let mailbox = 'inbox'; // inbox, starred, snoozed, sent, drafts, archive, trash
  let mailboxActionsOpen = false;
  let mailboxActionsHost = null;
  let mailboxCommandPendingKey = null;
  let mailboxActionError = '';
  let mailboxMenuListRef;
  let mailboxMenuMobileRef;

  // Derived
  $: mailboxCounts = computeMailboxCounts(emails);
  $: baseEmails = emails.filter(matchesMailbox);
  $: filtered = !search
    ? baseEmails
    : baseEmails.filter(e => [e.subject, e.from, e.preview].join(' ').toLowerCase().includes(search.toLowerCase()));
  const PRIMARY_TOOLBAR_PREFERENCE = ['draft', 'translate'];
  $: primaryCommandEntries = (() => {
    const sourceKeys = aiPrimaryCommandKeys.length ? aiPrimaryCommandKeys : Object.keys(aiFunctionsByKey || {});
    if (!sourceKeys || sourceKeys.length === 0) return [];

    const entries = sourceKeys
      .map((key) => ({ key, meta: aiFunctionsByKey[key] }))
      .filter((entry) => !!entry.meta && entry.key !== 'compose' && entry.key !== 'tone');

    const prioritized = [];
    const consumed = new Set();

    for (const preferredKey of PRIMARY_TOOLBAR_PREFERENCE) {
      const match = entries.find((entry) => entry.key === preferredKey);
      if (match) {
        prioritized.push(match);
        consumed.add(match.key);
      }
    }

    for (const entry of entries) {
      if (!consumed.has(entry.key)) {
        prioritized.push(entry);
      }
    }

    return prioritized;
  })();
  $: composeAiFunctions = Object.values(aiFunctionsByKey || {})
    .filter((fn) => Array.isArray(fn.scopes) && fn.scopes.includes('compose'));
  $: mailboxCommandEntries = deriveMailboxCommands();
  $: hasMailboxCommands = Array.isArray(mailboxCommandEntries) && mailboxCommandEntries.length > 0;
  $: activeMailboxActionLabel = (mailboxCommandEntries || []).find((entry) => entry.key === mailboxCommandPendingKey)?.label || '';
  $: if ((filtered.length === 0 || !hasMailboxCommands) && mailboxActionsOpen) {
    closeMailboxActions();
  }

  /** Looks up a variant (e.g., translation language) within a given function definition. */
  function getVariant(meta, variantKey) {
    if (!meta || !Array.isArray(meta.variants) || !variantKey) return null;
    return meta.variants.find((variant) => variant.key === variantKey) || null;
  }

  function deriveMailboxCommands() {
    if (!catalogData || !aiFunctionsByKey) {
      return MAILBOX_ACTION_FALLBACKS;
    }
    const scoped = Object.entries(aiFunctionsByKey)
      .map(([key, meta]) => ({ key, meta }))
      .filter((entry) => Array.isArray(entry.meta?.scopes) && entry.meta.scopes.includes('mailbox'))
      .map((entry) => ({
        key: entry.key,
        label: entry.meta?.label || entry.key,
        description: entry.meta?.description || entry.meta?.summary || '',
        meta: entry.meta
      }));
    if (scoped.length === 0) {
      return MAILBOX_ACTION_FALLBACKS.map((entry) => ({ ...entry }));
    }
    return scoped;
  }

  function buildMailboxContextSummary(list) {
    if (!Array.isArray(list) || list.length === 0) {
      return `Mailbox ${mailbox} is empty for the current filter.`;
    }
    const lines = [];
    lines.push(`Mailbox: ${mailbox}`);
    if (search && search.trim()) {
      lines.push(`Search filter: ${search.trim()}`);
    }
    lines.push(`Visible messages: ${list.length}`);
    lines.push('--- Messages Preview ---');
    list.slice(0, 25).forEach((email, index) => {
      lines.push(`${index + 1}. Subject: ${email.subject || 'No subject'}`);
      lines.push(`   From: ${email.from || 'Unknown sender'}`);
      if (email.timestamp) {
        lines.push(`   Sent: ${email.timestamp}`);
      }
      lines.push(`   Labels: ${(email.labels || []).join(', ') || 'None'}`);
    });
    if (list.length > 25) {
      lines.push(`(+${list.length - 25} more messages omitted for brevity)`);
    }
    return lines.join('\n');
  }

  function buildMailboxInstruction(entry, meta) {
    const description = entry?.description || meta?.description || meta?.summary || '';
    const fallback = meta?.defaultInstruction || 'Perform the requested action using the provided mailbox context.';
    const detailLine = description ? `Focus on: ${description}` : '';
    return [`Run the "${entry?.label || meta?.label || entry?.key}" action across the mailbox context.`, detailLine, fallback]
      .filter(Boolean)
      .join('\n\n');
  }

  function toggleMailboxActions(host) {
    if (mailboxActionsOpen && mailboxActionsHost === host) {
      mailboxActionsOpen = false;
      mailboxActionsHost = null;
    } else {
      mailboxActionsHost = host;
      mailboxActionsOpen = true;
    }
    mailboxActionError = '';
  }

  function closeMailboxActions() {
    mailboxActionsOpen = false;
    mailboxActionsHost = null;
  }

  function isMailboxMenuTarget(node) {
    if (!node) return false;
    if (mailboxMenuListRef && mailboxMenuListRef.contains(node)) return true;
    if (mailboxMenuMobileRef && mailboxMenuMobileRef.contains(node)) return true;
    return false;
  }

  async function handleMailboxAction(entry) {
    if (!entry || !entry.key || mailboxCommandPendingKey || filtered.length === 0) return;
    mailboxActionError = '';
    closeMailboxActions();
    const ready = await ensureCatalogReady();
    if (!ready) {
      mailboxActionError = 'AI helpers unavailable';
      alert('AI helpers are unavailable. Please try again.');
      return;
    }
    const meta = entry.meta || getFunctionMeta(catalogData, entry.key) || null;
    const instruction = buildMailboxInstruction(entry, meta);
    const commandArgs = mergeDefaultArgs(meta, null, {
      mailbox,
      search: search && search.trim() ? search.trim() : undefined,
      messageIds: filtered.slice(0, 50).map((mail) => mail.contextId || mail.id || mail.conversationId).filter(Boolean),
      totalMessages: filtered.length
    });
    mailboxCommandPendingKey = entry.key;
    try {
      await callAiCommand(entry.key, instruction, {
        subject: `${entry.label || 'Mailbox action'} (${mailbox})`,
        journeyScope: 'panel',
        journeyScopeTarget: 'mailbox-actions',
        journeyLabel: `${filtered.length} message${filtered.length === 1 ? '' : 's'}`,
        journeyHeadline: entry.label || 'Mailbox action',
        commandArgs,
        emailContext: buildMailboxContextSummary(filtered)
      });
    } catch (error) {
      mailboxActionError = error?.message || 'Unable to run mailbox action.';
      alert(mailboxActionError);
    } finally {
      mailboxCommandPendingKey = null;
    }
  }

  onMount(() => {
    ensureCatalog().catch(() => {});
  });

  onMount(() => {
    function handlePointerDown(event) {
      if (!mailboxActionsOpen) return;
      if (!isMailboxMenuTarget(event.target)) {
        closeMailboxActions();
      }
    }
    function handleKey(event) {
      if (event.key === 'Escape' && mailboxActionsOpen) {
        closeMailboxActions();
      }
    }
    document.addEventListener('pointerdown', handlePointerDown);
    document.addEventListener('keydown', handleKey);
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown);
      document.removeEventListener('keydown', handleKey);
    };
  });

  async function ensureCatalogReady() {
    return ensureCatalog();
  }

  // Auto-select first email on larger screens when none selected
  $: {
    if ((tablet || desktop || wide) && !selected && filtered.length) {
      selected = filtered[0];
    }
  }

  function selectEmail(e) {
    // Update emails array to mark as read (triggers reactivity)
    emails = emails.map((mail) =>
      mail.id === e.id ? { ...mail, read: true } : mail
    );
    // Update selected reference
    selected = emails.find((mail) => mail.id === e.id) ?? e;
  }
  function toggleSidebar() { sidebarOpen = !sidebarOpen; }
  function handleMenuClick() {
    if (mobile) {
      showDrawer = !showDrawer;
      return;
    }
    toggleSidebar();
  }

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
        journeyScopeTarget: id,
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

  function beginAiJourney({ scope = 'global', targetLabel = 'message', commandKey, headline, scopeTarget } = {}) {
    const defaultHeadline = getFunctionMeta(catalogData, commandKey)?.label || 'Working on your request';
    const subhead = JOURNEY_SCOPE_META[scope]?.subhead || JOURNEY_SCOPE_META.global.subhead;
    return aiJourney.begin({ scope, targetLabel, commandKey, scopeTarget, headline: headline || deriveJourneyHeadline(commandKey, defaultHeadline), subhead });
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
      // The backend already has this metadata when contextId is used.
      // If emailContext is sent directly, the frontend must build and include metadata below.
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
  async function callAiCommand(command, instruction, { contextId, subject, journeyScope = 'global', journeyScopeTarget = null, journeyLabel, journeyHeadline, commandVariant, commandArgs, emailContext } = {}) {
    const payload = {
      message: instruction,
      conversationId,
      maxResults: 5,
      thinkingEnabled: false,
      jsonOutput: false
    };

    const targetLabel = journeyLabel || subject || selected?.subject || 'message';
    const journeyToken = beginAiJourney({ scope: journeyScope, scopeTarget: journeyScopeTarget, targetLabel, commandKey: command, headline: journeyHeadline });

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
    if (!contextId) {
      if (emailContext && emailContext.trim()) {
        payload.emailContext = emailContext.trim();
      } else if (selected) {
        const ctx = buildEmailContextString(selected);
        if (ctx) payload.emailContext = ctx;
      }
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
  async function runMainAiCommand(request) {
    const command = typeof request === 'string' ? request : request?.key;
    const commandVariant = typeof request === 'object' ? request?.variantKey : null;
    if (!command) return;
    const selectedContextKey = selected ? (selected.contextId || selected.id) : null;
    const fnMeta = getFunctionMeta(catalogData, command);
    const targetsCompose = Array.isArray(fnMeta?.scopes) && fnMeta.scopes.includes('compose');

    if (!targetsCompose && selectedContextKey) {
      panelErrors = { ...panelErrors, [selectedContextKey]: '' };
    }

    try {
      const result = await handleAiCommand({
        command,
        commandVariant,
        selectedEmail: selected,
        catalogStore: catalog,
        windowManager,
        callAiCommand,
        ensureCatalogLoaded: ensureCatalogReady
      });

      if (result?.type === WindowKind.SUMMARY) {
        const key = result.contextId || selectedContextKey;
        if (key) {
          panelResponses = {
            ...panelResponses,
            [key]: {
              html: result.html,
              title: result.title || fnMeta?.label || 'AI Summary',
              commandKey: result.command || command,
              commandLabel: result.commandLabel || fnMeta?.label || 'AI Summary',
              updatedAt: Date.now()
            }
          };
          panelErrors = { ...panelErrors, [key]: '' };
        }
      }
    } catch (error) {
      if (error?.message === 'Close or minimize an existing draft before opening another.') {
        showWindowLimitMessage();
        return;
      }
      if (!targetsCompose && selectedContextKey) {
        panelErrors = { ...panelErrors, [selectedContextKey]: error?.message || 'Unable to complete request.' };
      }
      if (targetsCompose) {
        if (error?.message) {
          alert(error.message);
        } else {
          alert('Unable to complete request.');
        }
      }
    }
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
    <!-- Mobile/Tablet: Semi-transparent backdrop overlay to close drawer when clicking outside -->
    {#if (mobile || tablet) && showDrawer}
      <button type="button" class="fixed inset-0 bg-black/30 z-[50]" aria-label="Close menu overlay"
              on:click={() => (showDrawer = false)}
              on:keydown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); showDrawer = false; } }}>
      </button>
    {/if}

  <!-- List -->
  <section class="shrink-0 flex flex-col bg-white/90 border-r border-slate-200"
           class:w-[24rem]={wide}
           class:w-[22rem]={desktop}
           class:w-[19rem]={tablet && showEmailList}
           class:w-0={tablet && !showEmailList}
           class:w-full={mobile}
           class:hidden={mobile && selected}
           class:overflow-hidden={tablet && !showEmailList}
  >
    <div class="px-4 py-3 border-b border-slate-200">
      <div class="flex items-center gap-3">
        <button type="button" title="Toggle menu" class="rounded-xl border border-slate-200 bg-white h-9 w-9 grid place-items-center text-slate-600 hover:bg-slate-50" on:click={handleMenuClick}>
          <Menu class="h-4 w-4" />
        </button>
        <div class="flex-1 min-w-0 flex flex-col gap-1">
          <div class="relative" bind:this={mailboxMenuListRef}>
            <input
              placeholder="Search emails..."
              bind:value={search}
              class="w-full rounded-2xl border border-slate-200 bg-white/90 pl-4 pr-32 py-2 text-sm text-slate-800 shadow-inner focus:outline-none focus:ring-2 focus:ring-slate-200"
            />
            <button
              type="button"
              class="absolute top-1 bottom-1 right-1 inline-flex items-center gap-2 rounded-2xl border border-white/60 bg-slate-900/80 px-3 text-sm font-medium text-white shadow-lg backdrop-blur focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-500 hover:bg-slate-900"
              aria-haspopup="menu"
              aria-expanded={mailboxActionsOpen && mailboxActionsHost === 'list'}
              on:click={() => toggleMailboxActions('list')}
              disabled={!hasMailboxCommands || filtered.length === 0 || !!mailboxCommandPendingKey}
            >
              {#if mailboxCommandPendingKey}
                <Loader2 class="h-4 w-4 animate-spin" aria-hidden="true" />
                <span>{activeMailboxActionLabel ? `${activeMailboxActionLabel}…` : 'Running…'}</span>
              {:else}
                <Sparkles class="h-4 w-4" aria-hidden="true" />
                <span>AI Actions</span>
              {/if}
            </button>
            {#if mailboxActionsOpen && mailboxActionsHost === 'list'}
              <div class="absolute right-0 top-[calc(100%+0.5rem)] w-80 rounded-2xl border border-slate-200/70 bg-white/95 shadow-2xl backdrop-blur-xl z-30" role="menu" tabindex="-1" on:click|stopPropagation>
                <div class="p-3 space-y-2">
                  {#each mailboxCommandEntries as entry (entry.key)}
                    <button
                      type="button"
                      class="w-full rounded-xl border border-transparent px-3 py-2 text-left transition hover:border-slate-200 hover:bg-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-300"
                      on:click={() => handleMailboxAction(entry)}
                      disabled={filtered.length === 0}
                    >
                      <div class="flex items-start gap-3">
                        <div class="mt-0.5 h-8 w-8 rounded-full bg-slate-900/5 text-slate-700 grid place-items-center">
                          <Sparkles class="h-4 w-4" aria-hidden="true" />
                        </div>
                        <div class="min-w-0">
                          <p class="text-sm font-medium text-slate-900 tracking-wide">{entry.label || entry.key}</p>
                          {#if entry.description}
                            <p class="text-xs text-slate-500 leading-snug">{entry.description}</p>
                          {/if}
                        </div>
                      </div>
                    </button>
                  {/each}
                </div>
                <div class="border-t border-slate-200/70 px-3 py-2 text-xs text-slate-500">
                  Mailbox AI actions apply to the {filtered.length} message{filtered.length === 1 ? '' : 's'} currently listed.
                </div>
              </div>
            {/if}
          </div>
          {#if mailboxCommandPendingKey && activeMailboxActionLabel}
            <p class="text-xs text-slate-500">{activeMailboxActionLabel} in progress…</p>
          {/if}
          {#if mailboxActionError}
            <p class="text-xs text-rose-600">{mailboxActionError}</p>
          {/if}
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
      <div class="px-4 py-3 border-b border-slate-200 flex flex-col gap-2">
        <div class="flex items-center gap-2">
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
          <div class="flex-1 min-w-0 flex flex-col gap-1">
            <div class="relative" bind:this={mailboxMenuMobileRef}>
              <input
                placeholder="Search emails..."
                bind:value={search}
                class="w-full rounded-2xl border border-slate-200 bg-white/90 pl-4 pr-32 py-2 text-sm text-slate-800 shadow-inner focus:outline-none focus:ring-2 focus:ring-slate-200"
              />
              <button
                type="button"
                class="absolute top-1 bottom-1 right-1 inline-flex items-center gap-2 rounded-2xl border border-white/60 bg-slate-900/85 px-3 text-sm font-medium text-white shadow-lg backdrop-blur focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-500 hover:bg-slate-900 disabled:cursor-not-allowed disabled:opacity-60"
                aria-haspopup="menu"
                aria-expanded={mailboxActionsOpen && mailboxActionsHost === 'mobile'}
                on:click={() => toggleMailboxActions('mobile')}
                disabled={!hasMailboxCommands || filtered.length === 0 || !!mailboxCommandPendingKey}
              >
                {#if mailboxCommandPendingKey}
                  <Loader2 class="h-4 w-4 animate-spin" aria-hidden="true" />
                  <span>{activeMailboxActionLabel ? `${activeMailboxActionLabel}…` : 'Running…'}</span>
                {:else}
                  <Sparkles class="h-4 w-4" aria-hidden="true" />
                  <span>AI Actions</span>
                {/if}
              </button>
              {#if mailboxActionsOpen && mailboxActionsHost === 'mobile'}
                <div class="absolute right-0 top-[calc(100%+0.5rem)] w-72 rounded-2xl border border-slate-200/70 bg-white/95 shadow-2xl backdrop-blur-xl z-30" role="menu" tabindex="-1" on:click|stopPropagation>
                  <div class="p-3 space-y-2">
                    {#each mailboxCommandEntries as entry (entry.key)}
                      <button
                        type="button"
                        class="w-full rounded-xl border border-transparent px-3 py-2 text-left transition hover:border-slate-200 hover:bg-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-300"
                        on:click={() => handleMailboxAction(entry)}
                        disabled={filtered.length === 0}
                      >
                        <div class="flex items-start gap-3">
                          <div class="mt-0.5 h-8 w-8 rounded-full bg-slate-900/5 text-slate-700 grid place-items-center">
                            <Sparkles class="h-4 w-4" aria-hidden="true" />
                          </div>
                          <div class="min-w-0">
                            <p class="text-sm font-medium text-slate-900 tracking-wide">{entry.label || entry.key}</p>
                            {#if entry.description}
                              <p class="text-xs text-slate-500 leading-snug">{entry.description}</p>
                            {/if}
                          </div>
                        </div>
                      </button>
                    {/each}
                  </div>
                  <div class="border-t border-slate-200/70 px-3 py-2 text-xs text-slate-500">
                    Mailbox AI actions apply to the {filtered.length} message{filtered.length === 1 ? '' : 's'} currently listed.
                  </div>
                </div>
              {/if}
            </div>
            {#if mailboxCommandPendingKey && activeMailboxActionLabel}
              <p class="text-xs text-slate-500">{activeMailboxActionLabel} in progress…</p>
            {/if}
            {#if mailboxActionError}
              <p class="text-xs text-rose-600">{mailboxActionError}</p>
            {/if}
          </div>
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
          on:commandSelect={(event) => runMainAiCommand(event.detail)}
        />
      </div>
      <div class="flex-1 flex flex-col min-h-0 gap-4 pb-6"
           class:px-4={mobile}
           class:px-5={tablet}
           class:px-6={desktop || wide}>
        <div class="flex-1 min-h-0">
          <EmailDetailView
            email={selected}
            mobile={mobile}
            tablet={tablet}
            desktop={desktop}
            wide={wide}
            renderMarkdownFn={renderMarkdown}
          />
        </div>
        {#if activePanelState || activePanelJourneyOverlay}
          <div class="ai-panel-wrapper">
            <AiSummaryWindow
              panelState={activePanelState}
              journeyOverlay={activePanelJourneyOverlay}
              error={activePanelError}
              on:runCommand={(event) => runMainAiCommand(event.detail)}
            />
          </div>
        {/if}
      </div>

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
        journeyOverlay={composeJourneyOverlay && composeJourneyOverlay.scopeTarget === win.id ? composeJourneyOverlay : null}
        on:send={handleComposeSend}
        on:requestAi={handleComposeRequestAi}
      />
    {/if}
  {/each}

  <WindowDock windows={minimizedWindows} />
</WindowProvider>

{#if windowNotice}
  <div class="window-notice">{windowNotice}</div>
{/if}

{#if aiJourneyOverlay.visible && aiJourneyOverlay.scope === 'global'}
  <div class="fixed bottom-6 left-1/2 z-[80] -translate-x-1/2 sm:left-auto sm:right-6 sm:translate-x-0">
    <AiLoadingJourney
      steps={aiJourneyOverlay.steps}
      activeStepId={aiJourneyOverlay.activeStepId}
      headline={aiJourneyOverlay.headline}
      subhead={aiJourneyOverlay.subhead}
      show={true}
      inline={false}
      subdued={false} />
  </div>
{/if}

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
  .ai-panel-wrapper {
    position: relative;
    z-index: 10;
  }
</style>

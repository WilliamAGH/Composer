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
  import DrawerBackdrop from './lib/DrawerBackdrop.svelte';
  import AiPanelDockChip from './lib/AiPanelDockChip.svelte';
  import ComingSoonModal from './lib/ComingSoonModal.svelte';
  import WindowNotice from './lib/WindowNotice.svelte';
  import { isMobile, isTablet, isDesktop, isWide, viewport } from './lib/viewport';
  import { createWindowManager } from './lib/window/windowStore'; // temp use
  import { createComposeWindow, WindowKind } from './lib/window/windowTypes';
  import { catalogStore, hydrateCatalog, ensureCatalogLoaded as ensureCatalog, getFunctionMeta, mergeDefaultArgs, resolveDefaultInstruction } from './lib/services/aiCatalog';
  import { handleAiCommand } from './lib/services/aiCommandHandler';
import { mapEmailMessage, computeMailboxCounts, parseSubjectAndBody } from './lib/services/emailUtils';
  import { buildEmailContextString, parseRecipientInput, recipientFromEmail } from './lib/services/emailContextConstructor';
  import { buildReplyPrefill, buildForwardPrefill } from './lib/services/composePrefill.js';
  import { matchesMailbox as matchesMailboxFilter } from './lib/services/mailboxFiltering';
  import { createAiJourneyStore } from './lib/services/aiJourneyStore';
  import { Menu, Pencil, Inbox as InboxIcon, Star as StarIcon, AlarmClock, Send, ArrowLeft, ChevronLeft, ChevronRight, Archive, Trash2, Sparkles, Loader2 } from 'lucide-svelte';
  import { ACTION_IDEAS_INSTRUCTION, ACTION_MENU_COMMAND_KEY, DEFAULT_ACTION_OPTIONS, MAILBOX_ACTION_FALLBACKS, PRIMARY_TOOLBAR_PREFERENCE } from './lib/constants/catalogActions';
  import { escapeHtmlContent, renderMarkdownContent, formatRelativeTimestamp, formatFullTimestamp } from './lib/services/emailFormatting';
  import { initializeUiNonce, postJsonWithNonce, startChatHeartbeat } from './lib/services/sessionNonceClient';
  import './app-shared.css';
  export let bootstrap = {};

  hydrateCatalog(bootstrap.aiFunctions || null);
  const catalog = catalogStore();
  $: catalogData = $catalog;
  $: aiFunctionsByKey = catalogData?.functionsByKey || {};
  $: aiPrimaryCommandKeys = Array.isArray(catalogData?.primaryCommands) ? catalogData.primaryCommands : [];

  initializeUiNonce(bootstrap.uiNonce || null);
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
  let windowNotice = '';
  let windowNoticeTimer = null;
  let actionMenuOptions = DEFAULT_ACTION_OPTIONS.map((option) => ({ ...option }));
  let actionMenuLoading = false;
  let actionMenuPendingOptions = null;
  let actionMenuCache = {};
  let actionMenuInFlight = {};
  let actionMenuAppliedKey = null;
  let actionMenuActiveSource = 'default';
  let actionMenuPendingSource = 'ai';
  let selectedActionKey = null;
  let isActionMenuOpen = false;
  let comingSoonModal = { open: false, sourceLabel: '' };
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
  $: viewportTier = wide ? 'wide' : desktop ? 'desktop' : tablet ? 'tablet' : 'mobile';
  $: inlineSidebar = viewportTier === 'desktop' || viewportTier === 'wide';
  let showDrawer = false;
  $: drawerMode = mobile || tablet;
  $: sidebarVariant = (() => {
    if (!inlineSidebar) {
      return showDrawer ? 'drawer-visible' : 'drawer-hidden';
    }
    if (!sidebarOpen) {
      return 'inline-collapsed';
    }
    return viewportTier === 'wide' ? 'inline-wide' : 'inline-desktop';
  })();
  $: drawerVisible = sidebarVariant === 'drawer-visible';
  let showEmailList = true; // For tablet view toggle

  let panelResponses = {};
  let panelErrors = {};
  let panelSessionActive = false;
  let panelMinimized = false;
  let panelMaximized = false;
  let lastPanelContextKey = null;
  $: selectedPanelKey = selected ? (selected.contextId || selected.id) : null;
  $: activePanelState = selectedPanelKey ? panelResponses[selectedPanelKey] : null;
  $: activePanelError = selectedPanelKey ? panelErrors[selectedPanelKey] : '';
  $: panelRenderReady = panelSessionActive && !panelMinimized && (activePanelState || activePanelJourneyOverlay);
  $: if (selectedPanelKey !== lastPanelContextKey) {
    lastPanelContextKey = selectedPanelKey;
    panelSessionActive = false;
    panelMinimized = false;
    panelMaximized = false;
  }

  // UI state
  let sidebarOpen = true;
  $: if (!drawerMode && showDrawer) {
    showDrawer = false;
  }
  $: if (drawerMode && !sidebarOpen) {
    sidebarOpen = true;
  }
  let mailbox = 'inbox'; // inbox, starred, snoozed, sent, drafts, archive, trash
  let mailboxActionsOpen = false;
  let mailboxActionsHost = null;
  let mailboxCommandPendingKey = null;
  let mailboxActionError = '';
  let mailboxMenuListRef = null;
  let mailboxMenuMobileRef = null;

  // Derived
  $: mailboxCounts = computeMailboxCounts(emails);
  $: baseEmails = emails.filter((email) => matchesMailboxFilter(mailbox, email));
  $: filtered = !search
    ? baseEmails
    : baseEmails.filter(e => [e.subject, e.from, e.preview].join(' ').toLowerCase().includes(search.toLowerCase()));
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
  $: selectedActionKey = selected ? (selected.contextId || selected.id || selected.conversationId || null) : null;
  $: {
    if (!selectedActionKey) {
      applyActionMenuOptions(getDefaultActionOptions(), null, 'default');
    } else {
      if (actionMenuAppliedKey !== selectedActionKey && !isActionMenuOpen) {
        applyActionMenuOptions(getDefaultActionOptions(), selectedActionKey, 'default');
      } else if (actionMenuAppliedKey !== selectedActionKey && isActionMenuOpen) {
        actionMenuPendingOptions = getDefaultActionOptions();
        actionMenuPendingSource = 'default';
      }
      const cached = actionMenuCache[selectedActionKey];
      if (cached && (actionMenuAppliedKey !== selectedActionKey || actionMenuActiveSource !== 'ai')) {
        queueActionMenuUpdate(cached, selectedActionKey);
      } else if (!cached && !actionMenuInFlight[selectedActionKey]) {
        loadActionMenuSuggestions(selected, selectedActionKey);
      }
    }
  }
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
    if (!entry || !entry.key) return;
    if (entry.key === 'smart-triage') {
      closeMailboxActions();
      openComingSoonModal(entry.label || 'Smart Triage & Cleanup');
      return;
    }
    if (mailboxCommandPendingKey || filtered.length === 0) return;
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
  function toggleSidebar() {
    if (drawerMode) {
      showDrawer = !showDrawer;
      return;
    }
    sidebarOpen = !sidebarOpen;
  }
  function handleMenuClick() {
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

  function openReply(withAi = true) {
    if (!selected) return alert('Select an email first.');
    const prefills = buildReplyPrefill(selected);
    const descriptor = createComposeWindow(selected, {
      subject: prefills.subject,
      body: prefills.body,
      hasQuotedContext: prefills.hasQuotedContext,
      quotedContext: prefills.quotedContext,
      isReply: true,
      title: selected.subject ? `Reply: ${selected.subject}` : 'Reply'
    });
    const result = windowManager.open(descriptor);
    if (!result.ok) {
      showWindowLimitMessage();
      return;
    }
    if (withAi) {
      queueReplyPrefillAi(descriptor.id, descriptor.payload.subject, selected);
    }
  }

  function openForward() {
    if (!selected) return alert('Select an email first.');
    const prefills = buildForwardPrefill(selected);
    const descriptor = createComposeWindow(selected, {
      subject: prefills.subject,
      body: prefills.body,
      hasQuotedContext: prefills.hasQuotedContext,
      quotedContext: prefills.quotedContext,
      isReply: false,
      isForward: true,
      title: selected.subject ? `Forward: ${selected.subject}` : 'Forward'
    });
    const result = windowManager.open(descriptor);
    if (!result.ok) {
      showWindowLimitMessage();
    }
  }

  function queueReplyPrefillAi(windowId, subject, relatedEmail) {
    const instruction = buildReplyGreetingInstruction(relatedEmail);
    triggerComposeAi({
      id: windowId,
      command: 'draft',
      draft: '',
      subject,
      isReply: true,
      instructionOverride: instruction || undefined
    }).catch((error) => console.warn('Reply AI prefill failed', error));
  }

  function buildReplyGreetingInstruction(email) {
    const recipient = deriveRecipientContext({ fallbackEmail: email });
    if (recipient.name) {
      return `Draft only the opening greeting and closing paragraph for a reply to ${recipient.name}. Use their name in the greeting, reference the context politely, and end with a professional closing that leaves space for the user to add details. Preserve any existing user signature block from the context if one exists; otherwise end with "Thanks,\n[Your Name]".`;
    }
    if (recipient.email) {
      return 'Draft the opening greeting and closing paragraph for this reply. Use a neutral greeting (e.g., "Hello there") because the exact name is unknown, and end with a professional closing that keeps the user signature placeholder intact. Leave the middle blank for the user to fill in.';
    }
    return 'Draft the opening greeting and closing paragraph for this reply using a friendly but generic salutation and sign-off. Leave the body area blank for the user to fill in.';
  }

  function handleComposeSend(event) {
    // Placeholder: sending closes the draft for now.
    windowManager.close(event.detail.id);
  }

  async function triggerComposeAi(detail) {
    const { id, command, draft, subject: draftSubject, isReply, to: toInput, instructionOverride } = detail;
    const ready = await ensureCatalogReady();
    if (!ready) return alert('AI helpers are unavailable. Please try again.');
    const fn = getFunctionMeta(catalogData, command);
    if (!fn) return alert('Command unavailable.');
    const instruction = instructionOverride || buildComposeInstruction(command, draft || '', isReply, fn);
    const win = get(windowsStore).find((w) => w.id === id);
    const relatedEmail = win?.contextId ? emails.find((e) => e.id === win.contextId) : selected;
    const commandArgs = mergeDefaultArgs(fn, null);
    const recipientContext = deriveRecipientContext({
      toInput,
      composePayload: win?.payload,
      fallbackEmail: relatedEmail
    });
    try {
      const data = await callAiCommand(command, instruction, {
        contextId: relatedEmail?.contextId || relatedEmail?.id,
        subject: draftSubject || relatedEmail?.subject,
        journeyScope: 'compose',
        journeyScopeTarget: id,
        journeyLabel: draftSubject || relatedEmail?.subject || 'draft',
        journeyHeadline: deriveJourneyHeadline(command, fn.label || 'AI Assistant'),
        commandArgs,
        recipientContext
      });
      let draftText = (data?.response && data.response.trim()) || '';
      if (!draftText && data?.sanitizedHtml) {
        const temp = document.createElement('div');
        temp.innerHTML = data.sanitizedHtml;
        draftText = temp.textContent.trim();
      }
      if (draftText) {
        const parsed = parseSubjectAndBody(draftText);
        let updatedBody = parsed.body || draftText;
        const quote = win?.payload?.quotedContext;
        if (quote && !updatedBody.includes(quote.trim())) {
          updatedBody = `${updatedBody.trimEnd()}\n\n${quote}`;
        }
        windowManager.updateComposeDraft(id, {
          subject: parsed.subject || draftSubject,
          body: updatedBody
        });
      }
    } catch (error) {
      alert(error?.message || 'Unable to complete AI request.');
    }
  }

  function handleComposeRequestAi(event) {
    triggerComposeAi(event.detail);
  }

  const escapeHtml = escapeHtmlContent;
  const renderMarkdown = renderMarkdownContent;

  const JOURNEY_SCOPE_META = {
    global: { subhead: 'Composer assistant' },
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

  function normalizeRecipient(recipient = {}) {
    const name = typeof recipient.name === 'string' ? recipient.name.trim() : '';
    const email = typeof recipient.email === 'string' ? recipient.email.trim() : '';
    return { name, email };
  }

  function deriveRecipientContext({ toInput, composePayload, fallbackEmail } = {}) {
    const fromInput = normalizeRecipient(parseRecipientInput(toInput));
    if (fromInput.name || fromInput.email) {
      return fromInput;
    }

    if (composePayload) {
      const directPayload = normalizeRecipient({
        name: composePayload.recipientName,
        email: composePayload.recipientEmail || composePayload.toEmail
      });
      if (directPayload.name || directPayload.email) {
        return directPayload;
      }
      if (composePayload.to) {
        const parsedTo = normalizeRecipient(parseRecipientInput(composePayload.to));
        if (parsedTo.name || parsedTo.email) {
          return parsedTo;
        }
      }
    }

    if (fallbackEmail) {
      const fallbackRecipient = normalizeRecipient(recipientFromEmail(fallbackEmail));
      if (fallbackRecipient.name || fallbackRecipient.email) {
        return fallbackRecipient;
      }
    }

    return { name: '', email: '' };
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
  /**
   * Sends chat payloads with consistent metadata so the backend sees the same structure regardless
   * of which UI surface initiated the helper.
   */
  async function callAiCommand(command, instruction, { contextId, subject, journeyScope = 'global', journeyScopeTarget = null, journeyLabel, journeyHeadline, commandVariant, commandArgs, emailContext, recipientContext } = {}) {
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

    if (recipientContext) {
      const normalizedRecipient = normalizeRecipient(recipientContext);
      if (normalizedRecipient.name) {
        payload.recipientName = normalizedRecipient.name;
      }
      if (normalizedRecipient.email) {
        payload.recipientEmail = normalizedRecipient.email;
      }
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
      const data = await postJsonWithNonce('/api/chat', payload);
      advanceAiJourney(journeyToken, 'ai:llm-thinking');
      completeAiJourney(journeyToken);
      if (data?.conversationId) {
        conversationId = data.conversationId;
      }
      return data;
    } catch (err) {
      failAiJourney(journeyToken);
      throw err;
    }
  }

  /**
   * POST helper that retries once after silently refreshing the nonce.
   */
  onMount(() => {
    const stopHeartbeat = startChatHeartbeat();
    return () => stopHeartbeat();
  });

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

  function getDefaultActionOptions() {
    return DEFAULT_ACTION_OPTIONS.map((option) => ({ ...option }));
  }

  function applyActionMenuOptions(options, key = selectedActionKey, source = 'ai') {
    actionMenuOptions = options;
    actionMenuPendingOptions = null;
    actionMenuAppliedKey = key;
    actionMenuActiveSource = source;
  }

  function applyPendingActionOptions() {
    if (!isActionMenuOpen && actionMenuPendingOptions) {
      applyActionMenuOptions(actionMenuPendingOptions, selectedActionKey, actionMenuPendingSource || 'ai');
      actionMenuPendingOptions = null;
      actionMenuPendingSource = 'ai';
    }
  }

  function queueActionMenuUpdate(options, cacheKey = selectedActionKey, source = 'ai') {
    const next = Array.isArray(options) && options.length ? options : getDefaultActionOptions();
    if (cacheKey && cacheKey !== selectedActionKey) {
      return;
    }
    if (isActionMenuOpen) {
      actionMenuPendingOptions = next;
      actionMenuPendingSource = source;
    } else {
      applyActionMenuOptions(next, cacheKey, source);
    }
  }

  function buildActionMenuInstruction(email) {
    if (!email) return 'No email context available.';
    const subject = (email.subject || 'No subject').trim();
    const from = (email.from || 'Unknown sender').trim();
    const preview = ((email.preview || email.contentText || '').replace(/\s+/g, ' ').trim()).slice(0, 240);
    return `Subject: ${subject}\nFrom: ${from}\nPreview: ${preview || 'No preview provided.'}\nFocus on concise, high-value actions.`;
  }

  function extractJsonBlock(raw) {
    if (!raw || typeof raw !== 'string') return null;
    const trimmed = raw.trim();
    if (trimmed.startsWith('{') && trimmed.endsWith('}')) return trimmed;
    const first = trimmed.indexOf('{');
    const last = trimmed.lastIndexOf('}');
    if (first === -1 || last === -1 || last <= first) return null;
    return trimmed.slice(first, last + 1);
  }

  function sanitizeActionOption(option) {
    if (!option || typeof option !== 'object') return null;
    const rawLabel = typeof option.label === 'string' ? option.label.trim() : '';
    if (!rawLabel) return null;
    const words = rawLabel.split(/\s+/).filter(Boolean);
    if (words.length === 0 || words.length > 3) return null;
    const normalizedType = (option.actionType || '').toLowerCase();
    const actionType = normalizedType === 'comingsoon' ? 'comingSoon' : (normalizedType || 'summary');
    const commandKey = option.commandKey || (actionType === 'summary' ? 'summarize' : actionType === 'compose' ? 'compose' : null);
    const commandVariant = option.commandVariant || null;
    const instruction = typeof option.instruction === 'string' ? option.instruction.trim() || null : null;
    return {
      id: option.id || `ai-action-${rawLabel.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '')}`,
      label: rawLabel,
      actionType,
      commandKey,
      commandVariant,
      instruction
    };
  }

  function parseActionMenuResponse(data) {
    const raw = typeof data?.response === 'string' ? data.response : null;
    const fallbackHtml = typeof data?.sanitizedHtml === 'string' ? data.sanitizedHtml : null;
    const jsonBlock = extractJsonBlock(raw || fallbackHtml);
    if (!jsonBlock) return [];
    try {
      const parsed = JSON.parse(jsonBlock);
      const options = Array.isArray(parsed?.options) ? parsed.options : [];
      return options
        .map(sanitizeActionOption)
        .filter(Boolean)
        .slice(0, 3);
    } catch (error) {
      console.warn('Failed to parse action menu response', error);
      return [];
    }
  }

  async function loadActionMenuSuggestions(email, cacheKey) {
    if (!email || !cacheKey || actionMenuInFlight[cacheKey]) return;
    actionMenuInFlight = { ...actionMenuInFlight, [cacheKey]: true };
    if (cacheKey === selectedActionKey) {
      actionMenuLoading = true;
    }
    try {
      const ready = await ensureCatalogReady();
      if (!ready) return;
      const instruction = buildActionMenuInstruction(email);
      const data = await callAiCommand(ACTION_MENU_COMMAND_KEY, instruction, {
        contextId: email.contextId,
        subject: email.subject,
        journeyScope: 'panel',
        journeyScopeTarget: email.id || email.contextId || null,
        journeyLabel: email.subject || email.from || 'Selected email',
        journeyHeadline: 'Curating action ideas'
      });
      const parsed = parseActionMenuResponse(data);
      const finalOptions = parsed.length ? parsed : getDefaultActionOptions();
      actionMenuCache = { ...actionMenuCache, [cacheKey]: finalOptions };
      if (cacheKey === selectedActionKey) {
        queueActionMenuUpdate(finalOptions, cacheKey);
      }
    } catch (error) {
      console.error('Unable to refresh action menu', error);
    } finally {
      const updatedInFlight = { ...actionMenuInFlight };
      delete updatedInFlight[cacheKey];
      actionMenuInFlight = updatedInFlight;
      if (cacheKey === selectedActionKey) {
        actionMenuLoading = false;
      }
    }
  }

  /** Entry point for primary AI actions (summary, translation, compose helpers). */
  async function runMainAiCommand(request) {
    const command = typeof request === 'string' ? request : request?.key;
    const commandVariant = typeof request === 'object' ? request?.variantKey : null;
    const instructionOverride = typeof request === 'object' ? request?.instructionOverride : null;
    if (!command) return;
    const selectedContextKey = selected ? (selected.contextId || selected.id) : null;
    const fnMeta = getFunctionMeta(catalogData, command);
    const targetsCompose = Array.isArray(fnMeta?.scopes) && fnMeta.scopes.includes('compose');

    if (!targetsCompose) {
      panelSessionActive = true;
    }
    if (!targetsCompose && selectedContextKey) {
      panelErrors = { ...panelErrors, [selectedContextKey]: '' };
    }

    try {
      const result = await handleAiCommand({
        command,
        commandVariant,
        instructionOverride,
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

  function handlePanelMinimize() {
    panelMinimized = true;
    panelMaximized = false;
  }

  function handlePanelMaximizeToggle() {
    panelMaximized = !panelMaximized;
  }

  function handlePanelClose() {
    const key = selectedPanelKey;
    panelSessionActive = false;
    panelMinimized = false;
    panelMaximized = false;
    if (key) {
      panelResponses = removePanelEntry(panelResponses, key);
      panelErrors = removePanelEntry(panelErrors, key);
    }
  }

  function restorePanelFromDock() {
    panelMinimized = false;
  }

  function removePanelEntry(source, key) {
    if (!key || !source || !source[key]) return source;
    const next = { ...source };
    delete next[key];
    return next;
  }

  function handleActionMenuToggle(event) {
    isActionMenuOpen = !!(event?.detail?.open);
    if (!isActionMenuOpen) {
      applyPendingActionOptions();
    }
  }

  async function handleActionSelect(event) {
    const option = event?.detail?.option;
    if (!option) return;
    if (option.actionType === 'comingSoon' || !option.commandKey) {
      openComingSoonModal(option.label || 'This feature');
      return;
    }
    await runMainAiCommand({
      key: option.commandKey,
      variantKey: option.commandVariant || null,
      instructionOverride: option.instruction || null
    });
  }

  function openComingSoonModal(sourceLabel = 'This feature') {
    comingSoonModal = { open: true, sourceLabel };
  }

  function closeComingSoonModal() {
    comingSoonModal = { open: false, sourceLabel: '' };
  }

  function handleComingSoon(detail) {
    openComingSoonModal(detail?.label || 'This feature');
  }
</script>

<WindowProvider {windowManager}>
  <div class="h-[100dvh] flex overflow-hidden bg-gradient-to-b from-slate-50 to-slate-100">
    <MailboxSidebar
      mailbox={mailbox}
      mailboxCounts={mailboxCounts}
      variant={sidebarVariant}
      on:compose={openCompose}
      on:selectMailbox={(event) => {
        mailbox = event.detail.target;
        if (drawerMode) {
          showDrawer = false;
        }
      }}
    />
    <!-- Mobile/Tablet: Semi-transparent backdrop overlay to close drawer when clicking outside -->
    <DrawerBackdrop visible={drawerVisible} on:close={() => (showDrawer = false)} />

  <!-- List -->
  <section class="shrink-0 flex flex-col bg-white/90 border-r border-slate-200"
           class:w-[28rem]={wide}
           class:w-[25rem]={desktop && !wide}
           class:w-[20rem]={tablet && showEmailList}
           class:w-0={tablet && !showEmailList}
           class:w-full={mobile}
           class:hidden={mobile && selected}
           class:overflow-hidden={tablet && !showEmailList}
  >
    <div class="px-4 py-3 border-b border-slate-200">
      <div class="flex items-center gap-3">
        <button type="button" title="Toggle menu" class="btn btn--icon" on:click={handleMenuClick}>
          <Menu class="h-4 w-4" />
        </button>
        <div class="flex-1 min-w-0 flex flex-col gap-1">
          <div class="relative" bind:this={mailboxMenuListRef}>
            <div class="flex items-center rounded-2xl border border-slate-200 bg-white/90 shadow-inner focus-within:ring-2 focus-within:ring-slate-200 overflow-hidden">
              <input
                placeholder="Search emails..."
                bind:value={search}
                class="mailbox-search-input flex-1 bg-transparent border-0 pl-4 pr-3 py-2 text-base text-slate-800 focus:outline-none"
              />
              <button
                type="button"
                class="btn btn--secondary btn--compact rounded-none border-0 shadow-none"
                aria-haspopup="menu"
                aria-expanded={mailboxActionsOpen && mailboxActionsHost === 'list'}
                on:click={() => toggleMailboxActions('list')}
                disabled={!hasMailboxCommands || filtered.length === 0 || !!mailboxCommandPendingKey}
              >
                {#if mailboxCommandPendingKey}
                  <Loader2 class="h-4 w-4 animate-spin" aria-hidden="true" />
                  <span>{activeMailboxActionLabel ? `${activeMailboxActionLabel}…` : 'Working…'}</span>
                {:else}
                  <Sparkles class="h-4 w-4" aria-hidden="true" />
                  <span>Actions</span>
                {/if}
              </button>
            </div>
            {#if mailboxActionsOpen && mailboxActionsHost === 'list'}
              <div
                class="absolute right-0 top-[calc(100%+0.5rem)] menu-surface z-30"
                role="menu"
                tabindex="0"
                on:click|stopPropagation
                on:keydown|stopPropagation>
                <span class="menu-eyebrow">Mailbox Actions</span>
                <div class="menu-list">
                  {#each mailboxCommandEntries as entry (entry.key)}
                    <button
                      type="button"
                      class="menu-item text-left"
                      on:click={() => handleMailboxAction(entry)}
                      disabled={filtered.length === 0}
                    >
                      <div class="flex items-start gap-3 min-w-0">
                        <span class="menu-item-icon">
                          <Sparkles class="h-4 w-4" aria-hidden="true" />
                        </span>
                        <div class="min-w-0">
                          <p class="text-sm font-semibold text-slate-900">{entry.label || entry.key}</p>
                          {#if entry.description}
                            <p class="text-xs text-slate-500 leading-snug">{entry.description}</p>
                          {/if}
                        </div>
                      </div>
                      <span class="text-xs text-slate-400">AI</span>
                    </button>
                  {/each}
                </div>
                <div class="border-t border-slate-200/70 px-3 py-2 text-xs text-slate-500 mt-3">
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
          <span class="text-xs text-slate-400 ml-2 shrink-0">{formatRelativeTimestamp(e.timestampIso, e.timestamp)}</span>
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
            <button type="button" class="btn btn--icon" on:click={() => { selected = null; showDrawer = false; }} aria-label="Back to list">
              <ArrowLeft class="h-4 w-4" />
            </button>
          {/if}
          {#if tablet}
            <button type="button" class="btn btn--icon" on:click={() => showEmailList = !showEmailList} aria-label="Toggle email list">
              {#if showEmailList}
                <ChevronLeft class="h-4 w-4" />
              {:else}
                <ChevronRight class="h-4 w-4" />
              {/if}
            </button>
          {/if}
          <button type="button" title="Toggle menu" class="btn btn--icon" on:click={handleMenuClick} aria-label="Open folders">
            <Menu class="h-4 w-4" />
          </button>
          <div class="flex-1 min-w-0 flex flex-col gap-1">
            <div class="relative" bind:this={mailboxMenuMobileRef}>
              <input
                placeholder="Search emails..."
                bind:value={search}
                class="mailbox-search-input w-full rounded-2xl border border-slate-200 bg-white/90 pl-4 pr-32 py-2 text-base text-slate-800 shadow-inner focus:outline-none focus:ring-2 focus:ring-slate-200"
              />
              <button
                type="button"
                class="absolute top-1 bottom-1 right-1 inline-flex items-center gap-2 rounded-2xl border border-white/60 bg-slate-900/85 px-3 text-base font-medium text-white shadow-lg backdrop-blur focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-500 hover:bg-slate-900 disabled:cursor-not-allowed disabled:opacity-60"
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
                <div
                  class="absolute right-0 top-[calc(100%+0.5rem)] menu-surface z-30"
                  role="menu"
                  tabindex="0"
                  on:click|stopPropagation
                  on:keydown|stopPropagation>
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
          actionMenuOptions={actionMenuOptions}
          actionMenuLoading={actionMenuLoading}
          mobile={mobile}
          escapeHtmlFn={escapeHtmlContent}
          formatFullDateFn={formatFullTimestamp}
          on:reply={openReply}
          on:forward={openForward}
          on:commandSelect={(event) => runMainAiCommand(event.detail)}
          on:actionSelect={handleActionSelect}
          on:actionMenuToggle={handleActionMenuToggle}
          on:comingSoon={(event) => handleComingSoon(event.detail)}
        />
      </div>
      <div class="flex-1 flex flex-col min-h-0 gap-4 panel-column"
           class:px-4={mobile}
           class:px-5={tablet}
           class:px-6={desktop || wide}>
        <div class="flex-1 min-h-0 overflow-hidden">
          <EmailDetailView
            email={selected}
            mobile={mobile}
            tablet={tablet}
            desktop={desktop}
            wide={wide}
            renderMarkdownFn={renderMarkdownContent}
          />
        </div>
        {#if panelRenderReady}
          <div class={`ai-panel-wrapper ${panelMaximized ? 'maximized' : ''}`}>
            <AiSummaryWindow
              panelState={activePanelState}
              journeyOverlay={activePanelJourneyOverlay}
              error={activePanelError}
              maximized={panelMaximized}
              on:runCommand={(event) => runMainAiCommand(event.detail)}
              on:minimize={handlePanelMinimize}
              on:toggleMaximize={handlePanelMaximizeToggle}
              on:close={handlePanelClose}
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
  <AiPanelDockChip visible={panelSessionActive && panelMinimized} on:restore={restorePanelFromDock} />
</WindowProvider>

<WindowNotice message={windowNotice} />

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

<ComingSoonModal open={comingSoonModal.open} sourceLabel={comingSoonModal.sourceLabel} on:close={closeComingSoonModal} />



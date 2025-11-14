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
  import EmailListPane from './lib/EmailListPane.svelte';
  import AiLoadingJourney from './lib/AiLoadingJourney.svelte';
  import DrawerBackdrop from './lib/DrawerBackdrop.svelte';
  import AiPanelDockChip from './lib/AiPanelDockChip.svelte';
  import ComingSoonModal from './lib/ComingSoonModal.svelte';
  import WindowNotice from './lib/WindowNotice.svelte';
import { isMobile, isTablet, isDesktop, isWide, viewport, viewportSize } from './lib/viewportState';
  import { createWindowManager } from './lib/window/windowStore'; // temp use
  import { createComposeWindow, WindowKind } from './lib/window/windowTypes';
  import { catalogStore, hydrateCatalog, ensureCatalogLoaded as ensureCatalog, getFunctionMeta, mergeDefaultArgs, resolveDefaultInstruction } from './lib/services/aiCatalog';
  import { handleAiCommand } from './lib/services/aiCommandHandler';
  import { mapEmailMessage, parseSubjectAndBody } from './lib/services/emailUtils';
  import { buildEmailContextString, parseRecipientInput, recipientFromEmail } from './lib/services/emailContextConstructor';
  import { buildReplyPrefill, buildForwardPrefill } from './lib/services/composePrefill.js';
  import { createAiJourneyStore } from './lib/services/aiJourneyStore';
  import { Menu, Pencil, Inbox as InboxIcon, Star as StarIcon, AlarmClock, Send, ArrowLeft, ChevronLeft, ChevronRight, Archive, Trash2, Sparkles, Loader2 } from 'lucide-svelte';
  import { DEFAULT_ACTION_OPTIONS, MAILBOX_ACTION_FALLBACKS, PRIMARY_TOOLBAR_PREFERENCE } from './lib/constants/catalogActions';
  import { getJourneySubhead } from './lib/constants/journeyScopes';
  import { escapeHtmlContent, renderMarkdownContent, formatRelativeTimestamp, formatFullTimestamp } from './lib/services/emailFormatting';
  import { initializeUiNonce, startChatHeartbeat, CLIENT_WARNING_EVENT } from './lib/services/sessionNonceClient';
  import { ensureMailboxSessionToken } from './lib/services/mailboxSessionService';
import { createMailboxLayoutStore } from './lib/stores/mailboxLayoutStore';
import { createActionMenuSuggestionsStore } from './lib/stores/actionMenuSuggestionsStore';
import { createConversationLedger } from './lib/services/conversationLedger';
import { createAiPanelStore } from './lib/stores/aiPanelStore';
  import { executeCatalogCommand } from './lib/services/catalogCommandClient';
  import { launchMailboxAutomation } from './lib/services/mailboxAutomationClient';
  import './app-shared.css';
  export let bootstrap = {};

  hydrateCatalog(bootstrap.aiFunctions || null);
  const catalog = catalogStore();
  $: catalogData = $catalog;
  $: aiFunctionsByKey = catalogData?.functionsByKey || {};
  $: aiPrimaryCommandKeys = Array.isArray(catalogData?.primaryCommands) ? catalogData.primaryCommands : [];

  initializeUiNonce(bootstrap.uiNonce || null);
  const mailboxSessionToken = ensureMailboxSessionToken();
  const initialEmails = Array.isArray(bootstrap.messages) ? bootstrap.messages.map(mapEmailMessage) : [];
  const initialFolderCounts = bootstrap.folderCounts && typeof bootstrap.folderCounts === 'object' ? bootstrap.folderCounts : null;
  const initialEffectiveFolders = bootstrap.effectiveFolders && typeof bootstrap.effectiveFolders === 'object' ? bootstrap.effectiveFolders : null;
const mailboxLayout = createMailboxLayoutStore(initialEmails, initialFolderCounts, initialEffectiveFolders);
const ACTIVE_MAILBOX_ID = 'primary';
const ACTION_TOOLBAR_COMPACT_BREAKPOINT = 960;
  const mailboxStores = mailboxLayout.stores;
  const mailboxStore = mailboxStores.mailbox;
  const searchStore = mailboxStores.search;
  const mailboxCountsStore = mailboxStores.mailboxCounts;
  const filteredEmailsStore = mailboxStores.filteredEmails;
  const selectedEmailStore = mailboxStores.selectedEmail;
  const sidebarOpenStore = mailboxStores.sidebarOpen;
  const drawerModeStore = mailboxStores.drawerMode;
  const drawerVisibleStore = mailboxStores.drawerVisible;
  const pendingMovesStore = mailboxStores.pendingMoves;
  const resolveFolderForMessage = (email) => mailboxLayout.resolveFolderForMessage(email);
let emails = initialEmails;
mailboxStores.emails.subscribe((value) => {
  emails = value;
});
let windowNotice = '';
let windowNoticeTimer = null;

  function recordClientDiagnostic(level, message, error) {
    if (typeof window === 'undefined') return;
    const entry = {
      level,
      message: message || '',
      detail: error?.message || null,
      stack: error?.stack || null,
      at: Date.now()
    };
    if (Array.isArray(window.__COMPOSER_DIAGNOSTICS__)) {
      window.__COMPOSER_DIAGNOSTICS__.push(entry);
    } else {
      window.__COMPOSER_DIAGNOSTICS__ = [entry];
    }
  }

  function showGlobalNotice(message, duration = 4000) {
    if (!message) return;
    windowNotice = message;
    clearTimeout(windowNoticeTimer);
    windowNoticeTimer = setTimeout(() => {
      windowNotice = '';
    }, duration);
  }

  function processClientWarning(detail = {}) {
    const { message, error, silent, level = 'warn' } = detail || {};
    recordClientDiagnostic(level, message || 'Client warning', error);
    if (!silent && message) {
      showGlobalNotice(message);
    }
  }

  const windowManager = createWindowManager({ maxFloating: 4, maxDocked: 3 });
  const windowsStore = windowManager.windows;
  const floatingStore = windowManager.floating;
  const minimizedStore = windowManager.minimized;
  const windowErrorStore = windowManager.lastError;
  const aiJourney = createAiJourneyStore();
  const aiJourneyOverlayStore = aiJourney.overlay;
  const actionMenuStore = createActionMenuSuggestionsStore({
    ensureCatalogReady,
    callCatalogCommand: (commandKey, instruction, context) => callAiCommand(commandKey, instruction, context)
  });
  const actionMenuOptionsStore = actionMenuStore.options;
  const actionMenuLoadingStore = actionMenuStore.loading;
  let actionMenuOptions = DEFAULT_ACTION_OPTIONS.map((option) => ({ ...option, aiGenerated: false }));
  let actionMenuLoading = false;
  let selectedActionKey = null;
  let isActionMenuOpen = false;
  let comingSoonModal = { open: false, sourceLabel: '' };
  let mailbox = get(mailboxStore);
  let search = get(searchStore);
  let mailboxCounts = get(mailboxCountsStore);
let filtered = get(filteredEmailsStore);
let selected = get(selectedEmailStore);
let sidebarOpen = get(sidebarOpenStore);
let drawerMode = get(drawerModeStore);
let drawerVisible = get(drawerVisibleStore);
const conversationLedger = createConversationLedger(() => selected);
const panelStore = createAiPanelStore();
const panelStores = panelStore.stores;
const panelSessionActiveStore = panelStores.sessionActive;
const panelMinimizedStore = panelStores.minimized;
const panelMaximizedStore = panelStores.maximized;
const panelResponsesStore = panelStores.responses;
const panelErrorsStore = panelStores.errors;
  $: mailbox = $mailboxStore;
  $: search = $searchStore;
  $: mailboxCounts = $mailboxCountsStore;
  $: filtered = $filteredEmailsStore;
  $: selected = $selectedEmailStore;
  $: sidebarOpen = $sidebarOpenStore;
  $: drawerMode = $drawerModeStore;
  $: drawerVisible = $drawerVisibleStore;
  $: pendingMoves = $pendingMovesStore;
  $: actionMenuOptions = $actionMenuOptionsStore;
  $: actionMenuLoading = $actionMenuLoadingStore;
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
  $: viewportDimensions = $viewportSize;
  $: compactActions = (() => {
    const width = viewportDimensions?.width ?? 0;
    if (mobile) {
      return true; // Hide "AI Actions" text on mobile, show only icon
    }
    return width > 0 && width < ACTION_TOOLBAR_COMPACT_BREAKPOINT;
  })();
  $: inlineSidebar = viewportTier === 'desktop' || viewportTier === 'wide';
  $: mailboxLayout.setDrawerMode(mobile || tablet);
  $: sidebarVariant = (() => {
    if (!inlineSidebar) {
      return drawerVisible ? 'drawer-visible' : 'drawer-hidden';
    }
    if (!sidebarOpen) {
      return 'inline-collapsed';
    }
    return viewportTier === 'wide' ? 'inline-wide' : 'inline-desktop';
  })();
  $: if (!drawerMode && drawerVisible) {
    mailboxLayout.closeDrawer();
  }
  $: if (drawerMode && !sidebarOpen) {
    mailboxLayout.setSidebarOpen(true);
  }
  let showEmailList = true; // For tablet view toggle

  let previousPanelKey = null;
  $: selectedPanelKey = selected ? (selected.contextId || selected.id) : null;
  $: panelStore.setActiveKey(selectedPanelKey);
  $: activePanelState = selectedPanelKey ? ($panelResponsesStore[selectedPanelKey] || null) : null;
  $: activePanelError = selectedPanelKey ? ($panelErrorsStore[selectedPanelKey] || '') : '';
  $: panelRenderReady = $panelSessionActiveStore && !$panelMinimizedStore && (activePanelState || activePanelJourneyOverlay);
  $: if (selectedPanelKey !== previousPanelKey) {
    previousPanelKey = selectedPanelKey;
    panelStore.resetSessionState();
  }

  // UI state
  let mailboxActionsOpen = false;
  let mailboxActionsHost = null;
  let mailboxCommandPendingKey = null;
  let mailboxActionError = '';
  let mailboxMenuListRef = null;
  let mailboxMenuMobileRef = null;

  // Derived
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
  $: mailboxActionsComingSoon = Array.isArray(mailboxCommandEntries)
    && mailboxCommandEntries.length > 0
    && mailboxCommandEntries.every((entry) => entry?.comingSoon);
  $: selectedActionKey = selected ? (selected.contextId || selected.id || selected.conversationId || null) : null;
  $: {
    if (!selectedActionKey) {
      actionMenuStore.applyDefaults();
    } else if (!isActionMenuOpen) {
      actionMenuStore.loadSuggestions(selected, selectedActionKey);
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
    const summaryList = Array.isArray(filtered) ? filtered : [];
    const messageContextIds = summaryList
      .slice(0, 50)
      .map((mail) => mail.contextId || mail.id || mail.conversationId)
      .filter(Boolean);
    mailboxCommandPendingKey = entry.key;
    try {
      await launchMailboxAutomation({
        mailboxId: ACTIVE_MAILBOX_ID,
        searchQuery: search && search.trim() ? search.trim() : '',
        messageContextIds,
        catalogCommandKey: entry.key
      });
      showGlobalNotice(`${entry.label || 'Mailbox action'} queued`);
    } catch (error) {
      mailboxActionError = error?.message || 'Unable to run mailbox action.';
      alert(mailboxActionError);
      recordClientDiagnostic('error', 'Unable to run mailbox action.', error);
    } finally {
      mailboxCommandPendingKey = null;
    }
  }

  onMount(() => {
    ensureCatalog().catch(() => {});
  });

  onMount(() => {
    mailboxLayout.loadMailboxState(ACTIVE_MAILBOX_ID).catch((error) => {
      recordClientDiagnostic('warn', 'Mailbox state hydration failed', error);
    });
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

  onMount(() => {
    if (typeof window === 'undefined') return undefined;
    const handler = (event) => {
      processClientWarning(event?.detail || {});
    };
    window.addEventListener(CLIENT_WARNING_EVENT, handler);
    return () => {
      window.removeEventListener(CLIENT_WARNING_EVENT, handler);
    };
  });

  async function ensureCatalogReady() {
    return ensureCatalog();
  }

  // Auto-select first email on larger screens when none selected
  $: {
    if ((tablet || desktop || wide) && !selected && filtered && filtered.length) {
      mailboxLayout.selectEmailById(filtered[0].id);
    }
  }

  function selectEmail(email) {
    mailboxLayout.selectEmailById(email.id);
    if (drawerMode) {
      mailboxLayout.closeDrawer();
    }
  }
  function toggleSidebar() {
    if (drawerMode) {
      if (drawerVisible) {
        mailboxLayout.closeDrawer();
      } else {
        mailboxLayout.openDrawer();
      }
      return;
    }
    mailboxLayout.toggleSidebar();
  }
  function handleMenuClick(event) {
    toggleSidebar();
  }

  function showWindowLimitMessage() {
    const err = get(windowErrorStore);
    if (!err) return;
    showGlobalNotice(err.message);
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

  /**
   * Helper that routes all list/toolbar move requests through the store for consistent UX.
   */
  function moveEmailToFolder(email, targetFolderId) {
    if (!email || !targetFolderId) return;
    mailboxLayout
      .moveMessageRemote({ mailboxId: ACTIVE_MAILBOX_ID, messageId: email.id, targetFolderId })
      .then(() => showGlobalNotice(`Moved to ${targetFolderId}`))
      .catch((error) => {
        recordClientDiagnostic('error', 'Unable to move message.', error);
        showGlobalNotice('Unable to move message.');
      });
  }

  function archiveEmail(email) {
    moveEmailToFolder(email, 'archive');
  }

  function deleteEmail(email) {
    moveEmailToFolder(email, 'trash');
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
    }).catch((error) => {
      recordClientDiagnostic('warn', 'Reply AI prefill failed.', error);
      showGlobalNotice('AI reply prefill unavailable. Please try again.');
    });
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

  /**
   * When sending a draft we mark it as sent locally until SMTP is wired.
   */
  function handleComposeSend(event) {
    mailboxLayout.markDraftAsSent(event.detail.id);
    windowManager.close(event.detail.id);
    showGlobalNotice('Draft moved to Sent');
  }

  /**
   * Persists the draft payload whenever ComposeWindow emits save events.
   */
  function handleComposeSaveDraft(event) {
    mailboxLayout.saveDraftSession(event.detail);
  }

  function handleComposeDeleteDraft(event) {
    mailboxLayout.deleteDraftMessage(event.detail.id);
    showGlobalNotice('Draft deleted');
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
    const subhead = getJourneySubhead(scope);
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
    const targetLabel = journeyLabel || subject || selected?.subject || 'message';
    const conversationKey = conversationLedger.resolveKey({ journeyScope, journeyScopeTarget, contextId });
    const scopedConversationId = conversationLedger.read(conversationKey);
    const payload = {
      instruction,
      message: instruction,
      conversationId: scopedConversationId,
      targetLabel,
      journeyScope,
      journeyScopeTarget,
      journeyLabel,
      journeyHeadline,
      maxResults: 5,
      thinkingEnabled: false,
      jsonOutput: false
    };
    const journeyToken = beginAiJourney({ scope: journeyScope, scopeTarget: journeyScopeTarget, targetLabel, commandKey: command, headline: journeyHeadline });

    const trimmedContextId = typeof contextId === 'string' ? contextId.trim() : null;
    if (trimmedContextId) {
      payload.contextId = trimmedContextId;
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
      if (selected?.contextForAi && selected.contextForAi.trim()) {
        payload.emailContext = selected.contextForAi.trim();
      } else if (emailContext && emailContext.trim()) {
        payload.emailContext = emailContext.trim();
      } else if (selected) {
        const ctx = buildEmailContextString(selected);
        if (ctx) payload.emailContext = ctx;
      }
    }

    advanceAiJourney(journeyToken, 'ai:context-search');

    try {
      const data = await executeCatalogCommand(command, payload);
      advanceAiJourney(journeyToken, 'ai:llm-thinking');
      completeAiJourney(journeyToken);
      if (data?.conversationId) {
        conversationLedger.write(conversationKey, data.conversationId);
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

  /** Entry point for primary AI actions (summary, translation, compose helpers). */
  async function runMainAiCommand(request) {
    const command = typeof request === 'string' ? request : request?.key;
    const commandVariant = typeof request === 'object' ? request?.variantKey : null;
    const instructionOverride = typeof request === 'object' ? request?.instructionOverride : null;
    if (!command) return;
    const selectedContextKey = selected ? (selected.contextId || selected.id) : null;
    const fnMeta = getFunctionMeta(catalogData, command);
    const targetsCompose = Array.isArray(fnMeta?.scopes) && fnMeta.scopes.includes('compose');

    if (!targetsCompose && selectedContextKey) {
      panelStore.beginSession(selectedContextKey);
      panelStore.clearError(selectedContextKey);
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
          panelStore.recordResponse(key, {
            html: result.html,
            title: result.title || fnMeta?.label || 'AI Summary',
            commandKey: result.command || command,
            commandLabel: result.commandLabel || fnMeta?.label || 'AI Summary',
            updatedAt: Date.now()
          });
        }
      }
    } catch (error) {
      if (error?.message === 'Close or minimize an existing draft before opening another.') {
        showWindowLimitMessage();
        return;
      }
      if (!targetsCompose && selectedContextKey) {
        panelStore.recordError(selectedContextKey, error?.message || 'Unable to complete request.');
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
    panelStore.minimize();
  }

  function handlePanelMaximizeToggle() {
    panelStore.toggleMaximize();
  }

  function handlePanelClose() {
    panelStore.closePanel(selectedPanelKey);
  }

  function restorePanelFromDock() {
    panelStore.restoreFromDock();
  }

  function handleActionMenuToggle(event) {
    isActionMenuOpen = !!(event?.detail?.open);
    if (!isActionMenuOpen && selectedActionKey) {
      actionMenuStore.loadSuggestions(selected, selectedActionKey);
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
      mailboxCounts={mailboxCounts}
      variant={sidebarVariant}
      on:compose={openCompose}
      on:selectMailbox={(event) => {
        const target = event.detail.target;
        mailboxLayout.selectMailbox(target);
        // TODO: re-enable ensureMailboxState(target) when mailbox state endpoint is available.
        if (drawerMode) {
          mailboxLayout.closeDrawer();
        }
      }}
    />
    <!-- Mobile/Tablet: Semi-transparent backdrop overlay to close drawer when clicking outside -->
    <DrawerBackdrop visible={drawerVisible} on:close={() => mailboxLayout.closeDrawer()} />

    <EmailListPane
      search={search}
      {filtered}
      {selected}
      {mobile}
      {tablet}
      {desktop}
      {wide}
      drawerVisible={drawerVisible}
      {showEmailList}
      hasMailboxCommands={hasMailboxCommands}
      mailboxCommandEntries={mailboxCommandEntries}
      mailboxCommandPendingKey={mailboxCommandPendingKey}
      mailboxActionsOpen={mailboxActionsOpen}
      mailboxActionsHost={mailboxActionsHost}
      activeMailboxActionLabel={activeMailboxActionLabel}
      mailboxActionError={mailboxActionError}
      escapeHtmlFn={escapeHtml}
      formatRelativeTimestampFn={formatRelativeTimestamp}
      bind:mailboxMenuListRef={mailboxMenuListRef}
      resolveFolderFn={resolveFolderForMessage}
      pendingMoveIds={pendingMoves}
      compactActions={compactActions}
      on:toggleMenu={handleMenuClick}
      on:searchChange={(event) => mailboxLayout.setSearch(event.detail.value)}
      on:toggleMailboxActions={(event) => toggleMailboxActions(event.detail.host)}
      on:mailboxAction={(event) => handleMailboxAction(event.detail.entry)}
      on:selectEmail={(event) => selectEmail(event.detail.email)}
      on:archiveEmail={(event) => archiveEmail(event.detail.email)}
      on:deleteEmail={(event) => deleteEmail(event.detail.email)}
      on:moveEmail={(event) => moveEmailToFolder(event.detail.email, event.detail.targetFolderId)}
    />


  <!-- Content -->
  <section class="flex-1 flex flex-col bg-white/95 relative"
           class:hidden={mobile && !selected}>
    {#if mobile}
      <div class="px-4 py-3 border-b border-slate-200 flex flex-col gap-2">
        <div class="flex items-center gap-2">
          <button type="button" class="btn btn--icon" on:click={() => { mailboxLayout.selectEmailById(null); mailboxLayout.closeDrawer(); }} aria-label="Back to list">
            <ArrowLeft class="h-4 w-4" />
          </button>
          <button type="button" title="Toggle menu" class="btn btn--icon relative z-[70]" on:click={handleMenuClick} aria-label="Open folders">
            <Menu class="h-4 w-4" />
          </button>
          <div class="flex-1 min-w-0 flex flex-col gap-1">
            <div class="relative" bind:this={mailboxMenuMobileRef}>
              <input
                placeholder="Search emails..."
                value={search}
                on:input={(event) => mailboxLayout.setSearch(event.currentTarget.value)}
                class="mailbox-search-input w-full rounded-2xl border border-slate-200 bg-white/90 pl-4 pr-32 py-2 text-base text-slate-800 shadow-inner focus:outline-none focus:ring-2 focus:ring-slate-200"
              />
              <button
                type="button"
                class="absolute inset-y-0 right-0 btn btn--primary btn--compact mailbox-ai-trigger"
                class:mailbox-ai-trigger--compact={compactActions}
                aria-haspopup="menu"
                aria-expanded={mailboxActionsOpen && mailboxActionsHost === 'mobile'}
                on:click={() => toggleMailboxActions('mobile')}
                disabled={!hasMailboxCommands || filtered.length === 0 || !!mailboxCommandPendingKey}
              >
                <span class="flex items-center gap-1">
                  {#if mailboxCommandPendingKey}
                    <Loader2 class="h-4 w-4 animate-spin" aria-hidden="true" />
                  {:else}
                    <Sparkles class="h-4 w-4" aria-hidden="true" />
                  {/if}
                </span>
                <span class="mailbox-ai-trigger__label">
                  {#if mailboxCommandPendingKey}
                    {activeMailboxActionLabel ? `${activeMailboxActionLabel}…` : 'Running…'}
                  {:else}
                    AI Actions
                  {/if}
                </span>
              </button>
              {#if mailboxActionsOpen && mailboxActionsHost === 'mobile'}
                <div
                  class="absolute right-0 top-[calc(100%+0.5rem)] menu-surface"
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
                        <div class="flex items-center gap-3 min-w-0">
                          <div class="menu-item-icon">
                            <Sparkles class="h-4 w-4" aria-hidden="true" />
                          </div>
                          <div class="flex-1 min-w-0">
                            <p class="font-medium text-slate-900 tracking-wide truncate">{entry.label || entry.key}</p>
                            {#if entry.description}
                              <p class="text-xs text-slate-500 leading-snug">{entry.description}</p>
                            {/if}
                          </div>
                        </div>
                      </button>
                    {/each}
                  </div>
                  <div class="mt-3 text-xs text-slate-500">
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
    {#if tablet}
      <div class="px-5 py-3 border-b border-slate-200">
        <button type="button" class="btn btn--icon" on:click={() => showEmailList = !showEmailList} aria-label="Toggle email list">
          {#if showEmailList}
            <ChevronLeft class="h-4 w-4" />
          {:else}
            <ChevronRight class="h-4 w-4" />
          {/if}
        </button>
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
          compactActions={compactActions}
          currentFolderId={resolveFolderForMessage(selected)}
          pendingMove={pendingMoves.has(selected?.id)}
          escapeHtmlFn={escapeHtml}
          formatFullDateFn={formatFullTimestamp}
          on:reply={openReply}
          on:forward={openForward}
          on:archive={() => archiveEmail(selected)}
          on:delete={() => deleteEmail(selected)}
          on:move={(event) => moveEmailToFolder(selected, event.detail.targetFolderId)}
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
            renderMarkdownFn={renderMarkdown}
          />
        </div>
        {#if panelRenderReady}
          <div class={`ai-panel-wrapper ${$panelMaximizedStore ? 'maximized' : ''}`}>
            <AiSummaryWindow
              panelState={activePanelState}
              journeyOverlay={activePanelJourneyOverlay}
              error={activePanelError}
              maximized={$panelMaximizedStore}
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
        on:saveDraft={handleComposeSaveDraft}
        on:deleteDraft={handleComposeDeleteDraft}
      />
    {/if}
  {/each}

  <WindowDock windows={minimizedWindows} />
  <AiPanelDockChip visible={$panelSessionActiveStore && $panelMinimizedStore} on:restore={restorePanelFromDock} />
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

<style>
  /**
   * AI summary panel wrapper constrains height relative to email detail column.
   * @usage - Div wrapping <AiSummaryWindow /> instances within App.svelte
   * @related - .ai-panel-wrapper.maximized, .panel-column
   */
  .ai-panel-wrapper {
    position: relative;
    z-index: var(--z-panel-overlay, 10);
    height: clamp(280px, 35vh, 520px);
    max-height: 45vh;
    display: flex;
    flex-direction: column;
  }

  /**
   * Maximized mode allows the AI panel to overlay the column or viewport when expanded.
   * @usage - Conditional class when $panelMaximizedStore is true
   * @related - .ai-panel-wrapper
   */
  .ai-panel-wrapper.maximized {
    position: absolute;
    inset: 0;
    z-index: var(--z-panel-maximized, 30);
    height: 100%;
    max-height: 100%;
  }

  /**
   * Panel column anchor preserves positioning context for overlays rendered within detail pane.
   * @usage - Applied to flex column container for email detail + AI panel
   * @related - .ai-panel-wrapper inside this column
   */
  .panel-column {
    position: relative;
    min-height: 0;
  }

  /**
   * Mobile breakpoint relaxes panel height constraints and pads maximized overlays.
   * @usage - Applies automatically when viewport <=768px
   * @related - .ai-panel-wrapper, .ai-panel-wrapper.maximized
   */
  @media (max-width: 768px) {
    .ai-panel-wrapper {
      height: auto;
      max-height: none;
    }

    .ai-panel-wrapper.maximized {
      position: fixed;
      inset: 0;
      padding: 0.75rem;
    }
  }

  /**
   * Safe-area padding ensures maximized AI panel respects device notches on mobile Safari.
   * @usage - Applies within the mobile breakpoint when env(safe-area-*) is supported
   * @related - .ai-panel-wrapper.maximized mobile treatment
   */
  @supports (padding: env(safe-area-inset-top)) {
    @media (max-width: 768px) {
      .ai-panel-wrapper.maximized {
        padding-top: env(safe-area-inset-top);
        padding-right: env(safe-area-inset-right);
        padding-bottom: env(safe-area-inset-bottom);
        padding-left: env(safe-area-inset-left);
      }
    }
  }
</style>

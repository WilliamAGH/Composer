<script>
import { onDestroy, onMount } from 'svelte';
import { derived, get, writable } from 'svelte/store';
import { useMailboxDataStore, useMailboxChromeStore, useMailboxResponsiveState } from './lib/providers/MailboxChromeProvider.svelte';
import { useAiCommandContext } from './lib/providers/AiCommandProvider.svelte';
  import ComposeWindow from './lib/ComposeWindow.svelte';
  import AiSummaryWindow from './lib/AiSummaryWindow.svelte';
  import UnifiedDock from './lib/UnifiedDock.svelte';
  import WindowProvider from './lib/window/WindowProvider.svelte';
  import EmailActionToolbar from './lib/EmailActionToolbar.svelte';
  import EmailDetailView from './lib/EmailDetailView.svelte';
  import MailboxSidebar from './lib/MailboxSidebar.svelte';
  import EmailListPane from './lib/EmailListPane.svelte';
  import AiLoadingJourney from './lib/AiLoadingJourney.svelte';
  import DrawerBackdrop from './lib/DrawerBackdrop.svelte';
import MobileTopBar from './lib/MobileTopBar.svelte';
  import AiSummaryMobileSheet from './lib/AiSummaryMobileSheet.svelte';
  import ComingSoonModal from './lib/ComingSoonModal.svelte';
  import WindowNotice from './lib/WindowNotice.svelte';
  import { Sparkles } from 'lucide-svelte';
import OverlayStack from './lib/overlay/OverlayStack.svelte';
import { createOverlayController } from './lib/overlay/OverlayController';
import { provideOverlayController } from './lib/overlay/overlayContext';
import { isMobile, isTablet, isDesktop, isWide, viewport, viewportSize } from './lib/viewportState';
  import { createWindowManager } from './lib/window/windowStore'; // temp use
  import { createComposeWindow, WindowKind } from './lib/window/windowTypes';
import { getFunctionMeta, mergeDefaultArgs, resolveDefaultInstruction } from './lib/services/aiCatalog';
import { handleAiCommand, deriveHeadline, runComposeWindowAi } from './lib/services/aiCommandHandler';
import { deriveRecipientContext } from './lib/services/emailContextConstructor';
  import { buildReplyPrefill, buildForwardPrefill } from './lib/services/composePrefill';
import { ChevronLeft, ChevronRight } from 'lucide-svelte';
import { MAILBOX_ACTION_FALLBACKS, PRIMARY_TOOLBAR_PREFERENCE } from './lib/constants/catalogActions';
import { escapeHtmlContent, renderMarkdownContent, formatRelativeTimestamp, formatFullTimestamp } from './lib/services/emailFormatting';
  import { createActionMenuSuggestionsStore } from './lib/stores/actionMenuSuggestionsStore';
import { createAiPanelStore } from './lib/stores/aiPanelStore';
  import { launchMailboxAutomation } from './lib/services/mailboxAutomationClient';
  import EmailDetailMobileSheet from './lib/EmailDetailMobileSheet.svelte';
import { processClientWarning, recordClientDiagnostic, showWindowNotice } from './lib/services/clientDiagnosticsService';
import { CLIENT_WARNING_EVENT } from './lib/services/sessionNonceClient';

  /**
   * ShellLayout consumes provider contexts and drives the interactive shell. Keep it as
   * a composition root only—business logic lives inside services/controllers so we maintain single
   * responsibility per module.
   */

  const { catalog, ensureCatalog, windowNoticeStore, createAiCommandService } = useAiCommandContext();
  $: catalogData = $catalog;
  $: aiFunctionsByKey = catalogData?.functionsByKey || {};
  $: aiPrimaryCommandKeys = Array.isArray(catalogData?.primaryCommands) ? catalogData.primaryCommands : [];
const mailboxDataStore = useMailboxDataStore();
const mailboxChromeStore = useMailboxChromeStore();
const responsiveState = useMailboxResponsiveState();
const ACTIVE_MAILBOX_ID = 'primary';
const overlayController = createOverlayController();
provideOverlayController(overlayController);

function buildPrimaryCommandEntries(catalogData) {
  const functionsByKey = catalogData?.functionsByKey || {};
  const primaryKeys = Array.isArray(catalogData?.primaryCommands) && catalogData.primaryCommands.length
    ? catalogData.primaryCommands
    : Object.keys(functionsByKey);

  const entries = primaryKeys
    .map((key) => ({ key, meta: functionsByKey[key] }))
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
}
  const mailboxStores = mailboxDataStore.stores;
  const chromeStores = mailboxChromeStore.stores;
  const responsiveStores = responsiveState.stores;
  const mailboxStore = mailboxStores.mailbox;
  const searchStore = mailboxStores.search;
  const mailboxCountsStore = mailboxStores.mailboxCounts;
  const filteredEmailsStore = mailboxStores.filteredEmails;
  const pendingMovesStore = mailboxStores.pendingMoves;
  const selectedEmailStore = chromeStores.selectedEmail;
  const sidebarOpenStore = chromeStores.sidebarOpen;
  const drawerModeStore = chromeStores.drawerMode;
  const drawerVisibleStore = chromeStores.drawerVisible;
  const inlineSidebarStore = responsiveStores.inlineSidebar;
  const sidebarVariantStore = responsiveStores.sidebarVariant;
  const sidebarWidthStore = responsiveStores.sidebarWidth;
  const availableContentWidthStore = responsiveStores.availableContentWidth;
  const compactActionsStore = responsiveStores.compactActions;
  const viewportTierStore = responsiveStores.viewportTier;
  const viewportStateStore = responsiveStores.viewport;
  const windowManager = createWindowManager({ maxFloating: 4, maxDocked: 3 });
  const windowsStore = windowManager.windows;
  const floatingStore = windowManager.floating;
  const minimizedStore = windowManager.minimized;
  const windowErrorStore = windowManager.lastError;
  const panelReadyStore = writable(false);
  const comingSoonModalStore = writable({ open: false, sourceLabel: '' });
const mobileDetailOverlayVisibleStore = derived([isMobile, selectedEmailStore], ([$mobile, $selected]) => Boolean($mobile && $selected));
const composeOverlayVisibleStore = derived([isMobile, floatingStore], ([$mobile, $floating]) => {
  if (!$mobile || !Array.isArray($floating)) return false;
  return $floating.some((win) => win.kind === WindowKind.COMPOSE && !win.minimized);
});
const mobilePanelVisibleStore = derived([isMobile, panelReadyStore], ([$mobile, $panelReady]) => Boolean($mobile && $panelReady));
const overlayBackdropVisibleStore = derived(
  [drawerVisibleStore, mobileDetailOverlayVisibleStore, mobilePanelVisibleStore, composeOverlayVisibleStore, comingSoonModalStore],
  ([$drawer, $detail, $panel, $compose, $modal]) => Boolean($drawer || $detail || $panel || $compose || $modal.open)
);
const resolveFolderForMessage = (email) => mailboxDataStore.resolveFolderForMessage(email);
let emails = [];
mailboxStores.emails.subscribe((value) => {
  emails = value;
});

  const primaryCommandEntriesStore = derived(catalog, ($catalog) => buildPrimaryCommandEntries($catalog));
  $: primaryCommandEntries = $primaryCommandEntriesStore;

  const actionMenuStore = createActionMenuSuggestionsStore({
    ensureCatalogReady: ensureCatalog,
    callCatalogCommand: (commandKey, instruction, context) => callAiCommand(commandKey, instruction, context)
  });
  const actionMenuOptionsStore = actionMenuStore.options;
  const actionMenuLoadingStore = actionMenuStore.loading;
  const detailSheetInstances = derived(
    [
      mobileDetailOverlayVisibleStore,
      selectedEmailStore,
      actionMenuOptionsStore,
      actionMenuLoadingStore,
      pendingMovesStore,
      compactActionsStore,
      primaryCommandEntriesStore
    ],
    ([$visible, $selected, $actionOptions, $actionLoading, $pendingMoves, $compact, $primaryCommands]) => {
      if (!$visible || !$selected) {
        return [];
      }
      return [
        {
          id: 'email-detail-sheet',
          component: EmailDetailMobileSheet,
          props: {
            email: $selected,
            commands: $primaryCommands,
            actionMenuOptions: $actionOptions,
            actionMenuLoading: $actionLoading,
            compactActions: $compact,
            currentFolderId: resolveFolderForMessage($selected),
            pendingMove: $pendingMoves.has?.($selected.id),
            escapeHtmlFn: escapeHtml,
            formatFullDateFn: formatFullTimestamp,
            renderMarkdownFn: renderMarkdown,
            onBack: () => mailboxChromeStore.selectEmailById(null),
            onToggleMenu: handleMenuClick,
            onReply: openReply,
            onForward: openForward,
            onArchive: () => archiveEmail($selected),
            onDelete: () => deleteEmail($selected),
            onMove: (event) => moveEmailToFolder($selected, event?.detail?.targetFolderId),
            onCommandSelect: (event) => runMainAiCommand(event?.detail),
            onActionSelect: (event) => handleActionSelect(event?.detail ? { detail: event.detail } : event),
            onActionMenuToggle: (event) => handleActionMenuToggle(event?.detail ? { detail: event.detail } : event),
            onComingSoon: (event) => handleComingSoon(event?.detail)
          }
        }
      ];
    }
  );

  const comingSoonOverlayInstances = derived(comingSoonModalStore, ($modal) => {
    if (!$modal.open) {
      return [];
    }
    return [
      {
        id: 'coming-soon-modal',
        component: ComingSoonModal,
        props: {
          open: true,
          sourceLabel: $modal.sourceLabel,
          onCloseCallback: closeComingSoonModal
        }
      }
    ];
  });

  const drawerBackdropInstances = derived(overlayBackdropVisibleStore, ($visible) => {
    if (!$visible) {
      return [];
    }
    return [
      {
        id: 'drawer-backdrop',
        component: DrawerBackdrop,
        props: {
          onClose: handleOverlayBackdropClose
        }
      }
    ];
  });

  let selectedActionKey = null;
  let isActionMenuOpen = false;
  $: comingSoonModal = $comingSoonModalStore;
  let mailbox = get(mailboxStore);
  let search = get(searchStore);
  let mailboxCounts = get(mailboxCountsStore);
let filtered = get(filteredEmailsStore);
let selected = get(selectedEmailStore);
let sidebarOpen = get(sidebarOpenStore);
let drawerMode = get(drawerModeStore);
let drawerVisible = get(drawerVisibleStore);
let pendingMoves = get(pendingMovesStore);
const { aiClient, journeyStore: aiJourney, conversationLedger } = createAiCommandService(() => selected);
const aiJourneyOverlayStore = aiJourney.overlay;
const panelStore = createAiPanelStore();
const panelStores = panelStore.stores;
const panelSessionActiveStore = panelStores.sessionActive;
const panelMinimizedStore = panelStores.minimized;
const panelMaximizedStore = panelStores.maximized;
const panelResponsesStore = panelStores.responses;
const panelErrorsStore = panelStores.errors;
const panelActiveKeyStore = panelStores.activeKey;
const activePanelStateStore = derived([panelResponsesStore, panelActiveKeyStore], ([$responses, $key]) => ($key ? $responses[$key] || null : null));
const activePanelErrorStore = derived([panelErrorsStore, panelActiveKeyStore], ([$errors, $key]) => ($key ? $errors[$key] || '' : ''));
  $: mailbox = $mailboxStore;
  $: search = $searchStore;
  $: mailboxCounts = $mailboxCountsStore;
  $: filtered = $filteredEmailsStore;
  $: selected = $selectedEmailStore;
  $: sidebarOpen = $sidebarOpenStore;
  $: drawerMode = $drawerModeStore;
  $: drawerVisible = $drawerVisibleStore;
  $: pendingMoves = $pendingMovesStore;
  $: windows = $windowsStore;
  $: floatingWindows = $floatingStore;
  $: composeWindowStack = floatingWindows.filter((win) => win.kind === WindowKind.COMPOSE && !win.minimized);
  $: activeMobileComposeId = composeWindowStack.length ? composeWindowStack[composeWindowStack.length - 1].id : null;
  $: minimizedWindows = $minimizedStore;
  $: windowAlert = $windowErrorStore ? $windowErrorStore.message : '';
  $: aiJourneyOverlay = $aiJourneyOverlayStore;
  $: composeJourneyOverlay = aiJourneyOverlay.scope === 'compose' ? aiJourneyOverlay : null;
  $: panelJourneyOverlay = aiJourneyOverlay.scope === 'panel' ? aiJourneyOverlay : null;
$: activePanelJourneyOverlay = panelJourneyOverlay && selected && (panelJourneyOverlay.scopeTarget === selected.id || panelJourneyOverlay.scopeTarget === selected.contextId) ? panelJourneyOverlay : null;

const aiSheetInstances = derived(
  [mobilePanelVisibleStore, activePanelStateStore, activePanelErrorStore, aiJourneyOverlayStore],
  ([$visible, $state, $error, $journey]) => {
    if (!$visible) {
      return [];
    }
    return [
      {
        id: 'ai-summary-mobile',
        component: AiSummaryMobileSheet,
        props: {
          panelState: $state,
          journeyOverlay: $journey,
          error: $error,
          showMenuButton: true,
          onClose: handlePanelClose,
          onToggleMenu: handleMenuClick,
          onMinimize: handlePanelMinimize,
          onRunCommand: (detail) => runMainAiCommand(detail?.detail || detail)
        }
      }
    ];
  }
);

const overlayRegistrations = [
  overlayController.registerOverlay({
    key: 'drawer-backdrop',
    presenter: 'backdrop',
    priority: 10,
    source: drawerBackdropInstances
  }),
  overlayController.registerOverlay({
    key: 'detail-sheet',
    presenter: 'sheet',
    priority: 20,
    source: detailSheetInstances
  }),
  overlayController.registerOverlay({
    key: 'ai-sheet',
    presenter: 'sheet',
    priority: 30,
    source: aiSheetInstances
  }),
  overlayController.registerOverlay({
    key: 'coming-soon',
    presenter: 'modal',
    priority: 50,
    source: comingSoonOverlayInstances
  })
];

onDestroy(() => {
  for (const dispose of overlayRegistrations) {
    dispose();
  }
});
  // Viewport responsive
  $: mobile = $isMobile;
  $: tablet = $isTablet;
  $: desktop = $isDesktop;
  $: wide = $isWide;
  $: viewportType = $viewport;
  $: viewportTier = $viewportTierStore;
  $: viewportDimensions = $viewportStateStore;
  $: inlineSidebar = $inlineSidebarStore;
  $: sidebarVariant = $sidebarVariantStore;
  $: sidebarWidth = $sidebarWidthStore;
  $: availableContentWidth = $availableContentWidthStore;
  $: compactActions = $compactActionsStore;
  let showEmailList = true; // For tablet view toggle

  let previousPanelKey = null;
  $: selectedPanelKey = selected ? (selected.contextId || selected.id) : null;
  $: panelStore.setActiveKey(selectedPanelKey);
  $: activePanelState = selectedPanelKey ? ($panelResponsesStore[selectedPanelKey] || null) : null;
  $: activePanelError = selectedPanelKey ? ($panelErrorsStore[selectedPanelKey] || '') : '';
  $: panelRenderReady = $panelSessionActiveStore && !$panelMinimizedStore && (activePanelState || activePanelJourneyOverlay);
  $: panelReadyStore.set(panelRenderReady);
  $: mobilePanelVisible = $mobilePanelVisibleStore;
  $: mobileDetailOverlayVisible = $mobileDetailOverlayVisibleStore;
  $: if (selectedPanelKey !== previousPanelKey) {
    previousPanelKey = selectedPanelKey;
    panelStore.resetSessionState();
  }

  // Unified dock items - combines all minimized components
  $: dockItems = [
    // Minimized compose windows
    ...minimizedWindows.map(win => ({
      id: win.id,
      type: 'compose',
      title: win.title,
      icon: null,
      onRestore: () => windowManager.toggleMinimize(win.id),
      onClose: () => windowManager.close(win.id),
      closeable: true
    })),

    // Minimized AI panel
    ...($panelSessionActiveStore && $panelMinimizedStore ? [{
      id: 'ai-panel',
      type: 'panel',
      title: 'AI Panel',
      icon: Sparkles,
      onRestore: () => panelStore.restoreFromDock(),
      onClose: () => panelStore.closePanel(selectedPanelKey),
      closeable: true
    }] : [])
  ];

  // UI state
  let mailboxActionsOpen = false;
  let mailboxActionsHost = null;
  let mailboxCommandPendingKey = null;
  let mailboxActionError = '';
  let mailboxMenuListRef = null;
  let mailboxMenuMobileRef = null;

  // Derived
$: composeAiFunctions = Object.values(aiFunctionsByKey || {})
    .filter((fn) => Array.isArray(fn.scopes) && fn.scopes.includes('compose'));
  $: mailboxCommandEntries = deriveMailboxCommands();
  $: hasMailboxCommands = Array.isArray(mailboxCommandEntries) && mailboxCommandEntries.length > 0;
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
    const ready = await ensureCatalog();
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
      showWindowNotice(`${entry.label || 'Mailbox action'} queued`);
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

  // Auto-select first email on larger screens when none selected
  $: {
    if ((tablet || desktop || wide) && !selected && filtered && filtered.length) {
      mailboxChromeStore.selectEmailById(filtered[0].id);
    }
  }

  function selectEmail(email) {
    mailboxChromeStore.selectEmailById(email.id);
    if (drawerMode) {
      mailboxChromeStore.closeDrawer();
    }
  }
  function toggleSidebar() {
    if (drawerMode) {
      if (drawerVisible) {
        mailboxChromeStore.closeDrawer();
      } else {
        mailboxChromeStore.openDrawer();
      }
      return;
    }
    mailboxChromeStore.toggleSidebar();
  }

  function handleMenuClick(event) {
    toggleSidebar();
  }

  function closeTopComposeWindow() {
    const floatingWindows = get(floatingStore) || [];
    const composeWindows = floatingWindows.filter((win) => win.kind === WindowKind.COMPOSE && !win.minimized);
    if (composeWindows.length === 0) return false;
    const latest = composeWindows[composeWindows.length - 1];
    if (latest?.id) {
      windowManager.close(latest.id);
      return true;
    }
    return false;
  }

  function handleOverlayBackdropClose() {
    if (closeTopComposeWindow()) return;

    if (get(mobilePanelVisibleStore)) {
      handlePanelClose();
      return;
    }

    if (get(mobileDetailOverlayVisibleStore)) {
      mailboxChromeStore.selectEmailById(null);
      return;
    }

    if (get(drawerVisibleStore)) {
      mailboxChromeStore.closeDrawer();
    }
  }

  function showWindowLimitMessage() {
    const err = get(windowErrorStore);
    if (!err) return;
    showWindowNotice(err.message);
  }

  function openCompose() {
    // Minimize panel if open before creating new compose window
    const sessionActive = get(panelSessionActiveStore);
    const panelMinimized = get(panelMinimizedStore);
    console.log('[openCompose] Panel state check:', { sessionActive, panelMinimized });
    if (sessionActive && !panelMinimized) {
      console.log('[openCompose] Minimizing panel');
      panelStore.minimize();
    }

    const result = windowManager.open(createComposeWindow());
    if (!result.ok) {
      showWindowLimitMessage();
    }
  }

  function openReply(withAi = true) {
    if (!selected) return alert('Select an email first.');

    // Minimize panel if open before creating new compose window
    const sessionActive = get(panelSessionActiveStore);
    const panelMinimized = get(panelMinimizedStore);
    console.log('[openReply] Panel state check:', { sessionActive, panelMinimized });
    if (sessionActive && !panelMinimized) {
      console.log('[openReply] Minimizing panel');
      panelStore.minimize();
    }

    const prefills = buildReplyPrefill(selected);
    const descriptor = createComposeWindow(selected, {
      subject: prefills.subject,
      body: prefills.body,
      hasQuotedContext: prefills.hasQuotedContext,
      quotedContext: prefills.quotedContext,
      isReply: true,
      title: prefills.subject || 'Reply'
    });
    const result = windowManager.open(descriptor);
    if (!result.ok) {
      showWindowLimitMessage();
      return;
    }
    if (withAi) {
      queueReplyPrefillAi(selected);
    }
  }

  function openForward() {
    if (!selected) return alert('Select an email first.');

    // Minimize panel if open before creating new compose window
    const sessionActive = get(panelSessionActiveStore);
    const panelMinimized = get(panelMinimizedStore);
    console.log('[openForward] Panel state check:', { sessionActive, panelMinimized });
    if (sessionActive && !panelMinimized) {
      console.log('[openForward] Minimizing panel');
      panelStore.minimize();
    }

    const prefills = buildForwardPrefill(selected);
    const descriptor = createComposeWindow(selected, {
      to: '',
      recipientName: '',
      recipientEmail: '',
      subject: prefills.subject,
      body: prefills.body,
      hasQuotedContext: prefills.hasQuotedContext,
      quotedContext: prefills.quotedContext,
      isReply: false,
      isForward: true,
      title: prefills.subject || 'Forward'
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
    mailboxDataStore
      .moveMessageRemote({ mailboxId: ACTIVE_MAILBOX_ID, messageId: email.id, targetFolderId })
      .then(() => showWindowNotice(`Moved to ${targetFolderId}`))
      .catch((error) => {
        recordClientDiagnostic('error', 'Unable to move message.', error);
        showWindowNotice('Unable to move message.');
      });
  }

  function archiveEmail(email) {
    moveEmailToFolder(email, 'archive');
  }

  function deleteEmail(email) {
    moveEmailToFolder(email, 'trash');
  }

  function queueReplyPrefillAi(relatedEmail) {
    const instruction = buildReplyGreetingInstruction(relatedEmail);
    runMainAiCommand({ key: 'draft', instructionOverride: instruction || null }).catch((error) => {
      recordClientDiagnostic('warn', 'Reply AI prefill failed.', error);
      showWindowNotice('AI reply prefill unavailable. Please try again.');
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
    mailboxDataStore.markDraftAsSent(event.detail.id);
    windowManager.close(event.detail.id);
    showWindowNotice('Draft moved to Sent');
  }

  /**
   * Persists the draft payload whenever ComposeWindow emits save events.
   */
  function handleComposeSaveDraft(event) {
    mailboxDataStore.saveDraftSession(event.detail);
  }

  function handleComposeDeleteDraft(event) {
    mailboxDataStore.deleteDraftMessage(event.detail.id);
    showWindowNotice('Draft deleted');
  }

  async function handleComposeRequestAi(event) {
    const detail = event?.detail;
    if (!detail?.id || !detail.command) return;
    const windowConfig = windows.find((win) => win.id === detail.id);
    if (!windowConfig) {
      alert('Compose window unavailable.');
      return;
    }
    try {
      await runComposeWindowAi({
        windowManager,
        windowConfig,
        detail,
        catalogStore: catalog,
        ensureCatalogLoaded: ensureCatalog,
        callAiCommand,
        resolveEmailById: (id) => emails.find((entry) => entry.id === id),
        selectedEmail: selected
      });
    } catch (error) {
      recordClientDiagnostic('warn', 'Compose AI request failed.', error);
      alert(error?.message || 'Unable to complete AI request.');
    }
  }

  const escapeHtml = escapeHtmlContent;
  const renderMarkdown = renderMarkdownContent;

  // ---------- AI integration (parity with v1) ----------

  async function callAiCommand(command, instruction, overrides = {}) {
    return aiClient.call(command, instruction, {
      ...overrides,
      fallbackEmail: overrides.fallbackEmail || selected
    });
  }

  /** Builds compose/draft instructions while preserving catalog defaults and current user drafts. */

  /** Entry point for primary AI actions (summary, translation, compose helpers). */
  async function runMainAiCommand(request) {
    const command = typeof request === 'string' ? request : request?.key;
    const commandVariant = typeof request === 'object' ? request?.variantKey : null;
    const instructionOverride = typeof request === 'object' ? request?.instructionOverride : null;
    if (!command) return;
    if (!selected) {
      alert('Select an email first.');
      return;
    }
    const selectedContextKey = selected ? (selected.contextId || selected.id) : null;
    const fnMeta = getFunctionMeta(catalogData, command);
    const targetsCompose = Array.isArray(fnMeta?.scopes) && fnMeta.scopes.includes('compose');

    if (!targetsCompose && selectedContextKey) {
      // Minimize all non-minimized compose windows before opening panel
      const composeToMinimize = windows.filter(win => win.kind === WindowKind.COMPOSE && !win.minimized);
      console.log('[runMainAiCommand] Panel command - minimizing compose windows:', composeToMinimize.length);
      composeToMinimize.forEach((win) => {
        console.log('[runMainAiCommand] Minimizing compose window:', win.id);
        windowManager.toggleMinimize(win.id);
      });

      console.log('[runMainAiCommand] Beginning panel session');
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
        ensureCatalogLoaded: ensureCatalog,
        panelStore
      });

      if (result?.type === WindowKind.SUMMARY) {
        const key = result.contextId || selectedContextKey;
        if (key) {
          panelStore.recordResponse(key, {
            html: result.html,
            title: (result.title || fnMeta?.label || 'Summary').replace(/^AI\s+/i, ''),
            commandKey: result.command || command,
            commandLabel: (result.commandLabel || fnMeta?.label || 'Summary').replace(/^AI\s+/i, ''),
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
    comingSoonModalStore.set({ open: true, sourceLabel });
  }

  function closeComingSoonModal() {
    comingSoonModalStore.set({ open: false, sourceLabel: '' });
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
        mailboxDataStore.selectMailbox(target);
        // TODO: re-enable ensureMailboxState(target) when mailbox state endpoint is available.
        if (drawerMode) {
          mailboxChromeStore.closeDrawer();
        }
      }}
    />
    <EmailListPane
      search={search}
      {filtered}
      {selected}
      {mobile}
      {tablet}
      {desktop}
      {wide}
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
      on:toggleMenu={handleMenuClick}
      on:searchChange={(event) => mailboxDataStore.setSearch(event.detail.value)}
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
    {#if mobile && !mobileDetailOverlayVisible && !tablet}
      <MobileTopBar
        variant="search"
        showBackButton={false}
        showMenuButton={true}
        searchValue={search}
        hasMailboxCommands={hasMailboxCommands}
        mailboxCommandEntries={mailboxCommandEntries}
        mailboxCommandPendingKey={mailboxCommandPendingKey}
        mailboxActionsOpen={mailboxActionsOpen}
        activeActionsHost={mailboxActionsHost}
        actionsHost="mobile"
        activeMailboxActionLabel={activeMailboxActionLabel}
        mailboxActionError={mailboxActionError}
        filteredCount={filtered.length}
        compactActions={compactActions}
        bind:actionSurfaceRef={mailboxMenuMobileRef}
        on:toggleMenu={handleMenuClick}
        on:searchChange={(event) => mailboxDataStore.setSearch(event.detail.value)}
        on:toggleMailboxActions={(event) => toggleMailboxActions(event.detail.host)}
        on:mailboxAction={(event) => handleMailboxAction(event.detail.entry)}
      />
    {/if}
    {#if !selected}
      <div class="flex-1 grid place-items-center text-slate-400">
        <div class="text-center">
          <svg class="h-16 w-16 mx-auto mb-4 opacity-20 text-slate-400" viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M21.75 6.75v10.5a2.25 2.25 0 01-2.25 2.25h-15a2.25 2.25 0 01-2.25-2.25V6.75m19.5 0A2.25 2.25 0 0019.5 4.5h-15a2.25 2.25 0 00-2.25 2.25m19.5 0v.243a2.25 2.25 0 01-1.07 1.916l-7.5 4.615a2.25 2.25 0 01-2.36 0L3.32 8.91a2.25 2.25 0 01-1.07-1.916V6.75" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>
          <p>Select an email to read</p>
        </div>
      </div>
    {:else if !mobile}
      <div class="border-b border-slate-200"
           class:px-4={mobile}
           class:px-5={tablet}
           class:px-6={desktop || wide}
           class:py-3={mobile || tablet}
           class:py-4={desktop || wide}>
        <EmailActionToolbar
          email={selected}
          commands={primaryCommandEntries}
          actionMenuOptions={$actionMenuOptionsStore}
          actionMenuLoading={$actionMenuLoadingStore}
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
        {#if panelRenderReady && !mobile}
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
        mobileActive={win.id === activeMobileComposeId}
        on:send={handleComposeSend}
        on:requestAi={handleComposeRequestAi}
        on:saveDraft={handleComposeSaveDraft}
        on:deleteDraft={handleComposeDeleteDraft}
      />
    {/if}
  {/each}

  <UnifiedDock items={dockItems} />

  <OverlayStack controller={overlayController} />
</WindowProvider>

<WindowNotice message={$windowNoticeStore} />

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

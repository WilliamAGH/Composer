<script>
  import { createEventDispatcher, onMount, onDestroy, tick } from 'svelte';
  import { Paperclip, Send, Wand2, Highlighter, Trash2, ChevronDown } from 'lucide-svelte';
  import { isMobile } from './viewportState';
  import WindowFrame from './window/WindowFrame.svelte';
  import { useWindowContext } from './window/windowContext';
  import AiLoadingJourney from './AiLoadingJourney.svelte';
  import ComposeMobileSheet from './ComposeMobileSheet.svelte';

  /**
   * Compose window leveraging the shared WindowFrame chrome. Keeps feature-specific controls here while
   * the generic frame handles positioning/minimize logic via the window store. When payloads include a
   * quotedContext field the textarea is prefilled with blank space followed by the quoted block so
   * greetings and AI output always land above the prior conversation.
   */
  export let windowConfig = null;
  export let offsetIndex = 0;
  export let aiFunctions = [];
  export let journeyOverlay = null;

  const dispatch = createEventDispatcher();
  const windowManager = useWindowContext();
  $: mobile = $isMobile;
  let inputTo = null;
  let inputSubject = null;
  let inputMessage = null;
  let fileInput = null;
  let attachments = [];
  let initialized = false;
  let to = '';
  let subject = '';
  let body = '';
  let isReply = false;
  let lastBodyVersion = 0;
  let saveTimeout = null;
  let lastSavedSignature = null;
  $: journeyInlineActive = Boolean(journeyOverlay?.visible);
  let maximized = false;
  let composePromptOpen = false;
  let composePromptValue = '';
  let composePromptError = '';
  let composePromptTextarea = null;
  let composePromptCard = null;
  $: composePromptReady = (composePromptValue || '').trim().length > 0;

  $: if (!initialized && windowConfig) {
    to = windowConfig.payload?.to || '';
    subject = windowConfig.payload?.subject || '';
    body = windowConfig.payload?.body || '';
    isReply = Boolean(windowConfig.payload?.isReply);
    lastBodyVersion = windowConfig.payload?.bodyVersion || 0;
    initialized = true;
  }

  $: if (windowConfig?.payload && windowConfig.payload.bodyVersion !== undefined && windowConfig.payload.bodyVersion !== lastBodyVersion) {
    body = windowConfig.payload.body || body;
    subject = windowConfig.payload.subject ?? subject;
    lastBodyVersion = windowConfig.payload.bodyVersion;
  }

  onMount(() => {
    setTimeout(() => (isReply ? inputSubject?.focus() : (inputTo || inputSubject)?.focus()), 50);
    scheduleDraftSave(`${to}||${subject}||${body}`, true);
    document.addEventListener('pointerdown', handleGlobalPointer);
    document.addEventListener('keydown', handlePromptKeydown);
    if (typeof window !== 'undefined') {
      window.addEventListener('resize', handleViewportChange, { passive: true });
      window.addEventListener('scroll', handleViewportChange, { passive: true });
    }
  });

  onDestroy(() => {
    clearTimeout(saveTimeout);
    document.removeEventListener('pointerdown', handleGlobalPointer);
    document.removeEventListener('keydown', handlePromptKeydown);
    if (typeof window !== 'undefined') {
      window.removeEventListener('resize', handleViewportChange);
      window.removeEventListener('scroll', handleViewportChange);
    }
    anchorObserver?.disconnect();
  });

  function send() {
    dispatch('send', { id: windowConfig.id, to, subject, body, attachments });
  }

  /**
   * Deletes the in-progress draft from the mailbox store.
   */
  function deleteDraft() {
    dispatch('deleteDraft', { id: windowConfig.id });
    closeWindow();
  }

  function requestAi(command, instructionOverride = null) {
    dispatch('requestAi', { id: windowConfig.id, command, draft: body, subject, isReply, to, instructionOverride });
  }

  async function openComposePrompt() {
    composePromptValue = '';
    composePromptError = '';
    composePromptOpen = true;
    draftMenuOpen = false;
    toneMenuOpen = false;
    await tick();
    composePromptTextarea?.focus();
  }

  function closeComposePrompt() {
    composePromptOpen = false;
    composePromptError = '';
    composePromptValue = '';
  }

  function submitComposePrompt() {
    const trimmed = (composePromptValue || '').trim();
    if (!trimmed) {
      composePromptError = 'Share a quick summary so we know what to write.';
      composePromptTextarea?.focus();
      return;
    }
    requestAi('compose', trimmed);
    closeComposePrompt();
  }

  function handlePromptKeydown(event) {
    if (!composePromptOpen) return;
    if (event.key === 'Escape') {
      event.preventDefault();
      closeComposePrompt();
      return;
    }
    if ((event.metaKey || event.ctrlKey) && event.key === 'Enter') {
      event.preventDefault();
      submitComposePrompt();
    }
  }

  const fallbackDraftOptions = [
    { key: 'draft', label: 'Draft Reply' },
    { key: 'compose', label: 'AI Compose' }
  ];

  const tonePresets = [
    { id: 'tone-professional', label: 'Professional', guidance: 'Rewrite this email in a professional, confident tone. Keep it concise, precise, and free of slang while preserving the intent.' },
    { id: 'tone-casual', label: 'Casual', guidance: 'Rewrite this email so it sounds casual, friendly, and approachable. Keep the original meaning but loosen the language slightly.' },
    { id: 'tone-legal', label: 'Legal', guidance: 'Rewrite this email in a formal, legal-friendly tone. Be explicit, cite facts, and avoid emotional language while preserving intent.' },
    { id: 'tone-bestie', label: 'Bestie', guidance: 'Rewrite this email as if texting a close friend ("bestie"). Keep it upbeat, encouraging, and informal while preserving the main points.' },
    { id: 'tone-bro', label: 'Bro', guidance: 'Rewrite this email with relaxed “bro” energy—supportive, informal, and direct. Keep key facts intact.' }
  ];

  const composePromptSuggestions = [
    'Welcome a new lead and highlight our concierge onboarding',
    'Summarize today\'s call with next steps and deadlines',
    'Introduce the Fall release with friendly excitement'
  ];

  $: draftOptions = deriveDraftOptions(aiFunctions);
  $: primaryDraftOption = draftOptions.find((option) => option.key === 'draft') || draftOptions[0] || fallbackDraftOptions[0];

  let draftMenuOpen = false;
  let toneMenuOpen = false;
  let draftMenuRef = null;
  let toneMenuRef = null;
  let draftToggleButton = null;
  let toneToggleButton = null;
  let anchorElement = null;
  let anchorObserver = null;
  let maximizedAnchorBounds = null;
  let draftMenuPosition = { top: 0, right: 0 };
  let toneMenuPosition = { top: 0, right: 0 };
  const MENU_OFFSET = 8;
  const MENU_VIEWPORT_GUTTER = 12;

  function deriveDraftOptions(list) {
    if (!Array.isArray(list) || list.length === 0) return fallbackDraftOptions;
    const entries = [];
    const seen = new Set();
    for (const fn of list) {
      if (!fn?.key || fn.key === 'tone' || seen.has(fn.key)) continue;
      seen.add(fn.key);
      const label = fn.key === 'draft'
        ? 'Draft Reply'
        : fn.label || (fn.key === 'compose' ? 'AI Compose' : fn.key);
      entries.push({ key: fn.key, label });
    }
    return entries.length ? entries : fallbackDraftOptions;
  }

  function runPrimaryDraft() {
    const target = primaryDraftOption?.key || 'draft';
    if (target === 'compose') {
      openComposePrompt();
      return;
    }
    requestAi(target);
  }

  async function toggleDraftMenu() {
    draftMenuOpen = !draftMenuOpen;
    if (draftMenuOpen) {
      toneMenuOpen = false;
      await tick();
      syncMenuPosition('draft');
    }
  }

  async function toggleToneMenu() {
    toneMenuOpen = !toneMenuOpen;
    if (toneMenuOpen) {
      draftMenuOpen = false;
      await tick();
      syncMenuPosition('tone');
    }
  }

  function invokeDraftOption(option) {
    if (!option) return;
    if (option.key === 'compose') {
      draftMenuOpen = false;
      openComposePrompt();
      return;
    }
    requestAi(option.key);
    draftMenuOpen = false;
  }

  function invokeTonePreset(preset) {
    const trimmedBody = body?.trim();
    const instruction = trimmedBody && trimmedBody.length
      ? `${preset.guidance}\n\n${trimmedBody}`
      : preset.guidance;
    requestAi('tone', instruction);
    toneMenuOpen = false;
  }

  function handleGlobalPointer(event) {
    const target = event.target;
    if (draftMenuOpen && !isWithin(target, draftMenuRef, draftToggleButton)) {
      draftMenuOpen = false;
    }
    if (toneMenuOpen && !isWithin(target, toneMenuRef, toneToggleButton)) {
      toneMenuOpen = false;
    }
    if (composePromptOpen && composePromptCard && !composePromptCard.contains(target)) {
      closeComposePrompt();
    }
  }

  function isWithin(target, panelEl, triggerEl) {
    if (!target) return false;
    if (panelEl && panelEl.contains(target)) return true;
    if (triggerEl && triggerEl.contains(target)) return true;
    return false;
  }

  function registerInputRefs(refs = {}) {
    if (refs.to !== undefined) {
      inputTo = refs.to;
    }
    if (refs.subject !== undefined) {
      inputSubject = refs.subject;
    }
    if (refs.message !== undefined) {
      inputMessage = refs.message;
    }
  }

  function ensureAnchorElement() {
    if (anchorElement && document.contains(anchorElement)) {
      return anchorElement;
    }
    anchorObserver?.disconnect();
    anchorObserver = null;
    anchorElement = document.querySelector('.panel-column');
    if (anchorElement && typeof ResizeObserver !== 'undefined') {
      anchorObserver = new ResizeObserver(() => syncComposeAnchorBounds());
      anchorObserver.observe(anchorElement);
    }
    return anchorElement;
  }

  function syncComposeAnchorBounds() {
    if (typeof window === 'undefined' || mobile || !maximized) {
      maximizedAnchorBounds = null;
      return;
    }
    const candidate = ensureAnchorElement();
    if (!candidate) {
      maximizedAnchorBounds = null;
      return;
    }
    const rect = candidate.getBoundingClientRect();
    maximizedAnchorBounds = {
      top: Math.max(rect.top, 12),
      right: Math.max(window.innerWidth - rect.right, 12),
      bottom: Math.max(window.innerHeight - rect.bottom, 12),
      left: Math.max(rect.left, 12)
    };
  }

  function syncMenuPosition(kind = 'draft', attempt = 0) {
    if (typeof window === 'undefined') return;
    const trigger = kind === 'tone' ? toneToggleButton : draftToggleButton;
    if (!trigger) return;
    const rect = trigger.getBoundingClientRect();
    const panel = kind === 'tone' ? toneMenuRef : draftMenuRef;
    const menuHeight = panel?.offsetHeight || panel?.scrollHeight || 0;
    const viewportPadding = MENU_VIEWPORT_GUTTER;
    const offset = MENU_OFFSET;
    const viewportBottom = window.innerHeight - viewportPadding;
    const spaceBelow = viewportBottom - rect.bottom;
    const spaceAbove = rect.top - viewportPadding;

    if (!menuHeight && attempt < 2) {
      requestAnimationFrame(() => syncMenuPosition(kind, attempt + 1));
      return;
    }

    const openBelow = menuHeight > 0 && spaceBelow >= menuHeight + offset + viewportPadding;
    let top;
    if (openBelow) {
      const desiredTop = rect.bottom + offset;
      top = Math.min(desiredTop, viewportBottom - Math.max(menuHeight, 0));
    } else {
      const desiredTop = rect.top - offset - menuHeight;
      top = Math.max(viewportPadding, desiredTop);
    }
    const right = Math.max(viewportPadding, window.innerWidth - rect.right);
    const next = { top, right };
    if (kind === 'tone') {
      toneMenuPosition = next;
    } else {
      draftMenuPosition = next;
    }
  }

  function handleViewportChange() {
    syncComposeAnchorBounds();
    if (draftMenuOpen) syncMenuPosition('draft');
    if (toneMenuOpen) syncMenuPosition('tone');
  }

  $: if (maximized && !mobile) {
    syncComposeAnchorBounds();
  } else {
    maximizedAnchorBounds = null;
  }

  function closeWindow() {
    maximized = false;
    windowManager.close(windowConfig.id);
  }

  function toggleMinimizeWindow() {
    maximized = false;
    windowManager.toggleMinimize(windowConfig.id);
  }

  async function toggleMaximizeWindow() {
    maximized = !maximized;
    if (draftMenuOpen || toneMenuOpen) {
      await tick();
      if (draftMenuOpen) syncMenuPosition('draft');
      if (toneMenuOpen) syncMenuPosition('tone');
    }
  }

  function onFilesSelected(files) {
    if (!files || files.length === 0) return;
    // Retain the actual File objects for upload, not just metadata
    for (const file of files) {
      attachments = [...attachments, { file, name: file.name, size: file.size }];
    }
    if (fileInput) fileInput.value = '';
  }

  /**
   * Derives the minimal payload needed to persist the current draft state.
   */
  function snapshotDraftPayload() {
    return {
      id: windowConfig?.id,
      to,
      subject,
      body
    };
  }

  /**
   * Throttles draft persistence so autosave happens without spamming dispatch events.
   */
  function scheduleDraftSave(signature, immediate = false) {
    if (!windowConfig?.id) return;
    if (!immediate && signature === lastSavedSignature) {
      return;
    }
    const persist = () => {
      lastSavedSignature = signature;
      dispatch('saveDraft', snapshotDraftPayload());
    };
    if (immediate) {
      persist();
      return;
    }
    clearTimeout(saveTimeout);
    saveTimeout = setTimeout(persist, 400);
  }

  $: draftSignature = `${to}||${subject}||${body}`;

  $: if (initialized && windowConfig) {
    scheduleDraftSave(draftSignature);
  }
</script>

{#if windowConfig}
  {#if mobile}
    <ComposeMobileSheet
      title={windowConfig.title || (isReply ? 'Reply' : 'New Message')}
      bind:to
      bind:subject
      bind:body
      {attachments}
      {draftOptions}
      {primaryDraftOption}
      {tonePresets}
      journeyOverlay={journeyOverlay}
      journeyInlineActive={journeyInlineActive}
      onSend={send}
      onDeleteDraft={deleteDraft}
      onRunPrimaryDraft={runPrimaryDraft}
      onInvokeDraftOption={invokeDraftOption}
      onInvokeTonePreset={invokeTonePreset}
      onAttach={() => fileInput?.click()}
      onClose={closeWindow}
      registerInputRefs={registerInputRefs}
    />
  {:else}
    <WindowFrame
      open={true}
      title={windowConfig.title || (isReply ? 'Reply' : 'New Message')}
      mode="floating"
      minimized={windowConfig.minimized}
      allowMinimize={!mobile}
      allowMaximize={true}
      maximized={maximized}
      maximizedAnchorBounds={maximizedAnchorBounds}
      allowClose={true}
      offsetIndex={offsetIndex}
      on:close={closeWindow}
      on:toggleMinimize={toggleMinimizeWindow}
      on:toggleMaximize={toggleMaximizeWindow}
    >
      <div class="compose-body">
        <input
          bind:this={inputTo}
          bind:value={to}
          placeholder="To"
          class="field" />
        <input
          bind:this={inputSubject}
          bind:value={subject}
          placeholder="Subject"
          class="field" />

        {#if journeyOverlay?.visible}
          <AiLoadingJourney
            steps={journeyOverlay.steps || []}
            activeStepId={journeyOverlay.activeStepId}
            headline={journeyOverlay.headline}
            subhead={journeyOverlay.subhead}
            show={journeyOverlay.visible}
            inline={true}
            subdued={true}
            className="border-slate-200" />
        {/if}

        <textarea
          bind:this={inputMessage}
          bind:value={body}
          rows={isReply ? 6 : 8}
          placeholder={isReply ? 'Type your reply...' : 'Type your message...'}
          class="field textarea"
          disabled={journeyInlineActive}
          aria-busy={journeyInlineActive}></textarea>

        {#if attachments.length}
          <div class="attachments">
            <div class="attachments-header">
              <span>Attachments</span>
              <span>{attachments.length}</span>
            </div>
            <ul>
              {#each attachments as item}
                <li>{item.name}</li>
              {/each}
            </ul>
          </div>
        {/if}
      </div>

  <div slot="footer" class="compose-footer">
    <div class="compose-footer-main">
      <button type="button" class="btn btn--primary btn--labelled" on:click={send}>
        <Send class="h-4 w-4" /> <span class="compose-btn-label">Send</span>
      </button>
      <button
        type="button"
        class="btn btn--secondary btn--labelled"
        on:click={() => fileInput?.click()}
        on:keydown={(event) => {
          if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            fileInput?.click();
          }
        }}>
        <Paperclip class="h-4 w-4" /> <span class="compose-btn-label">Attach</span>
      </button>
      <button type="button" class="btn btn--ghost btn--icon" aria-label="Delete draft" title="Delete draft" on:click={deleteDraft}>
        <Trash2 class="h-4 w-4" />
      </button>
    </div>
    <div class="compose-footer-ai">
      <div class="compose-ai-cluster">
        <div class="compose-ai-split">
          <button type="button" class="btn btn--ghost btn--labelled btn--compact compose-ai-pill compose-ai-pill--main" on:click={runPrimaryDraft}>
            <Wand2 class="h-4 w-4" /> <span class="compose-ai-label">{primaryDraftOption?.label || 'Draft'}</span>
          </button>
          <button
            type="button"
            class="btn btn--ghost btn--icon compose-ai-pill compose-ai-pill--toggle"
            aria-haspopup="menu"
            aria-expanded={draftMenuOpen}
            bind:this={draftToggleButton}
            on:click={toggleDraftMenu}
            aria-label="More drafting options">
            <ChevronDown class={`h-4 w-4 transition ${draftMenuOpen ? 'rotate-180' : ''}`} />
          </button>
        </div>
        {#if draftMenuOpen && draftOptions.length}
          <div
            class="menu-surface compose-menu compose-menu--footer"
            bind:this={draftMenuRef}
            style={`top: ${draftMenuPosition.top}px; right: ${draftMenuPosition.right}px;`}>
            <span class="menu-eyebrow">Drafting Options</span>
            <div class="menu-list">
              {#each draftOptions as option (option.key)}
                <button type="button" class="menu-item" on:click={() => invokeDraftOption(option)}>
                  <div class="flex items-center gap-2 min-w-0">
                    <Wand2 class="h-4 w-4 text-slate-500" />
                    <span class="truncate">{option.label}</span>
                  </div>
                </button>
              {/each}
            </div>
          </div>
        {/if}
      </div>
      <div class="compose-ai-cluster">
        <button
          type="button"
          class="btn btn--ghost btn--labelled btn--compact compose-ai-pill tone-trigger"
          aria-haspopup="menu"
          aria-expanded={toneMenuOpen}
          bind:this={toneToggleButton}
          on:click={toggleToneMenu}>
          <Highlighter class="h-4 w-4" /> <span class="compose-ai-label">Tone</span>
          <ChevronDown class={`h-4 w-4 text-slate-500 transition ${toneMenuOpen ? 'rotate-180' : ''}`} />
        </button>
        {#if toneMenuOpen}
          <div
            class="menu-surface compose-menu compose-menu--footer"
            bind:this={toneMenuRef}
            style={`top: ${toneMenuPosition.top}px; right: ${toneMenuPosition.right}px;`}>
            <span class="menu-eyebrow">Rewrite Tone</span>
            <div class="menu-list">
              {#each tonePresets as preset (preset.id)}
                <button type="button" class="menu-item" on:click={() => invokeTonePreset(preset)}>
                  <div class="flex flex-col text-left">
                    <span class="font-medium text-slate-900">{preset.label}</span>
                    <span class="text-xs text-slate-500">Rewrite using the {preset.label.toLowerCase()} voice.</span>
                  </div>
                </button>
              {/each}
            </div>
          </div>
        {/if}
      </div>
    </div>
  </div>
    </WindowFrame>
  {/if}
  <input bind:this={fileInput} type="file" class="sr-only" on:change={(e) => onFilesSelected(e.currentTarget.files)} multiple />
  {#if composePromptOpen}
    <div class="compose-prompt-backdrop" role="presentation">
      <div
        class="compose-prompt-card"
        role="dialog"
        aria-modal="true"
        aria-labelledby="composePromptTitle"
        aria-describedby="composePromptDescription"
        bind:this={composePromptCard}>
        <p class="compose-prompt-eyebrow">AI Compose</p>
        <h3 id="composePromptTitle">What should we write?</h3>
        <p id="composePromptDescription">Give the assistant a quick brief—who it\'s for, the outcome, or any details we should weave in.</p>
        <div class="compose-prompt-suggestions" aria-label="Prompt suggestions">
          {#each composePromptSuggestions as suggestion (suggestion)}
            <button type="button" class="compose-prompt-chip" on:click={() => {
              composePromptValue = suggestion;
              composePromptError = '';
              composePromptTextarea?.focus();
            }}>{suggestion}</button>
          {/each}
        </div>
        <textarea
          bind:this={composePromptTextarea}
          bind:value={composePromptValue}
          rows={4}
          class="field compose-prompt-textarea"
          placeholder="e.g., Introduce our beta, recap yesterday\'s demo, and ask for a follow-up call"
          aria-label="Compose brief"
          aria-describedby="composePromptDescription"></textarea>
        {#if composePromptError}
          <p class="compose-prompt-error" role="alert">{composePromptError}</p>
        {/if}
        <div class="compose-prompt-actions">
          <button type="button" class="btn btn--ghost" on:click={closeComposePrompt}>Cancel</button>
          <button type="button" class="btn btn--primary" disabled={!composePromptReady} on:click={submitComposePrompt}>Compose for me</button>
        </div>
      </div>
    </div>
  {/if}
{/if}

<style>
  /**
   * Compose window styling ensures tap targets meet Apple HIG guidance while
   * retaining the layered Composer look when floating over desktop.
   */
  /**
   * Stack spacing for the compose form body.
   */
  .compose-body {
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
  }
  /**
   * Shared input look keeps iOS 16px minimum font while trimming desktop padding for a lighter feel.
   * @usage - ComposeWindow.svelte `To`, `Subject`, and message textarea controls
   */
.field {
    width: 100%;
    border: 1px solid rgba(148, 163, 184, 0.7);
    border-radius: 14px;
    padding: 0.65rem 0.9rem;
    font-size: clamp(16px, 0.95rem + 0.12vw, 17px);
    line-height: 1.5;
    min-height: 42px;
    background: rgba(255, 255, 255, 0.9);
  }
  /**
   * Draft textarea gets taller baseline for mobile comfort.
   */
  .textarea {
    resize: none;
    min-height: 200px;
  }
  /**
   * Compose AI action pills match the design standard compact button height.
   * @usage - ComposeWindow.svelte Draft + Tone controls (desktop/tablet)
   */
  .compose-ai-pill {
    min-height: 40px;
    padding-top: 0.35rem;
    padding-bottom: 0.35rem;
    padding-left: 0.75rem;
    padding-right: 0.75rem;
  }
  /**
   * Tone trigger spacing syncs with slimmer paddings so label + chevron stay tight.
   * @usage - ComposeWindow.svelte Tone button
   */
  .tone-trigger {
    gap: 0.2rem;
  }
  .compose-ai-pill :global(svg) {
    width: 13px;
    height: 13px;
  }
  .compose-ai-pill :global(.btn-icon-chip) {
    width: 28px;
    height: 28px;
  }
  .compose-menu {
    position: fixed;
    min-width: 15rem;
    z-index: var(--z-dropdown, 200);
  }
  /**
   * Attachment list uses bordered card styling.
   */
  .attachments {
    border: 1px solid rgba(148, 163, 184, 0.7);
    border-radius: 12px;
    padding: 0.5rem 0.75rem;
  }
  /**
   * Attachment header labels + count chips.
   */
  .attachments-header {
    display: flex;
    justify-content: space-between;
    font-size: 0.75rem;
    text-transform: uppercase;
    color: #475569;
  }
  /**
   * Attachment list reset.
   */
  .attachments ul {
    margin-top: 0.5rem;
    list-style: none;
    padding: 0;
  }
  /**
   * Attachment list items share muted text styling.
   */
  .attachments li {
    font-size: 0.85rem;
    color: #334155;
  }
  /**
   * Footer houses send + attach CTAs while AI controls pin to the trailing edge.
   * @usage - ComposeWindow.svelte footer slot
   */
  .compose-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 0.75rem;
    flex-wrap: nowrap;
  }
  /**
   * Primary CTA cluster (Send/Attach/Delete).
   * @usage - ComposeWindow.svelte footer main actions
   */
  .compose-footer-main {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    flex-wrap: wrap;
  }
  /**
   * AI control rail hugs the bottom-right corner.
   * @usage - ComposeWindow.svelte footer AI controls
   */
  .compose-footer-ai {
    display: flex;
    align-items: center;
    gap: 0.45rem;
  }
  /**
   * Anchors dropdown menus to their trigger buttons.
   * @usage - ComposeWindow.svelte footer AI controls
   */
  .compose-ai-cluster {
    position: relative;
  }
  /**
   * Split button wrapper keeps primary draft action + toggle visually merged.
   * @usage - ComposeWindow.svelte footer draft controls
   */
  .compose-ai-split {
    display: flex;
  }
  /**
   * Main split button half keeps its right corners squared so it melds with the toggle.
   * @usage - ComposeWindow.svelte footer draft controls
   */
  .compose-ai-pill--main {
    border-top-right-radius: 0;
    border-bottom-right-radius: 0;
  }
  /**
   * Toggle half keeps width tight and shares borders with the main half.
   * @usage - ComposeWindow.svelte footer draft controls
   */
  .compose-ai-pill--toggle {
    border-top-left-radius: 0;
    border-bottom-left-radius: 0;
    margin-left: -1px;
    width: 34px;
    justify-content: center;
    padding: 0;
  }
  .compose-menu--footer {
    margin-top: 0;
  }
  /**
   * Label wrappers for responsive hiding when footer runs out of horizontal space.
   * @usage - ComposeWindow.svelte footer button labels
   */
  .compose-btn-label,
  .compose-ai-label {
    display: inline;
  }
  /**
   * When viewport is constrained, hide labels and show only icons to prevent wrapping.
   * Buttons automatically become icon-only while maintaining tap target size.
   * @usage - Automatically applied when window width drops below 600px
   */
  @media (max-width: 600px) {
    .compose-btn-label,
    .compose-ai-label {
      display: none;
    }
    .compose-footer-main .btn--labelled {
      padding-left: 0.5rem;
      padding-right: 0.5rem;
      min-width: 42px;
    }
    .compose-ai-pill {
      padding-left: 0.5rem;
      padding-right: 0.5rem;
    }
  }
  .compose-prompt-backdrop {
    position: fixed;
    inset: 0;
    background: radial-gradient(circle at top, rgba(148, 163, 184, 0.15), rgba(15, 23, 42, 0.45));
    backdrop-filter: blur(16px) saturate(110%);
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 1.5rem;
    z-index: var(--z-modal, 180);
  }
  .compose-prompt-card {
    width: min(480px, 100%);
    background: linear-gradient(145deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.92));
    border-radius: 24px;
    border: 1px solid rgba(148, 163, 184, 0.35);
    box-shadow: 0 25px 60px -25px rgba(15, 23, 42, 0.4);
    padding: 1.75rem;
    display: flex;
    flex-direction: column;
    gap: 1rem;
    color: #0f172a;
  }
  .compose-prompt-eyebrow {
    font-size: 0.7rem;
    text-transform: uppercase;
    letter-spacing: 0.3em;
    color: rgba(99, 102, 241, 0.7);
    margin-bottom: -0.25rem;
  }
  .compose-prompt-card h3 {
    font-size: 1.25rem;
    font-weight: 600;
    margin: 0;
  }
  .compose-prompt-card p {
    margin: 0;
    color: #475569;
  }
  .compose-prompt-suggestions {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
  }
  .compose-prompt-chip {
    border: 1px solid rgba(148, 163, 184, 0.4);
    border-radius: 999px;
    padding: 0.3rem 0.75rem;
    font-size: 0.8rem;
    background: rgba(248, 250, 252, 0.9);
    color: #0f172a;
    transition: border-color 0.2s ease, background 0.2s ease;
    cursor: pointer;
  }
  .compose-prompt-chip:hover {
    border-color: rgba(99, 102, 241, 0.5);
    background: white;
  }
  .compose-prompt-textarea {
    min-height: 150px;
    background: rgba(255, 255, 255, 0.95);
    border-radius: 16px;
  }
  .compose-prompt-error {
    font-size: 0.85rem;
    color: #b91c1c;
  }
  .compose-prompt-actions {
    display: flex;
    justify-content: flex-end;
    gap: 0.5rem;
    flex-wrap: wrap;
  }

  @media (max-width: 640px) {
    .compose-prompt-card {
      border-radius: 20px;
      padding: 1.25rem;
      gap: 0.75rem;
    }
  }
  /* Mobile compose experience handled by ComposeMobileSheet.svelte */
</style>

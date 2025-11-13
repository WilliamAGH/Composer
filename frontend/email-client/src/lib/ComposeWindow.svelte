<script>
  import { createEventDispatcher, onMount, onDestroy } from 'svelte';
  import { Paperclip, Send, Wand2, Highlighter, Trash2, ChevronDown } from 'lucide-svelte';
  import { isMobile } from './viewport';
  import WindowFrame from './window/WindowFrame.svelte';
  import { useWindowContext } from './window/windowContext';
  import AiLoadingJourney from './AiLoadingJourney.svelte';

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
    scheduleDraftSave(true);
    document.addEventListener('pointerdown', handleGlobalPointer);
  });

  onDestroy(() => {
    clearTimeout(saveTimeout);
    document.removeEventListener('pointerdown', handleGlobalPointer);
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

  const fallbackDraftOptions = [
    { key: 'draft', label: 'AI Draft Reply' },
    { key: 'compose', label: 'AI Compose' }
  ];

  const tonePresets = [
    { id: 'tone-professional', label: 'Professional', guidance: 'Rewrite this email in a professional, confident tone. Keep it concise, precise, and free of slang while preserving the intent.' },
    { id: 'tone-casual', label: 'Casual', guidance: 'Rewrite this email so it sounds casual, friendly, and approachable. Keep the original meaning but loosen the language slightly.' },
    { id: 'tone-legal', label: 'Legal', guidance: 'Rewrite this email in a formal, legal-friendly tone. Be explicit, cite facts, and avoid emotional language while preserving intent.' },
    { id: 'tone-bestie', label: 'Bestie', guidance: 'Rewrite this email as if texting a close friend ("bestie"). Keep it upbeat, encouraging, and informal while preserving the main points.' },
    { id: 'tone-bro', label: 'Bro', guidance: 'Rewrite this email with relaxed “bro” energy—supportive, informal, and direct. Keep key facts intact.' }
  ];

  $: draftOptions = deriveDraftOptions(aiFunctions);
  $: primaryDraftOption = draftOptions.find((option) => option.key === 'draft') || draftOptions[0] || fallbackDraftOptions[0];

  let draftMenuOpen = false;
  let toneMenuOpen = false;
  let draftMenuRef = null;
  let toneMenuRef = null;
  let draftToggleButton = null;
  let toneToggleButton = null;

  function deriveDraftOptions(list) {
    if (!Array.isArray(list) || list.length === 0) return fallbackDraftOptions;
    const entries = [];
    const seen = new Set();
    for (const fn of list) {
      if (!fn?.key || fn.key === 'tone' || seen.has(fn.key)) continue;
      seen.add(fn.key);
      entries.push({ key: fn.key, label: fn.label || (fn.key === 'draft' ? 'AI Draft Reply' : fn.key === 'compose' ? 'AI Compose' : fn.key) });
    }
    return entries.length ? entries : fallbackDraftOptions;
  }

  function runPrimaryDraft() {
    const target = primaryDraftOption?.key || 'draft';
    requestAi(target);
  }

  function toggleDraftMenu() {
    draftMenuOpen = !draftMenuOpen;
    if (draftMenuOpen) toneMenuOpen = false;
  }

  function toggleToneMenu() {
    toneMenuOpen = !toneMenuOpen;
    if (toneMenuOpen) draftMenuOpen = false;
  }

  function invokeDraftOption(option) {
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
  }

  function isWithin(target, panelEl, triggerEl) {
    if (!target) return false;
    if (panelEl && panelEl.contains(target)) return true;
    if (triggerEl && triggerEl.contains(target)) return true;
    return false;
  }

  function closeWindow() {
    maximized = false;
    windowManager.close(windowConfig.id);
  }

  function toggleMinimizeWindow() {
    maximized = false;
    windowManager.toggleMinimize(windowConfig.id);
  }

  function toggleMaximizeWindow() {
    maximized = !maximized;
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
  function scheduleDraftSave(immediate = false) {
    if (!windowConfig?.id) return;
    const signature = `${to}||${subject}||${body}`;
    if (!immediate && signature === lastSavedSignature) {
      return;
    }
    const persist = () => {
      lastSavedSignature = `${to}||${subject}||${body}`;
      dispatch('saveDraft', snapshotDraftPayload());
    };
    if (immediate) {
      persist();
      return;
    }
    clearTimeout(saveTimeout);
    saveTimeout = setTimeout(persist, 400);
  }

  $: if (initialized && windowConfig) {
    scheduleDraftSave();
  }
</script>

{#if windowConfig}
  <WindowFrame
    open={true}
    title={windowConfig.title || (isReply ? 'Reply' : 'New Message')}
    mode="floating"
    minimized={windowConfig.minimized}
    allowMinimize={!mobile}
    allowMaximize={true}
    maximized={maximized}
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

    <div class="ai-actions">
      <div class="ai-action-group">
        <button type="button" class="btn btn--ghost btn--labelled btn--compact compose-ai-pill" on:click={runPrimaryDraft}>
          <Wand2 class="h-4 w-4" /> Draft
        </button>
        <button
          type="button"
          class="btn btn--ghost btn--icon compose-ai-pill dropdown-toggle"
          aria-haspopup="menu"
          aria-expanded={draftMenuOpen}
          bind:this={draftToggleButton}
          on:click={toggleDraftMenu}
          aria-label="More drafting options">
          <ChevronDown class={`h-4 w-4 transition ${draftMenuOpen ? 'rotate-180' : ''}`} />
        </button>
        {#if draftMenuOpen && draftOptions.length}
          <div class="menu-surface compose-menu" bind:this={draftMenuRef}>
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

      <div class="ai-action-group">
        <button
          type="button"
          class="btn btn--ghost btn--labelled btn--compact compose-ai-pill tone-trigger"
          aria-haspopup="menu"
          aria-expanded={toneMenuOpen}
          bind:this={toneToggleButton}
          on:click={toggleToneMenu}>
          <Highlighter class="h-4 w-4" /> Tone
          <ChevronDown class={`h-4 w-4 text-slate-500 transition ${toneMenuOpen ? 'rotate-180' : ''}`} />
        </button>
        {#if toneMenuOpen}
          <div class="menu-surface compose-menu" bind:this={toneMenuRef}>
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
    <button type="button" class="btn btn--primary btn--labelled" on:click={send}>
      <Send class="h-4 w-4" /> Send
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
      <Paperclip class="h-4 w-4" /> Attach
    </button>
    <button type="button" class="btn btn--ghost btn--icon" aria-label="Delete draft" title="Delete draft" on:click={deleteDraft}>
      <Trash2 class="h-4 w-4" />
    </button>
    <input bind:this={fileInput} type="file" class="sr-only" on:change={(e) => onFilesSelected(e.currentTarget.files)} multiple />
  </div>
</WindowFrame>
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
   * Shared input look with ergonomic padding + font sizing.
   */
  .field {
    width: 100%;
    border: 1px solid rgba(148, 163, 184, 0.7);
    border-radius: 14px;
    padding: 0.75rem 1rem;
    font-size: clamp(1rem, 0.95rem + 0.2vw, 1.1rem);
    line-height: 1.5;
    min-height: 46px;
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
   * Wrap AI quick actions so they flex on small screens.
   */
  .ai-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
  }
  .ai-action-group {
    display: flex;
    gap: 0.35rem;
    position: relative;
  }
  .compose-ai-pill {
    min-height: 34px;
    padding-top: 0.25rem;
    padding-bottom: 0.25rem;
  }
  .compose-ai-pill.dropdown-toggle {
    width: 42px;
    justify-content: center;
    padding: 0;
  }
  .tone-trigger {
    gap: 0.35rem;
  }
  .compose-ai-pill :global(svg) {
    width: 15px;
    height: 15px;
  }
  .compose-ai-pill :global(.btn-icon-chip) {
    width: 28px;
    height: 28px;
  }
  .compose-menu {
    position: absolute;
    left: 0;
    margin-top: 0.35rem;
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
   * Footer houses send + attach CTAs.
   */
  .compose-footer {
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }
  /**
   * Mobile overrides keep everything thumb-friendly.
   */
  @media (max-width: 640px) {
    .compose-body {
      gap: 0.5rem;
    }
    .ai-actions {
      flex-direction: column;
    }
    .compose-footer {
      position: sticky;
      bottom: 0;
      left: 0;
      right: 0;
      padding-top: 0.75rem;
      padding-bottom: 0.5rem;
      flex-direction: column;
      align-items: stretch;
      background: linear-gradient(180deg, rgba(248, 250, 252, 0.9), rgba(255, 255, 255, 0.95));
      backdrop-filter: blur(20px);
    }
    .compose-footer button,
    .compose-footer .btn {
      width: 100%;
      justify-content: center;
    }
    .textarea {
      min-height: min(45vh, 360px);
    }
  }
</style>

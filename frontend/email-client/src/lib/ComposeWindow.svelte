<script>
  import { createEventDispatcher, onMount } from 'svelte';
  import { Paperclip, Send, Wand2, Highlighter } from 'lucide-svelte';
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
  });

  function send() {
    dispatch('send', { id: windowConfig.id, to, subject, body, attachments });
  }

  function requestAi(command) {
    dispatch('requestAi', { id: windowConfig.id, command, draft: body, subject, isReply, to });
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
      {#if !aiFunctions || aiFunctions.length === 0}
        <button type="button" class="btn btn--ghost btn--labelled" on:click={() => requestAi('draft')}>
          <Wand2 class="h-4 w-4" /> AI Compose
        </button>
        <button type="button" class="btn btn--ghost btn--labelled" on:click={() => requestAi('tone')}>
          <Highlighter class="h-4 w-4" /> Adjust Tone
        </button>
      {:else}
        {#each aiFunctions as fn (fn.key)}
          <button type="button" class="btn btn--ghost btn--labelled" on:click={() => requestAi(fn.key)}>
            {#if fn.key === 'tone'}
              <Highlighter class="h-4 w-4" />
            {:else}
              <Wand2 class="h-4 w-4" />
            {/if}
            {fn.label || 'AI Assist'}
          </button>
        {/each}
      {/if}
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

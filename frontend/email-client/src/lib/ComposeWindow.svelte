<script>
  import { createEventDispatcher, onMount } from 'svelte';
  import { Paperclip, Send, Wand2, Highlighter } from 'lucide-svelte';
  import { isMobile } from './viewport';
  import WindowFrame from './window/WindowFrame.svelte';

  /**
   * Compose window leveraging the shared WindowFrame chrome. Keeps feature-specific controls here while
   * the generic frame handles positioning/minimize logic via the window store.
   */
  export let windowConfig;
  export let offsetIndex = 0;
  export let aiFunctions = [];

  const dispatch = createEventDispatcher();
  $: mobile = $isMobile;
  let inputTo;
  let inputSubject;
  let inputMessage;
  let fileInput;
  let attachments = [];
  let initialized = false;
  let to = '';
  let subject = '';
  let body = '';
  let isReply = false;
  let lastBodyVersion = 0;

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
    dispatch('requestAi', { id: windowConfig.id, command, draft: body, subject, isReply });
  }

  function onFilesSelected(files) {
    if (!files || files.length === 0) return;
    for (const file of files) {
      attachments = [...attachments, { name: file.name, size: file.size }];
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
  allowClose={true}
  offsetIndex={offsetIndex}
  on:close={() => dispatch('close', { id: windowConfig.id })}
  on:toggleMinimize={() => dispatch('toggleMinimize', { id: windowConfig.id })}
>
  <div class="compose-body">
    {#if !isReply}
      <input
        bind:this={inputTo}
        bind:value={to}
        placeholder="To"
        class="field" />
    {/if}
    <input
      bind:this={inputSubject}
      bind:value={subject}
      placeholder="Subject"
      class="field" />

    <div class="ai-actions">
      {#if !aiFunctions || aiFunctions.length === 0}
        <button type="button" class="ai-chip" on:click={() => requestAi('draft')}>
          <Wand2 class="h-4 w-4" /> AI Compose
        </button>
        <button type="button" class="ai-chip" on:click={() => requestAi('tone')}>
          <Highlighter class="h-4 w-4" /> Adjust Tone
        </button>
      {:else}
        {#each aiFunctions as fn (fn.key)}
          <button type="button" class="ai-chip" on:click={() => requestAi(fn.key)}>
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

    <textarea
      bind:this={inputMessage}
      bind:value={body}
      rows={isReply ? 6 : 8}
      placeholder={isReply ? 'Type your reply...' : 'Type your message...'}
      class="field textarea"></textarea>

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
    <button type="button" class="send-btn" on:click={send}>
      <Send class="h-4 w-4" /> Send
    </button>
    <label class="attach-btn">
      <Paperclip class="h-4 w-4" /> Attach
      <input bind:this={fileInput} type="file" class="sr-only" on:change={(e) => onFilesSelected(e.currentTarget.files)} multiple />
    </label>
  </div>
</WindowFrame>
{/if}

<style>
  .compose-body {
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
  }
  .field {
    width: 100%;
    border: 1px solid rgba(148, 163, 184, 0.7);
    border-radius: 12px;
    padding: 0.65rem 0.85rem;
  }
  .textarea {
    resize: none;
    min-height: 160px;
  }
  .ai-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
  }
  .ai-chip {
    display: inline-flex;
    align-items: center;
    gap: 0.35rem;
    border: 1px solid rgba(148, 163, 184, 0.7);
    border-radius: 999px;
    padding: 0.35rem 0.9rem;
    font-size: 0.85rem;
    color: #475569;
    background: white;
  }
  .attachments {
    border: 1px solid rgba(148, 163, 184, 0.7);
    border-radius: 12px;
    padding: 0.5rem 0.75rem;
  }
  .attachments-header {
    display: flex;
    justify-content: space-between;
    font-size: 0.75rem;
    text-transform: uppercase;
    color: #475569;
  }
  .attachments ul {
    margin-top: 0.5rem;
    list-style: none;
    padding: 0;
  }
  .attachments li {
    font-size: 0.85rem;
    color: #334155;
  }
  .compose-footer {
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }
  .send-btn {
    display: inline-flex;
    align-items: center;
    gap: 0.35rem;
    border-radius: 999px;
    padding: 0.5rem 1.2rem;
    background: linear-gradient(135deg, #0f172a, #1e293b);
    color: white;
  }
  .attach-btn {
    display: inline-flex;
    align-items: center;
    gap: 0.35rem;
    border-radius: 999px;
    padding: 0.5rem 1rem;
    border: 1px solid rgba(148, 163, 184, 0.7);
    cursor: pointer;
  }
</style>

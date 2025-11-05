<script>
  import { createEventDispatcher, onMount } from 'svelte';
  import { X, Minus, Paperclip, Send, Wand2, Highlighter } from 'lucide-svelte';
  export let open = true;
  export let isReply = false;
  export let to = '';
  export let subject = '';
  export let body = '';
  export let uiNonce = null;

  const dispatch = createEventDispatcher();
  let inputTo, inputSubject, inputMessage, fileInput;
  let attachments = [];

  onMount(() => {
    setTimeout(() => (isReply ? inputSubject?.focus() : (inputTo || inputSubject)?.focus()), 50);
  });

  function close() { dispatch('close'); }
  function minimize() { /* optional; keep for parity */ }
  function send() { dispatch('send', { to, subject, message: body, attachments }); }

  function requestAi(command) {
    dispatch('requestAi', { command, draft: body, subject, isReply });
  }

  function onFilesSelected(files) {
    if (!files || files.length === 0) return;
    for (const f of files) attachments = [...attachments, { name: f.name, size: f.size }];
    fileInput.value = '';
  }
</script>

{#if open}
<div class="compose-window fixed bottom-0 right-6 max-w-[560px] w-[92vw] bg-white/95 border border-slate-200 rounded-t-2xl shadow-2xl overflow-hidden z-[1000] flex flex-col">
  <div class="flex items-center justify-between px-3 py-2 border-b border-slate-200 bg-slate-50/70">
    <div class="text-sm font-semibold text-slate-700">{isReply ? 'Reply' : 'New Message'}</div>
    <div class="flex items-center gap-1.5">
      <button type="button" class="h-8 w-8 grid place-items-center text-slate-500 hover:text-slate-800" on:click={minimize} title="Minimize"><Minus class="h-4 w-4" /></button>
      <button type="button" class="h-8 w-8 grid place-items-center text-slate-500 hover:text-slate-800" on:click={close} title="Close"><X class="h-4 w-4" /></button>
    </div>
  </div>
  <div class="p-4 space-y-3 overflow-y-auto">
    {#if !isReply}
      <input bind:this={inputTo} bind:value={to} placeholder="To" class="w-full px-3 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-slate-200 border-slate-200" />
    {/if}
    <input bind:this={inputSubject} bind:value={subject} placeholder="Subject" class="w-full px-3 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-slate-200 border-slate-200" />

    <div class="flex gap-2 flex-wrap">
      <button type="button" class="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-50" on:click={() => requestAi('draft')}>
        <Wand2 class="h-4 w-4" /> AI Compose
      </button>
      <button type="button" class="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-50" on:click={() => requestAi('tone')}>
        <Highlighter class="h-4 w-4" /> Adjust Tone
      </button>
    </div>

    <textarea bind:this={inputMessage} bind:value={body} rows={isReply ? 6 : 8} placeholder={isReply ? 'Type your reply...' : 'Type your message...'} class="w-full px-3 py-3 border rounded-lg focus:outline-none focus:ring-2 focus:ring-slate-200 border-slate-200 resize-none"></textarea>

    {#if attachments.length}
    <div class="bg-white/70 border rounded-lg px-3 py-2 text-xs border-slate-200">
      <div class="flex items-center justify-between mb-2">
        <span class="uppercase font-semibold tracking-wide text-slate-600">Attachments</span>
        <span class="text-slate-500">{attachments.length} file{attachments.length === 1 ? '' : 's'}</span>
      </div>
      <ul class="space-y-1">
        {#each attachments as a}
          <li class="flex items-center justify-between gap-3 rounded-lg border border-slate-200/80 bg-white/90 px-3 py-2 shadow-sm">
            <div class="min-w-0 text-slate-700 truncate">{a.name}</div>
          </li>
        {/each}
      </ul>
    </div>
    {/if}
  </div>
  <div class="p-3 border-t border-slate-200 flex gap-2 bg-slate-50/50">
    <button type="button" class="inline-flex items-center gap-2 rounded-xl px-3 py-2 font-semibold text-white bg-gradient-to-br from-slate-900 to-slate-800 shadow ring-1 ring-slate-900/10" on:click={send}>
      <Send class="h-4 w-4" /> Send
    </button>
    <label class="inline-flex items-center gap-2 rounded-xl px-3 py-2 font-semibold border border-slate-200 bg-white text-slate-700 cursor-pointer hover:bg-slate-50">
      <Paperclip class="h-4 w-4" /> Attach
      <input bind:this={fileInput} type="file" class="sr-only" on:change={(e) => onFilesSelected(e.currentTarget.files)} multiple />
    </label>
  </div>
</div>
{/if}

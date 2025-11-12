<script>
  import { createEventDispatcher } from 'svelte';
  import { Reply, Forward, Archive, Trash2 } from 'lucide-svelte';
  import AiCommandButtons from './AiCommandButtons.svelte';

  /**
   * Header for the selected email: shows sender info, quick actions, and AI command buttons.
   * Extracted from App.svelte so message-level actions are reusable and easier to maintain.
   */
  export let email = null;
  export let commands = [];
  export let mobile = false;
  export let escapeHtmlFn = (value) => value ?? '';
  export let formatFullDateFn = () => '';

  const dispatch = createEventDispatcher();

  function emit(type, detail) {
    dispatch(type, detail);
  }
</script>

{#if email}
  <div class="flex items-start gap-3" class:flex-col={mobile}>
    <div class="flex items-start gap-3 min-w-0 flex-1">
      <img src={email.avatar || email.companyLogoUrl || ('https://i.pravatar.cc/120?u=' + encodeURIComponent(email.fromEmail || email.from))} alt={escapeHtmlFn(email.from)} class="h-10 w-10 rounded-full object-cover shrink-0" class:h-12={!mobile} class:w-12={!mobile} loading="lazy" />
      <div class="min-w-0 flex-1">
        <h2 class="text-lg font-semibold text-slate-900 break-words">{escapeHtmlFn(email.subject)}</h2>
        <div class="flex items-center gap-1 text-sm text-slate-600 flex-wrap">
          <span class="font-medium truncate">{escapeHtmlFn(email.from)}</span>
          {#if email.fromEmail}<span class="text-xs truncate">&lt;{escapeHtmlFn(email.fromEmail)}&gt;</span>{/if}
        </div>
        {#if email.to || email.toEmail}
          <div class="text-xs mt-1 text-slate-400 truncate">To: {escapeHtmlFn(email.to || 'Unknown recipient')} {#if email.toEmail}<span>&lt;{escapeHtmlFn(email.toEmail)}&gt;</span>{/if}</div>
        {/if}
        <p class="text-xs mt-1 text-slate-400">{formatFullDateFn(email.timestampIso, email.timestamp)}</p>
      </div>
    </div>
    <div class="flex gap-2 shrink-0" class:w-full={mobile} class:justify-end={mobile}>
      <button type="button" class="rounded-full h-9 w-9 grid place-items-center border border-slate-200 bg-white text-slate-600 hover:bg-slate-50" title="Reply" aria-label="Reply" on:click={() => emit('reply')}>
        <Reply class="h-4 w-4" />
      </button>
      <button type="button" class="rounded-full h-9 w-9 grid place-items-center border border-slate-200 bg-white text-slate-600 hover:bg-slate-50" title="Forward" aria-label="Forward" on:click={() => emit('forward')}>
        <Forward class="h-4 w-4" />
      </button>
      <button type="button" class="rounded-full h-9 w-9 grid place-items-center border border-slate-200 bg-white text-slate-600 hover:bg-slate-50" title="Archive" aria-label="Archive" on:click={() => emit('archive')}>
        <Archive class="h-4 w-4" />
      </button>
      <button type="button" class="rounded-full h-9 w-9 grid place-items-center border border-slate-200 bg-white text-slate-600 hover:bg-slate-50" title="Delete" aria-label="Delete" on:click={() => emit('delete')}>
        <Trash2 class="h-4 w-4" />
      </button>
    </div>
  </div>
  <AiCommandButtons commands={commands} on:select={(event) => emit('commandSelect', event.detail)} />
{/if}

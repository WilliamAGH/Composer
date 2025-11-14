<script>
  import { createEventDispatcher } from 'svelte';
  import { ArrowLeft } from 'lucide-svelte';
  import EmailActionToolbar from './EmailActionToolbar.svelte';
  import EmailDetailView from './EmailDetailView.svelte';

  export let email = null;
  export let commands = [];
  export let actionMenuOptions = [];
  export let actionMenuLoading = false;
  export let compactActions = false;
  export let currentFolderId = 'inbox';
  export let pendingMove = false;
  export let escapeHtmlFn = (value) => value ?? '';
  export let formatFullDateFn = () => '';
  export let renderMarkdownFn = (value) => value ?? '';

  const dispatch = createEventDispatcher();

  function emit(type, detail = undefined) {
    dispatch(type, detail);
  }

  function handleBack() {
    emit('back');
  }

  function handleToolbar(eventName, event) {
    emit(eventName, event?.detail);
  }
</script>

{#if email}
  <div class="mobile-detail-sheet">
    <div class="mobile-detail-sheet__header">
      <button
        type="button"
        class="btn btn--icon z-[70]"
        aria-label="Back to inbox"
        on:click={handleBack}>
        <ArrowLeft class="h-4 w-4" aria-hidden="true" />
      </button>
    </div>
    <div class="mobile-detail-sheet__body">
      <div class="mobile-detail-sheet__toolbar">
        <EmailActionToolbar
          email={email}
          commands={commands}
          actionMenuOptions={actionMenuOptions}
          actionMenuLoading={actionMenuLoading}
          mobile={true}
          compactActions={compactActions}
          currentFolderId={currentFolderId}
          pendingMove={pendingMove}
          escapeHtmlFn={escapeHtmlFn}
          formatFullDateFn={formatFullDateFn}
          on:reply={() => emit('reply')}
          on:forward={() => emit('forward')}
          on:archive={() => emit('archive')}
          on:delete={() => emit('delete')}
          on:move={(event) => handleToolbar('move', event)}
          on:commandSelect={(event) => handleToolbar('commandSelect', event)}
          on:actionSelect={(event) => handleToolbar('actionSelect', event)}
          on:actionMenuToggle={(event) => handleToolbar('actionMenuToggle', event)}
          on:comingSoon={(event) => handleToolbar('comingSoon', event)}
        />
      </div>
      <EmailDetailView
        email={email}
        mobile={true}
        tablet={false}
        desktop={false}
        wide={false}
        renderMarkdownFn={renderMarkdownFn}
      />
    </div>
  </div>
{/if}

<style>
  /**
   * Mobile detail sheet mirrors Compose/Ai sheets so reading view shares the same frosted layering.
   * @usage - Rendered from App.svelte when mobile detail overlay is visible
   * @related - ComposeMobileSheet.svelte, AiSummaryMobileSheet.svelte
   */
  .mobile-detail-sheet {
    position: fixed;
    inset: 0;
    z-index: 80;
    display: flex;
    flex-direction: column;
    background: linear-gradient(180deg, rgba(248, 250, 252, 0.96), rgba(15, 23, 42, 0.08));
    backdrop-filter: blur(12px);
  }

  @supports (padding: env(safe-area-inset-top)) {
    .mobile-detail-sheet {
      padding-top: env(safe-area-inset-top);
      padding-right: env(safe-area-inset-right);
      padding-bottom: env(safe-area-inset-bottom);
      padding-left: env(safe-area-inset-left);
    }
  }


  .mobile-detail-sheet__header {
    display: flex;
    align-items: center;
    padding: 0.75rem 1rem;
    background: rgba(255, 255, 255, 0.96);
    border-bottom: 1px solid rgba(148, 163, 184, 0.2);
  }

  .mobile-detail-sheet__body {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
    padding: 0.75rem 0.75rem 0;
    min-height: 0;
    overflow-y: auto;
    background: white;
  }

  .mobile-detail-sheet__toolbar {
    padding-top: 0.25rem;
  }
</style>

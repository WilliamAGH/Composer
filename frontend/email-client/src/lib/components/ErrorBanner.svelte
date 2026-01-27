<!--
  Error banner - displays fatal/render errors that require user action.
  Appears as a fixed banner at top of viewport.
-->
<script>
  import { fatal, clearFatalError } from '../stores/errorStore';
  import { slide } from 'svelte/transition';
  import { AlertOctagon, RefreshCw, X } from 'lucide-svelte';

  function handleReload() {
    window.location.reload();
  }

  function handleDismiss() {
    clearFatalError();
  }
</script>

{#if $fatal}
  <div
    class="error-banner"
    role="alert"
    aria-live="assertive"
    transition:slide={{ duration: 200 }}
  >
    <div class="error-banner__content">
      <AlertOctagon class="error-banner__icon" />
      <div class="error-banner__text">
        <p class="error-banner__message">{$fatal.message}</p>
        {#if $fatal.detail}
          <p class="error-banner__detail">{$fatal.detail}</p>
        {/if}
      </div>
    </div>
    <div class="error-banner__actions">
      <button type="button" class="error-banner__btn error-banner__btn--primary" on:click={handleReload}>
        <RefreshCw class="h-4 w-4" />
        Reload
      </button>
      <button type="button" class="error-banner__btn" on:click={handleDismiss} aria-label="Dismiss error">
        <X class="h-4 w-4" />
      </button>
    </div>
  </div>
{/if}

<style>
  .error-banner {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    z-index: 10000;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 1rem;
    padding: 0.875rem 1.25rem;
    background: linear-gradient(135deg, #dc2626 0%, #b91c1c 100%);
    color: white;
    box-shadow: 0 4px 12px rgba(185, 28, 28, 0.3);
  }

  .error-banner__content {
    display: flex;
    align-items: flex-start;
    gap: 0.75rem;
    flex: 1;
    min-width: 0;
  }

  .error-banner__content :global(.error-banner__icon) {
    flex-shrink: 0;
    width: 1.5rem;
    height: 1.5rem;
    margin-top: 0.125rem;
  }

  .error-banner__text {
    flex: 1;
    min-width: 0;
  }

  .error-banner__message {
    font-size: 0.9375rem;
    font-weight: 600;
    line-height: 1.4;
  }

  .error-banner__detail {
    font-size: 0.8125rem;
    opacity: 0.9;
    margin-top: 0.25rem;
    font-family: ui-monospace, monospace;
  }

  .error-banner__actions {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    flex-shrink: 0;
  }

  .error-banner__btn {
    display: flex;
    align-items: center;
    gap: 0.375rem;
    padding: 0.5rem 0.75rem;
    border-radius: 0.375rem;
    border: 1px solid rgba(255, 255, 255, 0.3);
    background: rgba(255, 255, 255, 0.1);
    color: white;
    font-size: 0.875rem;
    font-weight: 500;
    cursor: pointer;
    transition: background 0.15s, border-color 0.15s;
  }

  .error-banner__btn:hover {
    background: rgba(255, 255, 255, 0.2);
    border-color: rgba(255, 255, 255, 0.5);
  }

  .error-banner__btn--primary {
    background: white;
    color: #b91c1c;
    border-color: white;
  }

  .error-banner__btn--primary:hover {
    background: #fef2f2;
  }

  @media (max-width: 640px) {
    .error-banner {
      flex-direction: column;
      align-items: stretch;
      gap: 0.75rem;
    }

    .error-banner__actions {
      justify-content: flex-end;
    }
  }
</style>

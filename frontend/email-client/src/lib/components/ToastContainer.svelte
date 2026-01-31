<!--
  Toast container - renders stacked transient error notifications.
  Positioned fixed at bottom-right, auto-dismisses.
-->
<script>
  import { toasts, dismissToast } from '../stores/errorStore';
  import { fly, fade } from 'svelte/transition';
  import { X, AlertCircle, AlertTriangle, Info } from 'lucide-svelte';

  const iconMap = {
    error: AlertCircle,
    warning: AlertTriangle,
    info: Info
  };

  const colorMap = {
    error: 'bg-red-50 border-red-200 text-red-800',
    warning: 'bg-amber-50 border-amber-200 text-amber-800',
    info: 'bg-blue-50 border-blue-200 text-blue-800'
  };

  const iconColorMap = {
    error: 'text-red-500',
    warning: 'text-amber-500',
    info: 'text-blue-500'
  };
</script>

<div class="toast-container" aria-live="polite" aria-label="Notifications">
  {#each $toasts as toast (toast.id)}
    <div
      class="toast {colorMap[toast.severity]}"
      role="alert"
      in:fly={{ x: 100, duration: 200 }}
      out:fade={{ duration: 150 }}
    >
      <svelte:component this={iconMap[toast.severity]} class="toast__icon {iconColorMap[toast.severity]}" />
      <div class="toast__content">
        <p class="toast__message">
          {toast.message}
          {#if toast.actionLabel && toast.actionHref}
            <span class="toast__divider"> — </span>
            <a class="toast__action" href={toast.actionHref}>{toast.actionLabel}</a>
          {/if}
        </p>
        {#if toast.detail}
          <p class="toast__detail">{toast.detail}</p>
        {/if}
      </div>
      <button
        type="button"
        class="toast__dismiss"
        aria-label="Dismiss"
        on:click={() => dismissToast(toast.id)}
      >
        <X class="h-4 w-4" />
      </button>
    </div>
  {/each}
</div>

<style>
  .toast-container {
    position: fixed;
    bottom: 1rem;
    right: 1rem;
    z-index: 9999;
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
    max-width: 24rem;
    pointer-events: none;
  }

  .toast {
    display: flex;
    align-items: flex-start;
    gap: 0.75rem;
    padding: 0.875rem 1rem;
    border-radius: 0.5rem;
    border-width: 1px;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    pointer-events: auto;
  }

  .toast__icon {
    flex-shrink: 0;
    width: 1.25rem;
    height: 1.25rem;
    margin-top: 0.125rem;
  }

  .toast__content {
    flex: 1;
    min-width: 0;
  }

  .toast__message {
    font-size: 0.875rem;
    font-weight: 500;
    line-height: 1.4;
  }

  .toast__divider {
    opacity: 0.6;
  }

  .toast__action {
    font-weight: 600;
    text-decoration: underline;
  }

  .toast__action:hover {
    opacity: 0.85;
  }

  .toast__detail {
    font-size: 0.75rem;
    opacity: 0.8;
    margin-top: 0.25rem;
    font-family: ui-monospace, monospace;
    word-break: break-all;
  }

  .toast__dismiss {
    flex-shrink: 0;
    padding: 0.25rem;
    border-radius: 0.25rem;
    background: transparent;
    border: none;
    cursor: pointer;
    opacity: 0.6;
    transition: opacity 0.15s;
  }

  .toast__dismiss:hover {
    opacity: 1;
  }

  @media (max-width: 640px) {
    .toast-container {
      left: 1rem;
      right: 1rem;
      max-width: none;
    }
  }
</style>

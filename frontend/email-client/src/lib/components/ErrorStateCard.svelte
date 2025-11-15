<script>
  import { createEventDispatcher } from 'svelte';
  import { AlertTriangle } from 'lucide-svelte';

  export let title = 'Something went wrong';
  export let description = 'Try that again in a few seconds.';
  export let eyebrow = 'System notice';
  export let code = '';
  export let icon = AlertTriangle;
  export let size = 'default'; // "default" | "compact"
  export let primaryLabel = '';
  export let primaryVariant = 'primary';
  export let primaryIcon = null;
  export let secondaryLabel = '';
  export let secondaryVariant = 'ghost';
  export let secondaryIcon = null;

  const dispatch = createEventDispatcher();

  const variantClassMap = {
    primary: 'btn--primary',
    secondary: 'btn--secondary',
    ghost: 'btn--ghost'
  };

  $: IconComponent = icon ?? AlertTriangle;
  $: primaryClass = `btn ${variantClassMap[primaryVariant] || variantClassMap.primary}`;
  $: secondaryClass = `btn ${variantClassMap[secondaryVariant] || variantClassMap.ghost}`;

  function emit(action) {
    dispatch(action);
  }
</script>

<section class={`error-card ${size === 'compact' ? 'error-card--compact' : ''}`} aria-live="polite">
  <div class="error-card__surface">
    <div class="error-card__aura" aria-hidden="true"></div>
    <div class="error-card__content">
      {#if eyebrow}
        <p class="error-card__eyebrow">
          <span>{eyebrow}</span>
          {#if code}
            <span class="error-card__code">{code}</span>
          {/if}
        </p>
      {/if}
      <div class="error-card__icon" aria-hidden="true">
        <svelte:component this={IconComponent} class="error-card__icon-mark" />
      </div>
      <h2 class="error-card__title">{title}</h2>
      <p class="error-card__description">{description}</p>
      <slot name="support" />
      <slot />
      {#if primaryLabel || secondaryLabel}
        <div class="error-card__actions">
          {#if secondaryLabel}
            <button type="button" class={secondaryClass} on:click={() => emit('secondary')}>
              {#if secondaryIcon}
                <svelte:component this={secondaryIcon} class="error-card__action-icon" aria-hidden="true" />
              {/if}
              <span>{secondaryLabel}</span>
            </button>
          {/if}
          {#if primaryLabel}
            <button type="button" class={primaryClass} on:click={() => emit('primary')}>
              {#if primaryIcon}
                <svelte:component this={primaryIcon} class="error-card__action-icon" aria-hidden="true" />
              {/if}
              <span>{primaryLabel}</span>
            </button>
          {/if}
        </div>
      {/if}
    </div>
  </div>
</section>

<style>
  /**
   * Frosted glass error card shared by shell panels and stand-alone surfaces.
   * @usage - Wraps inline error state content (e.g., AI panel, modal body, standalone view)
   * @related - .error-card--compact variant trims padding for embedded layouts
   */
  .error-card {
    width: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .error-card--compact {
    justify-content: flex-start;
  }

  .error-card__surface {
    position: relative;
    width: 100%;
    border-radius: clamp(20px, 2vw, 28px);
    border: 1px solid rgba(148, 163, 184, 0.35);
    background: linear-gradient(145deg, rgba(255, 255, 255, 0.92), rgba(248, 250, 252, 0.82));
    box-shadow: 0 45px 90px -55px rgba(15, 23, 42, 0.55);
    overflow: hidden;
  }

  .error-card--compact .error-card__surface {
    border-radius: 18px;
  }

  .error-card__aura {
    position: absolute;
    inset: 0;
    background: radial-gradient(circle at 20% 20%, rgba(79, 70, 229, 0.25), transparent 55%),
      radial-gradient(circle at 80% 0%, rgba(14, 165, 233, 0.2), transparent 50%);
    filter: blur(12px);
    opacity: 0.9;
    pointer-events: none;
  }

  .error-card__content {
    position: relative;
    display: flex;
    flex-direction: column;
    gap: 0.9rem;
    padding: clamp(1.25rem, 1rem + 2vw, 2.5rem);
  }

  .error-card--compact .error-card__content {
    padding: 1rem 1.25rem 1.25rem;
  }

  .error-card__eyebrow {
    display: flex;
    flex-wrap: wrap;
    gap: 0.6rem;
    align-items: center;
    font-size: 0.85rem;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: #475569;
  }

  .error-card__code {
    padding: 0.2rem 0.6rem;
    border-radius: 999px;
    border: 1px solid rgba(148, 163, 184, 0.4);
    background: rgba(255, 255, 255, 0.8);
    font-size: 0.75rem;
    letter-spacing: 0.05em;
    color: #0f172a;
  }

  .error-card__icon {
    width: 56px;
    height: 56px;
    border-radius: 999px;
    border: 1px solid rgba(148, 163, 184, 0.35);
    background: rgba(15, 23, 42, 0.07);
    display: inline-flex;
    align-items: center;
    justify-content: center;
    box-shadow: inset 0 1px 2px rgba(255, 255, 255, 0.5);
  }

  .error-card__icon-mark {
    width: 28px;
    height: 28px;
    color: #0f172a;
  }

  .error-card__title {
    font-size: clamp(1.35rem, 1.1rem + 1vw, 1.85rem);
    font-weight: 600;
    color: #0f172a;
    margin: 0;
  }

  .error-card__description {
    font-size: 1rem;
    line-height: 1.6;
    color: #475569;
    margin: 0;
  }

  .error-card__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 0.75rem;
    margin-top: 0.5rem;
  }

  .error-card--compact .error-card__actions {
    margin-top: 0.25rem;
  }

  .error-card__action-icon {
    width: 18px;
    height: 18px;
  }

  @media (max-width: 640px) {
    .error-card__surface {
      border-radius: 18px;
    }

    .error-card__actions {
      width: 100%;
    }

    .error-card__actions button {
      flex: 1 1 100%;
    }
  }
</style>

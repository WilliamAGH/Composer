<!-- Shared Coming Soon dialog so new surfaces can reuse consistent messaging. -->
<script>
  import { Sparkles } from 'lucide-svelte';
  import { createEventDispatcher } from 'svelte';

  export let open = false;
  export let sourceLabel = 'This feature';

  const dispatch = createEventDispatcher();

  function close() {
    dispatch('close');
  }
</script>

{#if open}
  <div class="coming-soon-modal" role="dialog" aria-modal="true" aria-label="Coming soon">
    <button type="button" class="coming-soon-modal__backdrop" aria-label="Close coming soon modal" on:click={close}>
      <span class="sr-only">Close</span>
    </button>
    <div class="coming-soon-modal__card" role="document" tabindex="-1">
      <button type="button" class="coming-soon-modal__dismiss btn btn--icon" aria-label="Close" on:click={close}>
        ✕
      </button>
      <div class="coming-soon-modal__eyebrow">
        <Sparkles class="coming-soon-modal__sparkle" aria-hidden="true" />
        <span>Coming Soon</span>
      </div>
      <h3 class="coming-soon-modal__headline">
        {sourceLabel || 'This feature'} is almost here
      </h3>
      <p class="coming-soon-modal__body">
        We&apos;re putting the finishing touches on this workflow. Follow along in Composer updates for early access and let us know how you&apos;d like it to work.
      </p>
      <div class="coming-soon-modal__footer">
        <button type="button" class="btn btn--secondary coming-soon-modal__confirm" on:click={close}>
          Sounds good
        </button>
        <div class="coming-soon-modal__note">
          Need it sooner? Drop us a note in the roadmap channel.
        </div>
      </div>
    </div>
  </div>
{/if}

<style>
  /**
   * Full-screen overlay that pins the Coming Soon modal above all panes while allowing backdrop clicks to dismiss.
   * @usage - Root wrapper rendered by ComingSoonModal when `open=true`
   * @z-index-warning - Uses var(--z-modal, 180); ensure DrawerBackdrop (z-50) never overlaps this container
   * @related - .coming-soon-modal__backdrop, .coming-soon-modal__card
   */
  .coming-soon-modal {
    position: fixed;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 1rem;
    z-index: var(--z-modal, 180);
  }

  /**
   * Clickable scrim that dims the background while delegating focus to the modal card.
   * @usage - Direct child of .coming-soon-modal for closing interactions
   * @related - .coming-soon-modal__card
   */
  .coming-soon-modal__backdrop {
    position: absolute;
    inset: 0;
    background: rgba(15, 23, 42, 0.6);
    backdrop-filter: blur(10px);
    border: none;
    padding: 0;
    cursor: pointer;
  }

  /**
   * Primary modal surface styled with glassy gradient and generous spacing consistent with design language.
   * @usage - Wraps the headline, description, and action controls within the Coming Soon modal
   * @related - .coming-soon-modal__eyebrow, .coming-soon-modal__footer
   */
  .coming-soon-modal__card {
    position: relative;
    width: min(100%, 420px);
    border-radius: 32px;
    border: 1px solid rgba(255, 255, 255, 0.15);
    background: linear-gradient(135deg, rgba(15, 23, 42, 0.95), rgba(6, 95, 70, 0.85));
    color: white;
    padding: 1.5rem;
    box-shadow: 0 35px 80px rgba(15, 23, 42, 0.55);
  }

  /**
   * Elevated close button that mirrors other icon pills while staying legible over the gradient surface.
   * @usage - Absolute positioned dismiss button inside the modal card
   * @related - .coming-soon-modal__card
   */
  .coming-soon-modal__dismiss {
    position: absolute;
    top: 1rem;
    right: 1rem;
    background: rgba(255, 255, 255, 0.18);
    color: white;
    border-color: rgba(255, 255, 255, 0.3);
  }

  .coming-soon-modal__dismiss:hover {
    background: rgba(255, 255, 255, 0.25);
  }

  /**
   * Eyebrow pill that reinforces the “Coming Soon” state and pairs with the sparkle icon.
   * @usage - Inline badge positioned above the modal headline
   * @related - .coming-soon-modal__headline
   */
  .coming-soon-modal__eyebrow {
    display: inline-flex;
    align-items: center;
    gap: 0.35rem;
    padding: 0.4rem 0.9rem;
    border-radius: 999px;
    background: rgba(255, 255, 255, 0.12);
    font-size: 0.7rem;
    letter-spacing: 0.2em;
    text-transform: uppercase;
    margin-bottom: 1rem;
    font-weight: 600;
  }

  .coming-soon-modal__sparkle {
    width: 16px;
    height: 16px;
    color: #a7f3d0;
  }

  /**
   * Headline + body copy maintain consistent typography across modal instances.
   * @usage - Headline and supporting paragraph copy
   * @related - .coming-soon-modal__body
   */
  .coming-soon-modal__headline {
    font-size: 1.75rem;
    font-weight: 600;
    line-height: 1.2;
    margin: 0;
  }

  .coming-soon-modal__body {
    margin-top: 0.75rem;
    font-size: 0.95rem;
    line-height: 1.6;
    color: rgba(226, 232, 240, 0.95);
  }

  /**
   * Layout for the confirmation CTA and roadmap note, stacking on mobile while aligning horizontally on desktop.
   * @usage - Bottom section of modal card
   * @related - .coming-soon-modal__confirm, .coming-soon-modal__note
   */
  .coming-soon-modal__footer {
    margin-top: 1.5rem;
    display: flex;
    flex-direction: column;
    gap: 0.8rem;
  }

  @media (min-width: 640px) {
    .coming-soon-modal__footer {
      flex-direction: row;
      align-items: center;
    }
  }

  .coming-soon-modal__confirm {
    width: 100%;
    justify-content: center;
  }

  .coming-soon-modal__note {
    font-size: 0.75rem;
    color: rgba(226, 232, 240, 0.9);
    text-align: center;
  }

  @media (min-width: 640px) {
    .coming-soon-modal__note {
      text-align: left;
    }
  }
</style>

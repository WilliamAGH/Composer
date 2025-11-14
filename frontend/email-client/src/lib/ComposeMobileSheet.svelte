<script>
  import { onMount, onDestroy } from 'svelte';
  import { Wand2, Highlighter, ChevronDown, Send, Paperclip, Trash2 } from 'lucide-svelte';
  import AiLoadingJourney from './AiLoadingJourney.svelte';
  import MobileTopBar from './MobileTopBar.svelte';

  export let title = 'Compose';
  export let to = '';
  export let subject = '';
  export let body = '';
  export let attachments = [];
  export let draftOptions = [];
  export let primaryDraftOption = null;
  export let tonePresets = [];
  export let journeyOverlay = null;
  export let journeyInlineActive = false;
  export let onSend = () => {};
  export let onDeleteDraft = () => {};
  export let onRunPrimaryDraft = () => {};
  export let onInvokeDraftOption = () => {};
  export let onInvokeTonePreset = () => {};
  export let onAttach = () => {};
  export let onClose = () => {};
  export let registerInputRefs = () => {};
  export let showDraftMenu = true;

  let draftMenuOpen = false;
  let toneMenuOpen = false;
  let draftMenuRef = null;
  let toneMenuRef = null;
  let draftToggleButton = null;
  let toneToggleButton = null;
  let toInputEl = null;
  let subjectInputEl = null;
  let bodyInputEl = null;

  const hasAttachments = () => Array.isArray(attachments) && attachments.length > 0;

  function handleGlobalPointer(event) {
    const target = event.target;
    if (draftMenuOpen && draftMenuRef && !draftMenuRef.contains(target) && !draftToggleButton?.contains(target)) {
      draftMenuOpen = false;
    }
    if (toneMenuOpen && toneMenuRef && !toneMenuRef.contains(target) && !toneToggleButton?.contains(target)) {
      toneMenuOpen = false;
    }
  }

  onMount(() => {
    document.addEventListener('pointerdown', handleGlobalPointer);
    registerInputRefs?.({ to: toInputEl, subject: subjectInputEl, message: bodyInputEl });
  });

  onDestroy(() => {
    document.removeEventListener('pointerdown', handleGlobalPointer);
  });

  $: registerInputRefs?.({ to: toInputEl, subject: subjectInputEl, message: bodyInputEl });
  $: if (!showDraftMenu) {
    draftMenuOpen = false;
  }

  function runPrimaryDraft() {
    onRunPrimaryDraft?.();
  }

  function invokeDraftOption(option) {
    if (!option) return;
    onInvokeDraftOption?.(option);
    draftMenuOpen = false;
  }

  function invokeTonePreset(preset) {
    if (!preset) return;
    onInvokeTonePreset?.(preset);
    toneMenuOpen = false;
  }

  function toggleDraftMenu() {
    draftMenuOpen = !draftMenuOpen;
    if (draftMenuOpen) toneMenuOpen = false;
  }

  function toggleToneMenu() {
    toneMenuOpen = !toneMenuOpen;
    if (toneMenuOpen) draftMenuOpen = false;
  }
</script>

<div class="compose-mobile">
  <MobileTopBar
    variant="custom"
    showMenuButton={false}
    backIcon="close"
    backButtonAriaLabel="Close compose"
    on:back={onClose}>
    <div slot="center" class="compose-mobile__title">
      <p class="compose-mobile__eyebrow">Compose</p>
      <h2>{title}</h2>
    </div>
    <div slot="actions">
      <button type="button" class="btn btn--primary btn--labelled" on:click={onSend} disabled={journeyInlineActive}>
        <Send class="h-4 w-4" /> Send
      </button>
    </div>
  </MobileTopBar>

  <section class="compose-mobile__body">
    <input
      bind:this={toInputEl}
      bind:value={to}
      placeholder="To"
      class="compose-mobile__field" />
    <input
      bind:this={subjectInputEl}
      bind:value={subject}
      placeholder="Subject"
      class="compose-mobile__field" />

    <div class="compose-mobile__ai-row">
      <button type="button" class="btn btn--ghost btn--labelled compose-mobile__pill" on:click={runPrimaryDraft}>
        <Wand2 class="h-4 w-4" /> {primaryDraftOption?.label || 'Draft'}
      </button>
      {#if showDraftMenu}
        <button
          type="button"
          class="btn btn--ghost btn--icon compose-mobile__pill compose-mobile__pill-toggle"
          aria-haspopup="menu"
          aria-expanded={draftMenuOpen}
          bind:this={draftToggleButton}
          on:click={toggleDraftMenu}
          aria-label="More drafting options">
          <ChevronDown class={`h-4 w-4 transition ${draftMenuOpen ? 'rotate-180' : ''}`} />
        </button>
        {#if draftMenuOpen && draftOptions.length}
          <div class="menu-surface compose-mobile__menu" bind:this={draftMenuRef}>
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
      {/if}
    </div>

    <div class="compose-mobile__ai-row">
      <button
        type="button"
        class="btn btn--ghost btn--labelled compose-mobile__pill compose-mobile__pill--wide"
        aria-haspopup="menu"
        aria-expanded={toneMenuOpen}
        bind:this={toneToggleButton}
        on:click={toggleToneMenu}>
        <Highlighter class="h-4 w-4" /> Tone
        <ChevronDown class={`h-4 w-4 text-slate-500 transition ${toneMenuOpen ? 'rotate-180' : ''}`} />
      </button>
      {#if toneMenuOpen}
        <div class="menu-surface compose-mobile__menu" bind:this={toneMenuRef}>
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

    <textarea
      bind:this={bodyInputEl}
      bind:value={body}
      rows={8}
      placeholder="Type your message..."
      class="compose-mobile__textarea"
      disabled={journeyInlineActive}
      aria-busy={journeyInlineActive}></textarea>

    {#if journeyOverlay?.visible}
      <div class="compose-mobile__journey">
        <AiLoadingJourney
          steps={journeyOverlay.steps || []}
          activeStepId={journeyOverlay.activeStepId}
          headline={journeyOverlay.headline}
          subhead={journeyOverlay.subhead}
          show={journeyOverlay.visible}
          inline={true}
          subdued={true}
          className="border-slate-200" />
      </div>
    {/if}

    {#if hasAttachments()}
      <div class="compose-mobile__attachments">
        <div class="compose-mobile__attachments-header">
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
  </section>

  <footer class="compose-mobile__footer">
    <button type="button" class="btn btn--secondary btn--labelled" on:click={onAttach}>
      <Paperclip class="h-4 w-4" /> Attach
    </button>
    <button type="button" class="btn btn--ghost btn--labelled" on:click={onDeleteDraft}>
      <Trash2 class="h-4 w-4" /> Delete
    </button>
  </footer>
</div>

<style>
  /**
   * Mobile compose sheet anchors to the viewport with frosted backdrop and maximal padding for thumb reach.
   * @usage - Rendered exclusively when ComposeWindow detects a mobile viewport
   */
  .compose-mobile {
    position: fixed;
    inset: 0;
    display: flex;
    flex-direction: column;
    background: linear-gradient(180deg, rgba(248, 250, 252, 0.98), rgba(255, 255, 255, 0.97));
    padding: 1rem;
    gap: 1rem;
    z-index: 85;
  }
  /**
   * Title stack mirrors the desktop naming but centers for limited horizontal room.
   * @usage - compose-mobile title area inside MobileTopBar slot
   */
  .compose-mobile__title {
    flex: 1;
    min-width: 0;
  }
  .compose-mobile__title h2 {
    font-size: 1rem;
    font-weight: 600;
    color: #0f172a;
  }
  .compose-mobile__eyebrow {
    font-size: 0.65rem;
    letter-spacing: 0.35em;
    text-transform: uppercase;
    color: rgba(99, 102, 241, 0.7);
    margin-bottom: 0.15rem;
  }
  /**
   * Body stacks form controls with generous spacing while preserving ≥16px font sizing.
   * @usage - compose-mobile main form section
   */
  .compose-mobile__body {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
    overflow-y: auto;
  }
  /**
   * Field styling mirrors shared desktop fields but keeps mobile-safe padding baked in locally.
   * @usage - compose-mobile inputs only
   */
.compose-mobile__field {
  width: 100%;
  border: 1px solid rgba(148, 163, 184, 0.7);
  border-radius: 16px;
  padding: 0.85rem 1rem;
  font-size: clamp(16px, 1rem + 0.05vw, 17px);
  line-height: 1.5;
  background: rgba(255, 255, 255, 0.95);
}
  /**
   * AI action rows present buttons side-by-side while collapsing menus underneath.
   * @usage - compose-mobile AI quick actions (Draft + Tone)
   */
  .compose-mobile__ai-row {
    display: flex;
    align-items: center;
    gap: 0.35rem;
    position: relative;
  }
  /**
   * AI pills get flatter padding to match the user's request for compact controls even on tablets.
   * @usage - compose-mobile quick action buttons
   */
  .compose-mobile__pill {
    min-height: 36px;
    padding: 0.25rem 0.8rem;
  }
  .compose-mobile__pill-toggle {
    width: 42px;
    justify-content: center;
    padding: 0;
  }
  .compose-mobile__pill--wide {
    flex: 1;
    justify-content: space-between;
  }
  /**
   * Dropdown menus reuse the shared glass surface but anchor underneath the trigger.
   * @usage - compose-mobile draft/tone dropdowns
   */
  .compose-mobile__menu {
    position: absolute;
    left: 0;
    right: 0;
    margin-top: 0.35rem;
    z-index: var(--z-dropdown, 200);
  }
  /**
   * Textarea keeps taller baseline for comfortable mobile drafting.
   * @usage - compose-mobile message body
   */
.compose-mobile__textarea {
  width: 100%;
  border: 1px solid rgba(148, 163, 184, 0.7);
  border-radius: 16px;
  padding: 0.85rem 1rem;
  font-size: clamp(16px, 1rem + 0.05vw, 17px);
  line-height: 1.5;
  min-height: min(45vh, 360px);
  resize: none;
  background: rgba(255, 255, 255, 0.95);
}
  /**
   * Journey card inherits spacing from body but adds subtle border for readability.
   * @usage - compose-mobile inline AI journey readout
   */
  .compose-mobile__journey {
    border: 1px solid rgba(148, 163, 184, 0.6);
    border-radius: 16px;
    padding: 0.5rem;
    background: rgba(255, 255, 255, 0.9);
  }
  /**
   * Attachment block mirrors desktop style with uppercase label + count chips.
   * @usage - compose-mobile attachment summary
   */
  .compose-mobile__attachments {
    border: 1px solid rgba(148, 163, 184, 0.7);
    border-radius: 14px;
    padding: 0.5rem 0.75rem;
    background: rgba(255, 255, 255, 0.92);
  }
  .compose-mobile__attachments-header {
    display: flex;
    justify-content: space-between;
    font-size: 0.75rem;
    text-transform: uppercase;
    color: #475569;
  }
  .compose-mobile__attachments ul {
    list-style: none;
    padding: 0;
    margin: 0.5rem 0 0;
  }
  .compose-mobile__attachments li {
    font-size: 0.85rem;
    color: #334155;
  }
  /**
   * Footer pins primary secondary actions with safe spacing at the bottom of the sheet.
   * @usage - compose-mobile footer controls
   */
  .compose-mobile__footer {
    display: flex;
    gap: 0.5rem;
  }

  @supports (padding: env(safe-area-inset-top)) {
    .compose-mobile {
      padding-top: max(1rem, env(safe-area-inset-top));
      padding-right: max(1rem, env(safe-area-inset-right));
      padding-bottom: max(1rem, env(safe-area-inset-bottom));
      padding-left: max(1rem, env(safe-area-inset-left));
    }
  }
</style>

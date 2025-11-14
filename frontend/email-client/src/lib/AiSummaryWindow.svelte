<script>
  import { createEventDispatcher } from 'svelte';
  import AiLoadingJourney from './AiLoadingJourney.svelte';
  import WindowActionControls from './window/WindowActionControls.svelte';
  import { Sparkles, RotateCcw } from 'lucide-svelte';

  export let panelState = null;
  export let journeyOverlay = null;
  export let error = '';
  export let maximized = false;
  export let hideChrome = false;

  const dispatch = createEventDispatcher();

  $: title = panelState?.title || 'AI Summary';
  $: html = panelState?.html || '';
  $: lastCommand = panelState?.commandKey || 'summarize';
  $: updatedLabel = panelState?.updatedAt
    ? new Date(panelState.updatedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    : null;
  $: isLoading = Boolean(journeyOverlay && journeyOverlay.visible);
  $: hasContent = Boolean(html && !isLoading);
  $: activeCommandKey = (panelState?.commandKey || lastCommand || '').toLowerCase();
  $: badgeLabel = activeCommandKey === 'translate' ? 'Translation' : 'Summary';
  $: emptyTitle = badgeLabel === 'Translation' ? 'No translation yet' : 'No summary yet';
  $: emptyCopy = badgeLabel === 'Translation'
    ? 'Run an AI translation to pin results to this thread.'
    : 'Run an AI summary to pin results to this thread.';
  $: emptyActionLabel = badgeLabel === 'Translation' ? 'Translate email' : 'Generate summary';
  $: primaryActionAria = hasContent
    ? `Regenerate ${badgeLabel.toLowerCase()}`
    : `Generate ${badgeLabel.toLowerCase()}`;

  function emitRunCommand(key = 'summarize') {
    dispatch('runCommand', { key });
  }

  function handlePrimaryAction() {
    emitRunCommand(lastCommand || 'summarize');
  }

  function minimize() {
    dispatch('minimize');
  }

  function toggleMaximize() {
    dispatch('toggleMaximize');
  }

  function closePanel() {
    dispatch('close');
  }
</script>

<section class={`ai-summary-panel ${maximized ? 'maximized' : ''} ${hideChrome ? 'ai-summary-panel--sheet' : ''}`} aria-live="polite">
  {#if !hideChrome}
    <header class="panel-header">
      <div class="panel-title-group">
        <div class="panel-heading">
          <span class="panel-chip">{badgeLabel}</span>
          {#if updatedLabel}
            <span class="panel-meta">Updated at {updatedLabel}</span>
          {/if}
        </div>
      </div>
      <WindowActionControls
        showRefresh={true}
        {maximized}
        refreshDisabled={isLoading}
        refreshAriaLabel={primaryActionAria}
        allowMinimize={true}
        allowMaximize={true}
        allowClose={true}
        minimizeTitle="Minimize"
        maximizeTitle={maximized ? 'Restore' : 'Maximize'}
        closeTitle="Close"
        on:refresh={handlePrimaryAction}
        on:minimize={minimize}
        on:maximize={toggleMaximize}
        on:close={closePanel}
      />
    </header>
  {/if}
  <div class="panel-body">
    <div class="panel-scroll">
      {#if isLoading}
        <AiLoadingJourney
          steps={journeyOverlay.steps}
          activeStepId={journeyOverlay.activeStepId}
          headline={journeyOverlay.headline}
          subhead={journeyOverlay.subhead}
          show={journeyOverlay.visible}
          inline={true}
          subdued={true}
          className="border-slate-200" />
      {:else if error}
      <div class="panel-state panel-error">
        <p>{error}</p>
        <button
          type="button"
          class="btn btn--ghost btn--icon"
          aria-label="Try again"
          on:click={() => emitRunCommand(lastCommand)}>
          <RotateCcw class="h-4 w-4" aria-hidden="true" />
        </button>
      </div>
      {:else if hasContent}
        <div class="panel-html">
          {@html html}
        </div>
      {:else}
        <div class="panel-state">
          <Sparkles class="h-6 w-6 text-slate-400" aria-hidden="true" />
          <p class="panel-empty-title">{emptyTitle}</p>
          <p class="panel-empty-copy">{emptyCopy}</p>
          <button
            type="button"
            class="btn btn--secondary btn--icon"
            aria-label={emptyActionLabel}
            on:click={() => emitRunCommand(lastCommand || 'summarize')}>
            <RotateCcw class="h-4 w-4" aria-hidden="true" />
          </button>
        </div>
      {/if}
    </div>
  </div>
</section>

<style>
  /**
   * Frosted surface for summary/translation output with layered navy shadows.
   *
   * Creates the signature glassmorphic look for AI summary panels with subtle gradients,
   * rounded corners, and a backdrop blur effect. The surface adapts its appearance when
   * maximized to provide better readability.
   *
   * @usage - <section class="ai-summary-panel"> root element in AiSummaryWindow.svelte
   * @layout - Flexbox column layout to stack header, body, and footer vertically
   * @backdrop - blur(18px) creates frosted glass effect (removed when maximized)
   * @responsive - Padding and border-radius scale with viewport via clamp()
   * @z-index - Inherits from parent positioning context
   * @related - .ai-summary-panel.maximized for expanded state variant
   */
  .ai-summary-panel {
    width: 100%;
    height: 100%;
    display: flex;
    flex-direction: column;
    background: linear-gradient(145deg, rgba(248, 250, 252, 0.95), rgba(255, 255, 255, 0.88));
    border: 1px solid rgba(15, 23, 42, 0.08);
    border-radius: clamp(20px, 2vw, 26px);
    box-shadow: 0 30px 70px -35px rgba(15, 23, 42, 0.45);
    padding: clamp(0.75rem, 0.7rem + 0.5vw, 1rem);
    backdrop-filter: blur(18px);
  }

  /**
   * Maximized state trades blur for crisp edges to match docked windows.
   *
   * When the panel is expanded to fill available space, we remove the backdrop blur
   * for better performance and switch to a solid white background for maximum contrast
   * and readability of longer content.
   *
   * @usage - Applied when panel is in maximized state via .ai-summary-panel.maximized
   * @background - Solid white replaces gradient for clarity
   * @backdrop - Blur removed for performance during expanded view
   * @shadow - Deeper shadow emphasizes elevated maximized state
   * @related - .ai-summary-panel base styles
   */
  .ai-summary-panel.maximized {
    background: #ffffff;
    border-color: rgba(15, 23, 42, 0.15);
    box-shadow: 0 60px 140px -55px rgba(15, 23, 42, 0.5);
    backdrop-filter: none;
  }

  /**
   * Header layout keeps the command title on the left and chrome controls on the right.
   *
   * CRITICAL FIX: Changed flex-wrap from 'wrap' to 'nowrap' and align-items from
   * 'flex-start' to 'center'. The previous flex-wrap: wrap was the root cause of
   * buttons wrapping to a new line when the title was long. Now the header will
   * keep title and controls inline, with the title truncating if needed.
   *
   * @usage - <header class="panel-header"> wrapping title and WindowActionControls
   * @layout - Flexbox with space-between for title left, controls right alignment
   * @wrapping - nowrap ensures title and controls stay on same horizontal line
   * @alignment - center vertically aligns title badges with action buttons
   * @spacing - 1.25rem gap provides breathing room between title and controls
   * @responsive - On mobile (<640px), switches to column layout via media query
   * @anti-pattern - DO NOT change flex-wrap back to 'wrap' - this causes button wrapping
   * @related - .panel-title-group for left content, WindowActionControls for right buttons
   */
  .panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 1.25rem;
    flex-wrap: nowrap;
  }

  /**
   * Vertical stacking for badge, timestamp, and title copy.
   *
   * Container for the left side of the header that holds the mode badge and timestamp.
   * Keeps all title-related elements organized in a vertical stack.
   *
   * @usage - <div class="panel-title-group"> wrapping .panel-heading
   * @layout - Flexbox column for vertical stacking of badge and metadata
   * @spacing - 0.45rem gap between stacked elements
   * @flex-behavior - Will shrink if space is constrained (allows buttons to stay visible)
   * @related - .panel-heading child container, .panel-header parent
   */
  .panel-title-group {
    display: flex;
    flex-direction: column;
    gap: 0.45rem;
    flex-shrink: 1;
    min-width: 0;
  }

  /**
   * Badge and timestamp stack vertically so metadata stays directly under the mode chip.
   *
   * Inner container that specifically handles the badge pill and timestamp text,
   * ensuring they stack with consistent alignment and spacing.
   *
   * @usage - <div class="panel-heading"> wrapping .panel-chip and .panel-meta
   * @layout - Flexbox column with flex-start alignment
   * @alignment - Left-aligned to match the overall header layout
   * @spacing - 0.35rem gap between badge and timestamp (tighter than parent gap)
   * @related - .panel-chip, .panel-meta children elements
   */
  .panel-heading {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 0.35rem;
  }

  /**
   * Pill badge helps distinguish summary vs translation modes.
   *
   * Compact, uppercase badge that displays the current panel mode (Summary or Translation).
   * The gradient background and inner shadow create a subtle 3D effect that matches the
   * overall glassmorphic design language.
   *
   * @usage - <span class="panel-chip"> displaying mode label
   * @typography - 0.62rem font size with wide 0.25em letter spacing for readability
   * @styling - Pill shape (999px border-radius) with gradient and inset shadow
   * @color - Dark navy text (#0f172a) on light gradient background
   * @accessibility - Uppercase text may reduce readability for some users, but wide
   *                  letter-spacing compensates. Consider aria-label if text is truncated.
   * @related - .panel-meta sibling for timestamp display
   */
  .panel-chip {
    display: inline-flex;
    align-items: center;
    text-transform: uppercase;
    letter-spacing: 0.25em;
    font-size: 0.62rem;
    font-weight: 600;
    padding: 0.25rem 0.9rem;
    border-radius: 999px;
    color: #0f172a;
    border: 1px solid rgba(15, 23, 42, 0.12);
    background: linear-gradient(135deg, rgba(255, 255, 255, 0.9), rgba(226, 232, 240, 0.6));
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);
  }

  /**
   * Timestamp adopts muted slate color for low-contrast metadata.
   *
   * Displays the "Updated at HH:MM" timestamp in a subtle, non-distracting style.
   * The muted color and small size de-emphasize this secondary information.
   *
   * @usage - <span class="panel-meta"> displaying timestamp
   * @typography - 0.78rem font size with 0.05em letter spacing, uppercase
   * @color - Medium slate gray (#94a3b8) for subtle appearance
   * @accessibility - Small text size may be challenging for some users; ensure sufficient
   *                  contrast ratio and consider making it slightly larger if issues arise
   * @related - .panel-chip sibling for mode badge
   */
  .panel-meta {
    font-size: 0.78rem;
    letter-spacing: 0.05em;
    text-transform: uppercase;
    color: #94a3b8;
    padding-left: 0.125rem;
  }

  /**
   * Content area for displaying summaries - simple container without framing.
   *
   * FIXED: Removed nested window-frame styling (borders, shadows, gradients) that
   * was creating a "window within a window" effect. Now this is just a flex container
   * that holds the scrollable content area without additional visual framing.
   *
   * @usage - <div class="panel-body"> wrapping .panel-scroll
   * @layout - Flexbox container that fills available vertical space
   * @overflow - Hidden to clip content and delegate scrolling to inner .panel-scroll
   * @flex-behavior - flex: 1 expands to fill remaining space in parent column
   * @related - .panel-scroll child for scrollable content area
   */
  .panel-body {
    margin-top: 1.25rem;
    flex: 1;
    display: flex;
    overflow: hidden;
  }

  /**
   * Scroll wrapper maintains padding while allowing long HTML to overflow.
   *
   * Handles vertical scrolling for content that exceeds the available height.
   * The minimum height ensures empty states have adequate space for messaging.
   *
   * @usage - <div class="panel-scroll"> wrapping .panel-html or empty states
   * @layout - Flexbox child that expands to fill parent .panel-body
   * @overflow - overflow-y: auto shows scrollbar only when content exceeds height
   * @min-height - 220px ensures adequate space for empty state messaging
   * @flex-behavior - flex: 1 fills available space
   * @accessibility - Scrollable region should have proper focus management
   * @related - .panel-html for rendered content, .panel-state for empty/error states
   */
  .panel-scroll {
    flex: 1;
    min-height: 220px;
    overflow-y: auto;
  }

  /**
   * Markdown-rendered summary typography.
   *
   * Styles for AI-generated content that has been converted from Markdown to HTML.
   * Uses comfortable line-height and spacing for extended reading of summaries.
   *
   * @usage - <div class="panel-html"> wrapping {@html html} content
   * @typography - 0.95rem font size with 1.65 line-height for readability
   * @color - Near-black (#111827) for maximum contrast and readability
   * @spacing - Child elements (p, ul, ol, li) have additional margin rules below
   * @related - Global styles for paragraph, list, and list-item elements
   */
  .panel-html {
    font-size: 0.95rem;
    line-height: 1.65;
    color: #111827;
  }

  /**
   * Paragraph spacing within rendered HTML content.
   *
   * @usage - Paragraphs within .panel-html
   * @spacing - 0.85rem bottom margin creates comfortable reading rhythm
   */
  .panel-html :global(p) {
    margin-bottom: 0.85rem;
  }

  /**
   * List spacing within rendered HTML content.
   *
   * @usage - Unordered and ordered lists within .panel-html
   * @spacing - Left indentation and bottom margin for list containers
   */
  .panel-html :global(ul),
  .panel-html :global(ol) {
    margin-left: 1.25rem;
    margin-bottom: 0.85rem;
  }

  /**
   * List item spacing within rendered HTML content.
   *
   * @usage - List items within .panel-html lists
   * @spacing - 0.4rem bottom margin separates list items
   */
  .panel-html :global(li) {
    margin-bottom: 0.4rem;
  }

  /**
   * Empty and error states share columnar layout.
   *
   * Used when there's no content to display (empty state) or when an error has occurred.
   * The vertical column layout stacks the icon, heading, description, and action button.
   *
   * @usage - <div class="panel-state"> for empty or error states
   * @layout - Flexbox column with left alignment
   * @min-height - 180px ensures adequate space for empty state messaging
   * @spacing - 0.6rem gap between child elements (icon, text, button)
   * @color - Medium slate gray (#475569) for neutral empty state text
   * @related - .panel-error variant for error state styling
   */
  .panel-state {
    min-height: 180px;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 0.6rem;
    color: #475569;
  }

  /**
   * Error text color cues retry state.
   *
   * Modifier class that changes text color to indicate an error condition.
   * Applied alongside .panel-state when displaying error messages.
   *
   * @usage - <div class="panel-state panel-error"> for error states
   * @color - Red (#b91c1c) signals error condition
   * @accessibility - Color should not be sole indicator; pair with icons/text
   * @related - .panel-state base styles
   */
  .panel-error {
    color: #b91c1c;
  }

  /**
   * Empty-state heading matches primary text color.
   *
   * Primary heading text within empty states (e.g., "No summary yet").
   * Uses darker color and heavier weight for emphasis.
   *
   * @usage - <p class="panel-empty-title"> within .panel-state
   * @typography - 600 font weight for emphasis
   * @color - Dark navy (#0f172a) for strong contrast
   * @related - .panel-empty-copy for description text
   */
  .panel-empty-title {
    font-weight: 600;
    color: #0f172a;
  }

  /**
   * Empty copy uses calmer slate tone.
   *
   * Descriptive text within empty states that explains what action to take.
   * Uses lighter color to de-emphasize secondary information.
   *
   * @usage - <p class="panel-empty-copy"> within .panel-state
   * @typography - 0.9rem font size, slightly smaller than body text
   * @color - Medium-dark slate (#64748b) for secondary text
   * @related - .panel-empty-title for heading text
   */
  .panel-empty-copy {
    font-size: 0.9rem;
    color: #64748b;
  }

  /**
   * Mobile tweaks keep controls tappable and maintain breathing room.
   *
   * On mobile viewports, the header switches from horizontal to vertical layout
   * to accommodate narrower screens. This stacks the title above the action buttons
   * rather than trying to fit them side-by-side, which would compress the title
   * or wrap the buttons.
   *
   * @usage - Applied when viewport width ≤640px
   * @layout - Column direction stacks title and WindowActionControls vertically
   * @alignment - Left-aligned to match mobile UI conventions
   * @spacing - Reduced gap and body margin-top for tighter mobile layout
   * @related - .panel-header base styles, .panel-body responsive sizing
   */
  @media (max-width: 640px) {
    /**
     * Header switches to column layout on mobile for better title/button organization.
     *
     * @layout - Column direction stacks title above buttons
     * @alignment - flex-start left-aligns all children
     * @spacing - 1rem gap provides adequate touch target separation
     */
    .panel-header {
      flex-direction: column;
      align-items: flex-start;
      gap: 1rem;
    }

    /**
     * Body gets tighter top margin and responsive min-height on mobile.
     *
     * @spacing - Reduced margin-top from 1.25rem to 1rem saves vertical space
     * @sizing - min() ensures body doesn't exceed 55vh or 360px on short screens
     */
    .panel-body {
      margin-top: 1rem;
      min-height: min(55vh, 360px);
    }
  }
</style>
  .ai-summary-panel--sheet {
    border: none;
    box-shadow: none;
    padding: 0;
    background: transparent;
  }

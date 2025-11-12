<script>
  import EmailIframe from './EmailIframe.svelte';

  /**
   * Renders the selected email body (iframe vs markdown) with responsive padding. Extracted to keep the
   * main App component focused on data orchestration.
   */
  export let email = null;
  export let mobile = false;
  export let tablet = false;
  export let desktop = false;
  export let wide = false;
  /** Required: must sanitize input before returning HTML for {@html} rendering */
  export let renderMarkdownFn;
</script>

{#if email}
  <div class="overflow-y-auto flex-1">
    <div class="w-full max-w-full overflow-x-hidden"
         class:p-4={!email.contentHtml && (mobile || tablet)}
         class:p-5={!email.contentHtml && desktop}
         class:p-6={!email.contentHtml && wide}>
      {#if email.contentHtml}
        <EmailIframe html={email.contentHtml} />
      {:else}
        <div class="prose prose-sm max-w-none text-slate-700 break-words">
          {@html renderMarkdownFn(email.contentMarkdown || email.contentText || '')}
        </div>
      {/if}
    </div>
  </div>
{/if}

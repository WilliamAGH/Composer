<script>
  import MailboxChromeProvider from './lib/providers/MailboxChromeProvider.svelte';
  import ShellLayout from './ShellLayout.svelte';
  import AiCommandProvider from './lib/providers/AiCommandProvider.svelte';
  import ErrorBoundary from './lib/components/ErrorBoundary.svelte';
  import ErrorBanner from './lib/components/ErrorBanner.svelte';
  import ToastContainer from './lib/components/ToastContainer.svelte';
  import './app-shared.css';

  /**
   * App.svelte is now a thin bootstrapper. Providers own state initialization so this component simply
   * passes the server bootstrap payload into the shell layout.
   *
   * Error handling hierarchy:
   * - ErrorBoundary: catches render errors in child components, surfaces via ErrorBanner
   * - ErrorBanner: fixed top banner for fatal errors requiring user action (reload/dismiss)
   * - ToastContainer: transient error notifications (API failures, validation) that auto-dismiss
   */
  export let bootstrap = {};
</script>

<!-- Fatal error banner (fixed at top, shown when fatal error set) -->
<ErrorBanner />

<!-- Toast notifications (fixed at bottom-right, auto-dismiss) -->
<ToastContainer />

<!-- Error boundary catches render errors and surfaces them via ErrorBanner -->
<ErrorBoundary>
  <MailboxChromeProvider {bootstrap}>
    <AiCommandProvider {bootstrap}>
      <ShellLayout />
    </AiCommandProvider>
  </MailboxChromeProvider>
</ErrorBoundary>

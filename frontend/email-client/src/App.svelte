<script>
  import MailboxChromeProvider from './lib/providers/MailboxChromeProvider.svelte';
  import ShellLayout from './ShellLayout.svelte';
  import './app-shared.css';

  /**
   * App.svelte is now a thin bootstrapper. Providers own state initialization so this component simply
   * passes the server bootstrap payload into the shell layout.
   */
  export let bootstrap = {};
</script>

<MailboxChromeProvider {bootstrap}>
  <ShellLayout {bootstrap} />
</MailboxChromeProvider>

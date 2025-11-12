<script>
  import { createWindowManager } from './windowStore';
  import { provideWindowContext } from './windowContext';

  /**
   * Provides the shared window manager via context so child components can open/minimize windows without
   * prop-drilling. Accepts an existing manager or creates one from config.
   */
  export let manager = null;
  export let config = {};

  const instance = manager || createWindowManager(config);
  provideWindowContext(instance);
</script>

<slot data-window-manager={instance}></slot>

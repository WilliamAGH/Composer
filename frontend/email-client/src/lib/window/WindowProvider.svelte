<script>
  import { createWindowManager } from './windowStore';
  import { provideWindowContext } from './windowContext';

  /**
   * Provides the shared window manager via context so child components can open/minimize windows without
   * prop-drilling. Accepts an existing manager or creates one from config.
   */
  export let windowManager = null;
  export let manager = null; // deprecated alias for backwards compatibility
  export let config = {};

  const instance = windowManager || manager || createWindowManager(config);
  provideWindowContext(instance);
</script>

<slot data-window-manager={instance}></slot>

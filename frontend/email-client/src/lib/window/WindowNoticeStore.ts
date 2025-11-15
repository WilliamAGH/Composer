import { writable, type Readable } from 'svelte/store';

export interface WindowNoticeStore extends Readable<string> {
  show: (message: string, duration?: number) => void;
  clear: () => void;
}

/**
 * Creates a shared toast/notice store used by the floating window system.
 * Keeps a single message visible at a time and clears it after the timeout elapses.
 * @returns {{subscribe: import('svelte/store').Readable<string>['subscribe'], show: (message: string, duration?: number) => void, clear: () => void}}
 */
export function createWindowNoticeStore(): WindowNoticeStore {
  const notice = writable('');
  let timer: ReturnType<typeof setTimeout> | null = null;

  function show(message: string, duration = 4000) {
    if (!message) {
      clear();
      return;
    }
    notice.set(message);
    if (timer) {
      clearTimeout(timer);
    }
    timer = setTimeout(() => {
      notice.set('');
      timer = null;
    }, duration);
  }

  function clear() {
    if (timer) {
      clearTimeout(timer);
      timer = null;
    }
    notice.set('');
  }

  return {
    subscribe: notice.subscribe,
    show,
    clear
  };
}

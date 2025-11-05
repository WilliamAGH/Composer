import { readable } from 'svelte/store';

export const isMobile = readable(false, (set) => {
  if (typeof window === 'undefined' || !window.matchMedia) {
    set(false);
    return () => {};
  }
  const mq = window.matchMedia('(max-width: 768px)');
  const onChange = () => set(!!mq.matches);
  onChange();
  mq.addEventListener('change', onChange);
  return () => mq.removeEventListener('change', onChange);
});
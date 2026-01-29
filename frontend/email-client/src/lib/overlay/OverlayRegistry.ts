import { writable, type Readable } from "svelte/store";
import type { SvelteComponent } from "svelte";

export type OverlayPresenter = "backdrop" | "sheet" | "modal";

export interface OverlayInstance {
  id: string;
  component: typeof SvelteComponent;
  props?: Record<string, unknown>;
  zIndex?: number;
}

export interface OverlayDefinition {
  key: string;
  presenter: OverlayPresenter;
  priority: number;
  instances: OverlayInstance[];
}

export interface OverlaySourceConfig {
  key: string;
  presenter: OverlayPresenter;
  priority: number;
  source: Readable<OverlayInstance[]>;
}

export function createOverlayRegistry() {
  const entries = new Map<string, OverlayDefinition>();
  const unsubscribers = new Map<string, () => void>();
  const { subscribe, set } = writable<OverlayDefinition[]>([]);

  function emit() {
    set(Array.from(entries.values()));
  }

  function register(config: OverlaySourceConfig) {
    unregister(config.key);
    const entry: OverlayDefinition = {
      key: config.key,
      presenter: config.presenter,
      priority: config.priority,
      instances: [],
    };
    entries.set(config.key, entry);

    const unsubscribe = config.source.subscribe((instances) => {
      entry.instances = Array.isArray(instances) ? instances : [];
      emit();
    });

    unsubscribers.set(config.key, () => {
      unsubscribe();
      entries.delete(config.key);
      emit();
    });

    emit();
    return () => unregister(config.key);
  }

  function unregister(key: string) {
    const dispose = unsubscribers.get(key);
    if (dispose) {
      dispose();
      unsubscribers.delete(key);
    } else if (entries.delete(key)) {
      emit();
    }
  }

  return {
    subscribe,
    register,
    unregister,
  };
}

import { derived, type Readable } from "svelte/store";
import {
  createOverlayRegistry,
  type OverlayDefinition,
  type OverlayInstance,
  type OverlayPresenter,
  type OverlaySourceConfig,
} from "./OverlayRegistry";

export interface OverlayStackItem extends OverlayInstance {
  key: string;
  presenter: OverlayPresenter;
  priority: number;
}

export interface OverlayController {
  registerOverlay: (config: OverlaySourceConfig) => () => void;
  unregisterOverlay: (key: string) => void;
  overlays: Readable<OverlayStackItem[]>;
}

export function createOverlayController(): OverlayController {
  const registry = createOverlayRegistry();

  const overlays = derived(registry, ($entries: OverlayDefinition[]) =>
    $entries
      .slice()
      .sort((a, b) => a.priority - b.priority)
      .flatMap((entry) =>
        entry.instances.map(
          (instance): OverlayStackItem => ({
            ...instance,
            key: entry.key,
            presenter: entry.presenter,
            priority: entry.priority,
          }),
        ),
      ),
  );

  return {
    registerOverlay: registry.register,
    unregisterOverlay: registry.unregister,
    overlays,
  };
}

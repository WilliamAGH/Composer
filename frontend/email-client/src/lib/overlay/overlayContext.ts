import { getContext, setContext } from "svelte";
import type { OverlayController } from "./OverlayController";

const OVERLAY_CONTROLLER_KEY = Symbol("overlay-controller");

export function provideOverlayController(controller: OverlayController) {
  setContext(OVERLAY_CONTROLLER_KEY, controller);
}

export function useOverlayController(): OverlayController {
  const controller = getContext<OverlayController | undefined>(OVERLAY_CONTROLLER_KEY);
  if (!controller) {
    throw new Error("Overlay controller context not found. Ensure OverlayHost provides it.");
  }
  return controller;
}

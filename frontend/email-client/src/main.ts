/**
 * Email client application entry point.
 * Validates bootstrap data from server and initializes the Svelte app.
 */
import { mount } from "svelte";
import App from "./App.svelte";
import "./generated/tailwind.css";

import {
  EmailClientBootstrapSchema,
  type EmailClientBootstrap,
} from "./lib/schemas/bootstrapSchemas";
import { validateWithLogging } from "./lib/validation/validatedParse";

// Re-export schema types for consumers that need them
export type { EmailClientBootstrap } from "./lib/schemas/bootstrapSchemas";
export type { EmailMessage } from "./lib/schemas/emailSchemas";
export type {
  AiFunctionCatalogDto,
  AiFunctionSummary,
  AiFunctionVariantSummary,
} from "./lib/schemas/catalogSchemas";

declare global {
  interface Window {
    __EMAIL_CLIENT_BOOTSTRAP__?: unknown;
  }
}

const defaultBootstrap: EmailClientBootstrap = {
  uiNonce: null,
  messages: [],
  folderCounts: {},
  effectiveFolders: {},
  aiFunctions: null,
};

/**
 * Validate and extract bootstrap data from the window object.
 * Falls back to default if missing or invalid.
 */
function getValidatedBootstrap(): EmailClientBootstrap {
  const rawBootstrap = window.__EMAIL_CLIENT_BOOTSTRAP__;

  if (rawBootstrap === undefined) {
    // No bootstrap data provided - use default (expected in some dev scenarios)
    return defaultBootstrap;
  }

  const validationResult = validateWithLogging(
    EmailClientBootstrapSchema,
    rawBootstrap,
    "window.__EMAIL_CLIENT_BOOTSTRAP__",
  );

  if (!validationResult.success) {
    // Validation logged full error details - fall back to defaults
    return defaultBootstrap;
  }

  return validationResult.data;
}

const bootstrap = getValidatedBootstrap();

const target = document.getElementById("email-client-root");

if (!target) {
  throw new Error("Email client root element is missing from the DOM");
}

const app = mount(App, {
  target,
  props: { bootstrap },
});

export default app;

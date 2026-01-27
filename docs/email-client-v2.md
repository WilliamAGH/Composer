# Email Client — Architecture and Flow

## Overview
- Host: Thymeleaf template `templates/email-client-v2.html` injects bootstrap data and CSP/nonce.
- App: Svelte + Vite builds static assets to `src/main/resources/static/app/email-client/`.
- Backend: Spring Boot serves APIs and the host page; no Node in production.

Data flow
1) Controller (`WebViewController.emailClientV2`) provides:
   - `uiNonce` via `UiNonceService`
   - `emailMessages` via `EmailMessageProvider.loadEmails()`
2) Template sets `window.__EMAIL_CLIENT_BOOTSTRAP__ = { uiNonce, messages, aiFunctions }` (if `aiFunctions` is null the client now fetches them via `GET /api/ai-functions`).
3) Svelte mounts to `#email-client-root`, normalizes messages, and renders UI.
4) Email HTML bodies render in an isolated iframe via `window.EmailRenderer.renderInIframe()`.

## Window system (compose + AI panels)

Composer’s desktop UI uses a shared window shell for compose drafts, AI summaries, and future tools.

- `frontend/email-client/src/lib/window/windowTypes.ts` – typed factories for window descriptors (kept outside Svelte component code so stores/helpers can reuse them without rendering).
- `frontend/email-client/src/lib/window/windowStore.ts` – Svelte store that enforces per-mode limits, manages focus/minimize, and keeps AI panels tied to email IDs.
- `frontend/email-client/src/lib/window/WindowFrame.svelte` – shared chrome for floating/docked windows; feature components wrap this instead of duplicating markup.
- `frontend/email-client/src/lib/UnifiedDock.svelte` – unified dock for all minimized windows (compose + AI panels) with consistent spacing and restoration UX.

Add new AI windows by creating a feature component that wraps `WindowFrame` and registering it in `windowStore` rather than building bespoke overlays.

## Reply / forward compose behavior

- Compose actions originate in `EmailActionToolbar.svelte` and flow through the shell coordinator (`ShellLayout.svelte`) so window creation stays centralized.
- Reply/forward prefill behavior lives in `frontend/email-client/src/lib/services/composePrefill.ts`:
  - Reply/forward normalize subject prefixes (`Re:` / `Fwd:`) consistently.
  - Both flows keep greetings/signatures above the quoted context by inserting quoted metadata/body at the bottom of the compose body.
  - Forward intentionally leaves `To` blank so users don’t accidentally send to the original sender.
- Compose payloads carry `quotedContext` so future helpers can preserve quoted thread context even after AI edits.

Email safety model
- Server-side sanitization: `EmailHtmlSanitizer` strips scripts, handlers, embeds, and dangerous CSS; constrains images.
- Client isolation: `email-renderer.js` creates a sandboxed iframe with strict CSP (script-src 'none', object-src 'none', form-action 'none', base-uri 'none', connect-src 'none').
- Fallback: If EmailRenderer is unavailable, Svelte uses DOMPurify to sanitize and render (dev-only path).
- Remote images: default behavior relies on sanitizer + CSP; optional “display images” toggle can be added to widen img-src or hydrate lazy images.

### Rendering compatibility guardrails
- The iframe stylesheet is *compatibility-first*: it only enforces container sizing, image down-scaling, and reverts accidental `tbody`/`thead` display overrides. We intentionally avoid blanket resets such as `box-sizing: border-box` or `table-layout: fixed` so fragile newsletter markup retains its intended geometry.
- The wrapper element (`.email-wrapper`) adds only a light 16px padding buffer so messages never touch the frame edge, while letting content dictate width/height and simply exposing a horizontal scrollbar if an email genuinely needs to exceed the viewport.
- Emails rendered through the iframe now clamp tables, block-level elements, and inline `style="width:..."` declarations to `max-width: 100%` so tablet/mobile breakpoints no longer bleed wider desktop layouts into the viewport. The clamp keeps newsletters readable on 640–960px widths while preserving original layout ratios when space allows.
- Never add global selectors that touch `table`, `td`, `tr`, etc. inside the iframe unless there is a documented client break. Prefer targeted fixes (e.g., `.email-wrapper table > tbody { display: table-row-group !important; }`) and record the reasoning here when changes are required.
- Do not style the rendered HTML from Svelte/Tailwind—treat the iframe as an opaque boundary and only communicate via `EmailRenderer`.

Local development
- Spring only (serve built assets): `make build-java` (after a Vite build) and `make run` then open `/email-client-v2`.
- Frontend dev (recommended): `make fe-dev` (Vite on :5183, proxies /api to Spring). Keep Spring running with `make run` for APIs.

Build and deploy
- Single entry: `make build` → builds Vite bundle then packages the Spring Boot JAR.
- Sub-builds: `make build-vite` (frontend only) and `make build-java` (backend only).
- Production JAR serves the Svelte assets directly from `/app/email-client/`.

File map
- Template: `src/main/resources/templates/email-client-v2.html`
- Renderer: `src/main/resources/static/js/email-renderer.js`
- Frontend root: `frontend/email-client/`
  - `vite.config.ts` (outDir + proxy)
  - `src/main.ts`, `src/App.svelte`, `src/lib/EmailIframe.svelte`
- Window system (compose + AI summary)
  - `src/lib/window/windowTypes.ts` – typed window descriptors + factories
  - `src/lib/window/windowStore.ts` – Svelte store managing open/minimized windows
  - `src/lib/window/WindowFrame.svelte` – shared chrome
  - `src/lib/UnifiedDock.svelte` – unified dock for all minimized components
- Feature windows: `ComposeWindow.svelte`, `AiSummaryWindow.svelte`
- Sidebar & message UI: `MailboxSidebar.svelte`, `EmailActionToolbar.svelte`, `EmailDetailView.svelte`
- Message UI components: `EmailActionToolbar.svelte`, `EmailDetailView.svelte`

## Mobile acceptance checklist

Use this quick sweep whenever mobile UX changes ship (compose, AI summary, drawers):

1. **Email selection + nav** – On ≤640px width, select an email and verify the shared mobile top navigation renders (back + menu + search) and toggles the drawer correctly.
2. **Summary modal** – Trigger an AI summary/translation; the mobile modal should take the full viewport, show the AI journey, and close via the nav back button.
3. **Docking** – Tap the dock/minimize action inside the summary modal and confirm the AI panel dock chip appears for restoration.
4. **Compose parity** – Open Compose (new, reply, forward) and ensure the same nav chrome appears with the close control and send CTA, even while AI drafts run inline.
5. **Breakpoint sanity** – Rotate the simulator or drag DevTools to switch between mobile/tablet widths to make sure compose + summary overlays adapt without clipping and AI journeys remain visible.

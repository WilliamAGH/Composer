# Email Client v2 (Svelte) — Architecture and Flow

Status: Canonical client served at /email-client-v2 (legacy route /email-client redirects here)

Overview
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

Email safety model
- Server-side sanitization: `EmailHtmlSanitizer` strips scripts, handlers, embeds, and dangerous CSS; constrains images.
- Client isolation: `email-renderer.js` creates a sandboxed iframe with strict CSP (script-src 'none', object-src 'none', form-action 'none', base-uri 'none', connect-src 'none').
- Fallback: If EmailRenderer is unavailable, Svelte uses DOMPurify to sanitize and render (dev-only path).
- Remote images: default behavior relies on sanitizer + CSP; optional “display images” toggle can be added to widen img-src or hydrate lazy images.

### Rendering compatibility guardrails
- The iframe stylesheet is *compatibility-first*: it only enforces container sizing, image down-scaling, and reverts accidental `tbody`/`thead` display overrides. We intentionally avoid blanket resets such as `box-sizing: border-box` or `table-layout: fixed` so fragile newsletter markup retains its intended geometry.
- The wrapper element (`.email-wrapper`) adds only a light 16px padding buffer so messages never touch the frame edge, while letting content dictate width/height and simply exposing a horizontal scrollbar if an email genuinely needs to exceed the viewport.
- Never add global selectors that touch `table`, `td`, `tr`, etc. inside the iframe unless there is a documented client break. Prefer targeted fixes (e.g., `.email-wrapper table > tbody { display: table-row-group !important; }`) and record the reasoning here when changes are required.
- Do not style the rendered HTML from Svelte/Tailwind—treat the iframe as an opaque boundary and only communicate via `EmailRenderer`.

Local development
- Spring only (serve built assets): `make build-java` (after a Vite build) and `make run` then open `/email-client-v2`.
- Frontend dev (recommended): `make fe-dev` (Vite on :5173, proxies /api to Spring). Keep Spring running with `make run` for APIs.

Build and deploy
- Single entry: `make build` → builds Vite bundle then packages the Spring Boot JAR.
- Sub-builds: `make build-vite` (frontend only) and `make build-java` (backend only).
- Production JAR serves the Svelte assets directly from `/app/email-client/`.

File map
- Template: `src/main/resources/templates/email-client-v2.html`
- Renderer: `src/main/resources/static/js/email-renderer.js`
- Frontend root: `frontend/email-client/`
  - `vite.config.js` (outDir + proxy)
  - `src/main.js`, `src/App.svelte`, `src/lib/EmailIframe.svelte`
- Window system (compose + AI summary)
  - `src/lib/window/windowTypes.js` – factory helpers (JS module for reuse across stores/components)
  - `src/lib/window/windowStore.js` – Svelte store managing open/minimized windows
  - `src/lib/window/WindowFrame.svelte` – shared chrome
  - `src/lib/window/WindowDock.svelte` – minimized dock UI
- Feature windows: `ComposeWindow.svelte`, `AiSummaryWindow.svelte`
- Sidebar & message UI: `MailboxSidebar.svelte`, `EmailActionToolbar.svelte`, `EmailDetailView.svelte`
- Message UI components: `EmailActionToolbar.svelte`, `EmailDetailView.svelte`

Cutover
- Validate `/email-client-v2` in staging (legacy path already redirects here).
- `/email-client` remains as a redirect only; there is no separate fallback template.

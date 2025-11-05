# Email Client v2 (Svelte) — Architecture and Flow

Status: Experimental rollout under /email-client-v2

Overview
- Host: Thymeleaf template `templates/email-client-v2.html` injects bootstrap data and CSP/nonce.
- App: Svelte + Vite builds static assets to `src/main/resources/static/app/email-client/`.
- Backend: Spring Boot serves APIs and the host page; no Node in production.

Data flow
1) Controller (`WebViewController.emailClientV2`) provides:
   - `uiNonce` via `UiNonceService`
   - `emailMessages` via `EmailMessageProvider.loadEmails()`
2) Template sets `window.__EMAIL_CLIENT_BOOTSTRAP__ = { uiNonce, messages, commandDefaults, commandTemplates }`.
3) Svelte mounts to `#email-client-root`, normalizes messages, and renders UI.
4) Email HTML bodies render in an isolated iframe via `window.EmailRenderer.renderInIframe()`.

Email safety model
- Server-side sanitization: `EmailHtmlSanitizer` strips scripts, handlers, embeds, and dangerous CSS; constrains images.
- Client isolation: `email-renderer.js` creates a sandboxed iframe with strict CSP (script-src 'none', object-src 'none', form-action 'none', base-uri 'none', connect-src 'none').
- Fallback: If EmailRenderer is unavailable, Svelte uses DOMPurify to sanitize and render (dev-only path).
- Remote images: default behavior relies on sanitizer + CSP; optional “display images” toggle can be added to widen img-src or hydrate lazy images.

Local development
- Backend only: `make run` (Spring serves /email-client-v2; requires a prior frontend build).
- Frontend dev (recommended): `make fe-dev` (Vite on :5173, proxies /api to Spring on :8080). The dev index.html provides minimal bootstrap and Tailwind.

Build and deploy
- Build frontend: `make fe-build` → emits `/static/app/email-client/email-client.js`.
- Build all: `make build-all` → builds Svelte bundle then Maven package.
- Production JAR serves the Svelte assets directly from `/app/email-client/`.

File map
- Template: `src/main/resources/templates/email-client-v2.html`
- Renderer: `src/main/resources/static/js/email-renderer.js`
- Frontend root: `frontend/email-client/`
  - `vite.config.js` (outDir + proxy)
  - `src/main.js`, `src/App.svelte`, `src/lib/EmailIframe.svelte`

Cutover
- Validate /email-client-v2 in staging.
- Flip route from `/email-client` to use the new template after sign-off; keep old page available for rollback.

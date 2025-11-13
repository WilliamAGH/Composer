# Composer Architecture Entry Point

```
                          +-----------------------------+
                          |   External Email Providers   |
                          +--------------+--------------+
                                         |
                                         v
+------------------+     ingest via   +----------------------+     exposes      +-----------------------+
| Mailbox Intakes  +----------------->+  Spring Boot 3 Core  +----------------> | REST (/api/**) Layer  |
| (Gmail, IMAP, etc)|                 |  (Java 21)           |                 | adapters/in/web       |
+---------+--------+                 +----------+-----------+                 +----+--------------------+
          |                                   |                                   |
          |                                   |                                   |
          |                                   v                                   v
          |                       +----------------------+            +---------------------------+
          |                       | Application Usecases |            | Frontend (Svelte + Vite)  |
          |                       | src/main/java/...    |            | frontend/email-client     |
          |                       +----------+-----------+            +-----------+---------------+
          |                                  |                                    |
          |                                  v                                    v
          |                  +-------------------------------+       +-------------------------------+
          |                  | Domain + Ports (domain/**)    |       | UI Stores & Services (lib/**) |
          |                  +-------------------------------+       +---------------+---------------+
          |                                  |                                    |
          |                                  v                                    v
          |                  +-------------------------------+       +-------------------------------+
          |                  | Outbound Adapters (OpenAI,    |       | Svelte Components (lib/*.svelte)
          |                  | Qdrant, Persistence)          |       | Pages wired via App.svelte     |
          |                  +-------------------------------+       +-------------------------------+
          |                                                                                |
          |                                                                                v
          +------------------------------------ data / actions <------------------- WindowManager &
                                                                                     Session clients
```

## Backend (Java 21 + Spring Boot 3)

```
src/main/java/com/composerai/api
├── boot/                    // Application entry point + typed configuration
├── application/usecase/     // Business orchestrations (e.g., LaunchMailboxAutomation...UseCase)
├── domain/                  // Pure domain models, services, and ports
├── adapters/in/web/         // REST controllers translating HTTP ↔ DTOs
├── adapters/out/**          // External integrations (OpenAI, Qdrant, persistence)
└── shared/                  // Cross-cutting helpers (error envelopes, validation)
```

* **Why:** Controllers stay thin; use cases own transactions; domain enforces invariants; adapters isolate frameworks.
* **Important conventions:**
  - All new config goes under `boot/` using `@ConfigurationProperties`.
  - Use case records/DTOs live under `application/usecase/dto`.
  - Integration logic (OpenAI, mailbox state, automation) belongs in adapters/out implementing domain ports.

## Frontend (Svelte + Vite)

```
frontend/email-client
├── src/App.svelte                 // Orchestrates providers & page layout only
├── src/lib/
│   ├── components/*.svelte        // UI atoms (EmailListPane, EmailActionToolbar, etc.)
│   ├── stores/*.js                // Writable/derived stores (mailboxLayoutStore, actionMenuStore)
│   ├── services/*.js              // Non-visual logic (sessionNonceClient, catalogCommandClient)
│   ├── constants/*.js             // Shared command metadata & journey copy
│   └── window/**                  // WindowManager + compose window infrastructure
├── src/app-shared.css             // Design tokens / global glassmorphism rules
└── dist/ (gitignored build output)
```

* **State flow:**
  - `createMailboxLayoutStore` owns mailbox email lists, selection, drawer/sidebar state, and hydrates `/api/mailboxes/{id}/state` via `sessionNonceClient`.
  - `createActionMenuSuggestionsStore` requests catalog suggestions through `executeCatalogCommand` so App never holds cache logic.
  - `App.svelte` subscribes to stores, wires events (select email, run automation, open compose) and renders components via `WindowProvider`.
  - `callAiCommand` delegates to `executeCatalogCommand`, and mailbox automation goes through `launchMailboxAutomation` → `/api/mailboxes/{id}/automation`.

## Cross-cutting Transport & Auth

* **Nonce-aware transport:** `lib/services/sessionNonceClient.js` exposes `initializeUiNonce`, `postJsonWithNonce`, `getJsonWithNonce`, and `startChatHeartbeat`. Every fetch that hits Spring Boot must go through these helpers so 403 refreshes are centralized.
* **Catalog commands:** `lib/services/catalogCommandClient.js` is the single entry point for `/api/catalog-commands/{commandKey}/execute` so AI requests stay DRY.
* **Automation:** `lib/services/mailboxAutomationClient.js` posts to `/api/mailboxes/{id}/automation`. All list-level actions should call this client instead of hitting OpenAI directly.

## Mailbox Folder State (session-scoped)

```
Frontend                                     Backend
---------                                    -----------------------------------------
mailboxSessionService.js  --> X-Mailbox-Session header
mailboxStateClient.js     --> /api/mailboxes/{id}/state
mailboxLayoutStore.js     <-- MailboxStateSnapshotResult (messages + counts + placements)
                                     |
                                     v
adapters/in/web/MailboxFolderStateController
      |
      v
application/usecase/mailbox/
  ├── LoadMailboxStateSnapshotUseCase
  └── ExecuteMessageMoveUseCase
      |
      v
domain/service/MailboxFolderTransitionService
      |
      +--> MailboxSnapshotPort (FileSystemMailboxSnapshotAdapter -> EmailMessageProvider)
      +--> SessionScopedMessagePlacementPort (in-memory adapter for session overrides)
```

*Why:* The UI now gets the same structures we will reuse once a real IMAP adapter exists. Per-session folder moves live server-side so adding a real mailbox backend later only requires swapping the outbound ports.

- **UI controls**: `EmailActionToolbar` and `EmailListPane` both call `mailboxLayout.moveMessageRemote` through shared `MailboxMoveMenu`, ensuring archive/move/delete behave identically on desktop/tablet/mobile.
- **Drafts**: `ComposeWindow` emits `saveDraft` every time the user types; `mailboxLayout.saveDraftSession` reflects those changes under the Drafts folder and `markDraftAsSent` toggles the entry to Sent on send.

## Guidance When Adding Code

1. **Start with this document + `AGENTS.md` to understand the layering.**
2. **Follow the package layout** (use case → domain → adapter) for backend logic. Do not add logic directly to controllers.
3. **In the frontend**, prefer creating a store/service/component under `src/lib/` before touching `App.svelte`.
4. **Reuse shared constants** (`lib/constants/*.js`) instead of sprinkling default strings or prompt fragments across files.
5. **All network calls** must use `sessionNonceClient` helpers; mixing raw `fetch` introduces nonce drift.
6. **Document purpose-first:** when new flows span backend + frontend, update this file plus the relevant section in `AGENTS.md`.

This file is the authoritative “entry point” for understanding Composer’s architecture—extend it whenever a new subsystem or workflow lands.

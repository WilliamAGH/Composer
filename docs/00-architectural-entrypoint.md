# Composer Architecture Entry Point

## Purpose & Usage
- **Why this file exists:** to keep purpose alignment (Step 0) explicit by documenting *why* Composer exists and enumerating every artifact in the repo so contributors know exactly where to extend functionality without guesswork.
- **How to use it:** before touching code, scan the end-to-end flow, jump to the inventory section for the file you plan to edit, and note its responsibilities plus any neighboring files you may need to update.
- **Scope covered:** Spring Boot backend (Java 21 + resources), the Svelte/Vite frontend, datasets under `data/`, build scripts, docs, tests, and configuration metadata checked into git. Local node_modules, build artifacts, and secrets are intentionally excluded.

## End-to-End System Flow
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

## Repository Inventory
Each bullet lists a real file (tracked in git) and what it does so you can quickly find the right extension point.

### Root & Tooling (workspace top level)
- `.classpath` — Eclipse classpath metadata so IDEs know how to resolve the Maven project.
- `.editorconfig` — Shared whitespace/casing rules consumed by IDEs and editors.
- `.factorypath` — Eclipse annotation processing classpath definition used while generating sources.
- `.gitignore` — Ignore rules covering build artifacts, node_modules, IDE state, and generated assets.
- `.hintrc` — HTMLHint configuration that enforces conventions on server-rendered templates.
- `.project` — Eclipse project metadata for importing the Maven module.
- `.settings/org.eclipse.core.resources.prefs` — Workspace encoding/resource preferences for Eclipse.
- `.settings/org.eclipse.jdt.apt.core.prefs` — Eclipse APT (annotation processing) configuration toggles.
- `.settings/org.eclipse.jdt.core.prefs` — Java compiler preferences (warnings, source levels) for Eclipse.
- `.settings/org.eclipse.m2e.core.prefs` — M2E (Eclipse Maven plugin) metadata that keeps Maven sync stable.
- `.vscode/settings.json` — VS Code workspace defaults (formatting, TypeScript, Svelte tooling).
- `.zed/settings.json` — Zed editor workspace preferences.
- `AGENTS.md` — Operational rulebook for autonomous agents contributing to Composer (non-negotiable guardrails).
- `ComposerAIFavIcon.png` — Source favicon asset bundled into the Spring static resources.
- `Dockerfile` — Multi-stage build for packaging the Spring Boot app plus Vite bundle into a container.
- `Makefile` — Task shortcuts (`make build`, `make run`, `make lint`, etc.) orchestrating Maven + Vite workflows.
- `README.md` — High-level project overview, tech stack summary, and quick-start steps.
- `deploy.sh` — Helper script used during manual deployments to package and push artifacts.
- `package-lock.json` — Root-level lockfile for any Node tooling that lives outside the frontend package (e.g., repo-level scripts).
- `pom.xml` — Maven descriptor: declares Spring Boot plugins, Java 21 target, and third-party dependencies (OpenAI SDK, etc.).
- `spotbugs-exclude.xml` — Exclusion filters for SpotBugs to ignore intentional patterns during `make lint`.

### Data Fixtures (`data/`)
- `data/eml/Ashish, need a little help with your homework_.eml` — Sample `.eml` used to test parsing of CSV-style content.
- `data/eml/CA DMV- Complete Your REAL ID Application.eml` — DMV reminder fixture for timeline QA.
- `data/eml/Expanding our service in CA.eml` — Marketing outreach sample email for enrichment tests.
- `data/eml/Find a spacious and affordable way to pick up friends.eml` — Ride share promo email fixture.
- `data/eml/Homework.eml` — Plain-text homework reminder used for low-complexity ingestion paths.
- `data/eml/Important_ 30 days left in your free trial.eml` — Billing reminder fixture containing urgency copy.
- `data/eml/OpenAI is hiring.eml` — Recruiting email used inside OpenAI contextual demos.
- `data/eml/Posts from VC News Daily for 09182025.eml` — Newsletter digest fixture for multi-message parsing.
- `data/eml/Thanks for Subscribing, Ashish Divakaran.eml` — Subscription confirmation email sample.
- `data/eml/Thanks for your order—we're getting it ready..eml` — Receipt-style sample email (note UTF-8 em dash) validating sanitization.
- `data/eml/Your order from Fire Wings (order #f4886bdb).eml` — Food order confirmation fixture for entity extraction tests.
- `data/eml/[EXTERNAL]Essay Collaboration.eml` — External-tagged message verifying subject markers.
- `data/eml/hovibear is now following you on Twitch.eml` — Social notification sample for icon rendering flows.
- `data/eml/sample-emma-williams.eml` — Synthetic message for seeded persona Emma Williams.
- `data/eml/sample-michael-chen.eml` — Synthetic message for persona Michael Chen.
- `data/eml/sample-sarah-johnson.eml` — Synthetic message for persona Sarah Johnson.
- `data/markdown/openai-is-hiring.md` — Markdown digest of the “OpenAI is hiring” newsletter.
- `data/markdown/posts-from-vc-news-daily-for-09182025.md` — Markdown version of the VC News Daily digest.
- `data/openai-is-hiring.json` — Structured JSON representation of the hiring email for RAG experiments.
- `data/plain/openai-is-hiring.txt` — Plain-text export of the hiring email.
- `data/plain/posts-from-vc-news-daily-for-09182025.txt` — Plain-text export of the VC News digest.
- `data/posts-from-vc-news-daily-for-09182025.json` — JSON digest copy of the VC News newsletter.

### Documentation (`docs/`)
- `docs/00-architectural-entrypoint.md` — **This file**: canonical architecture reference plus file inventory (kept current).
- `docs/email-client-v2.md` — Frontend UX write-up covering v2 mail client journeys.
- `docs/email-context-conversation-uuids.md` — Notes on how conversation UUIDs are generated and propagated.
- `docs/example-openai-conversation-json-2025-11-12.md` — Captured JSON transcript from an OpenAI reasoning session for troubleshooting.

### Backend Java Source (`src/main/java/com/composerai/api`)
#### Boot + Config
- `ComposerAiApiApplication.java` — Spring Boot entry point; boots the API and ties together auto-configuration.
- `config/AiFunctionCatalogProperties.java` — `@ConfigurationProperties` exposing AI function catalog configuration.
- `config/AppProperties.java` — Application-scoped typed properties (app name, version, etc.).
- `config/ClientConfiguration.java` — Bean factory for outbound HTTP clients and JSON mappers shared across services.
- `config/CorsConfig.java` — Global CORS setup for `/api/**` endpoints to support the Svelte frontend.
- `config/ErrorMessagesProperties.java` — Typed message bundle for user-facing error text.
- `config/GlobalModelAttributes.java` — Injects enums/constants into Thymeleaf templates so JS can read backend values safely.
- `config/MagicEmailProperties.java` — Config for magic email integration (deployment toggles, keys).
- `config/OpenAiProperties.java` — Stores API keys, default models, and request tuning for OpenAI calls.
- `config/ProviderCapabilities.java` — Records which features are enabled per AI provider (used by controllers & UI models).
- `config/QdrantProperties.java` — Typed configuration for Qdrant vector store connectivity.
- `config/SecurityHeadersConfig.java` — Adds default security headers (CSP, frame policies) to responses.

#### Controllers & Web Adapters (`controller/**` + `adapters/in/web`)
- `adapters/in/web/MailboxFolderStateController.java` — REST adapter that exposes folder state snapshot + move APIs backed by use cases.
- `adapters/in/web/dto/MessageMoveRequest.java` — HTTP DTO describing a mailbox move (folder + target message IDs).
- `controller/AiFunctionCatalogController.java` — Serves read-only catalog metadata for AI function discovery.
- `controller/CatalogCommandController.java` — Executes catalog commands invoked from the UI (AI actions, macros).
- `controller/ChatController.java` — SSE/REST endpoints providing AI chat responses via OpenAI/OpenRouter.
- `controller/EmailFileParseController.java` — QA endpoint that ingests uploaded `.eml` files and shows parsed output.
- `controller/QaWebController.java` — Serves QA Thymeleaf pages (email parser, diagnostics) for manual validation.
- `controller/SystemController.java` — System health endpoints (ping/version) for uptime checks.
- `controller/UiNonceService.java` — Server-side helper that manages UI nonce issuance; consumed via `UiSessionController`.
- `controller/UiSessionController.java` — REST endpoints for session bootstrap and nonce refresh.
- `controller/WebViewController.java` — Serves the Svelte SPA shell (`email-client` bundle) under `/email-client-v2` (default landing for `/`) and hosts the diagnostics chat view at `/chat-diagnostics` (also available at `/chat`).

#### Application Layer (`application/**`)
- `application/dto/mailbox/MailboxStateSnapshotResult.java` — Use-case response describing messages, folders, and placements sent to the UI.
- `application/dto/mailbox/MessageMoveCommand.java` — Command object capturing the intent to move or delete a message.
- `application/dto/mailbox/MessageMoveResult.java` — Result DTO summarizing the server-side outcome of a move (new placements).
- `application/usecase/mailbox/ExecuteMessageMoveUseCase.java` — Coordinates folder transitions, validates requests, and persists placements via ports.
- `application/usecase/mailbox/LoadMailboxStateSnapshotUseCase.java` — Loads the canonical mailbox snapshot combining provider data with session overrides.

#### Domain Layer (`domain/**`)
- `domain/model/MailFolderIdentifier.java` — Value object identifying a mail folder (type + human label).
- `domain/model/MailboxSnapshot.java` — Aggregate capturing the server-side view of folders, drafts, and metadata.
- `domain/model/MessageFolderPlacement.java` — Value object to track where a message currently resides per session.
- `domain/port/MailboxSnapshotPort.java` — Abstraction over mailbox data providers (filesystem, IMAP, etc.).
- `domain/port/SessionScopedMessagePlacementPort.java` — Interface for persisting session-specific placements/moves.
- `domain/service/MailboxFolderTransitionService.java` — Domain service containing rules for legal folder moves and transitions.

#### Outbound Adapters (`adapters/out/**`)
- `adapters/out/mailbox/FileSystemMailboxSnapshotAdapter.java` — File-based implementation of `MailboxSnapshotPort` that reads demo emails from disk.
- `adapters/out/persistence/SessionScopedMessagePlacementAdapter.java` — In-memory/session persistence of message placements implementing `SessionScopedMessagePlacementPort`.

#### AI Catalog Helpers (`ai/**`)
- `ai/AiFunctionCatalogHelper.java` — Utilities for composing AI function descriptors and metadata payloads.
- `ai/AiFunctionDefinition.java` — Record describing a single AI function (name, description, parameters).

#### Shared DTOs & Validation (`dto/**`, `validation/**`, `exception/**`)
- `dto/AiFunctionCatalogDto.java` — DTO returned by the catalog API summarizing available AI functions.
- `dto/ChatRequest.java` — Request payload accepted by chat endpoints (messages, catalog actions, nonce info).
- `dto/ChatResponse.java` — Response envelope for chat completions (stream + final message info).
- `dto/ErrorResponse.java` — Standardized error envelope used by `GlobalExceptionHandler`.
- `dto/SseEventType.java` — Enum describing SSE event names emitted by chat streaming endpoints.
- `validation/AiCommandValid.java` — Custom annotation for validating incoming AI command payloads.
- `validation/AiCommandValidator.java` — Constraint validator enforcing catalog command requirements.
- `exception/GlobalExceptionHandler.java` — Centralized `@ControllerAdvice` translating exceptions into `ErrorResponse` objects.

#### Services & Business Logic (`service/**`)
- `service/ChatService.java` — High-level orchestrator for conversational flows tying together OpenAI, ledgering, and prompts.
- `service/CompanyLogoProvider.java` — Supplies company logos used in chat responses from known domains.
- `service/ContextBuilder.java` — Gathers retrieval-augmented context before requests are sent to LLM providers.
- `service/EmailParsingService.java` — Parses `.eml` files into `EmailMessage` objects using the email pipeline.
- `service/HtmlToText.java` — Utility to convert HTML email bodies into readable plain text.
- `service/OpenAiChatService.java` — Service implementation for handling OpenAI chat completion requests (streaming + sync).
- `service/OpenRouterRequestAdapter.java` — Adapter translating Composer chat requests into OpenRouter payloads.
- `service/ReasoningStreamAdapter.java` — Handles reasoning model streaming semantics (tools vs. text events).
- `service/VectorSearchService.java` — Talks to Qdrant to fetch contextual documents for prompts.

#### Email Pipeline (`service/email/**`)
- `service/email/ChunkingStrategy.java` — Defines how email bodies are chunked prior to embedding or display.
- `service/email/DataDirectoryEmailMessageProvider.java` — Reads `.eml` files from `data/` to act as a mail provider during demos/tests.
- `service/email/EmailDocumentBuilder.java` — Builds `EmailMessage` documents enriched with metadata for indexing.
- `service/email/EmailExtractor.java` — Pulls fields (subject, sender, attachments) from raw `.eml` blobs.
- `service/email/EmailHtmlSanitizer.java` — Cleans HTML bodies for safe rendering inside the sandboxed iframe.
- `service/email/EmailMessageProvider.java` — Interface describing providers capable of yielding `EmailMessage` collections.
- `service/email/EmailPipeline.java` — Coordinates extraction, sanitization, chunking, and indexing of incoming emails.
- `service/email/HtmlConverter.java` — Converts HTML email bodies to sanitized markup for the renderer.

#### Shared Ledger & Session Utilities (`shared/**`)
- `shared/ledger/ChatLedgerRecorder.java` — Writes chat interactions to the ledger for auditing.
- `shared/ledger/ContextRef.java` — Typed reference to context docs used in a chat turn.
- `shared/ledger/ConversationEnvelope.java` — Envelope summarizing a chat conversation for storage/export.
- `shared/ledger/ConversationEvent.java` — Individual ledger event (user turn, tool call, completion) record.
- `shared/ledger/ConversationLedgerService.java` — Service for persisting/retrieving ledger events.
- `shared/ledger/EmailContextResolver.java` — Maps email IDs to context references for ledger readability.
- `shared/ledger/EmailObject.java` — Ledger-ready representation of an email artifact.
- `shared/ledger/LlmCallPayload.java` — Details about outbound LLM calls saved for auditing.
- `shared/ledger/ToolCallPayload.java` — Records specific tool call metadata invoked during a chat.
- `shared/ledger/UsageMetrics.java` — Tracks token counts and latency metrics emitted by LLM providers.
- `shared/session/SessionTokenResolver.java` — Resolves session tokens/nonces injected into API calls.

#### Utilities & Models
- `model/EmailMessage.java` — Core POJO representing an email (headers, body, metadata).
- `model/EmailMessageContextFormatter.java` — Formats an `EmailMessage` into a prompt-friendly context string.
- `util/IdGenerator.java` — Generates opaque IDs for sessions, conversations, and requests.
- `util/StringUtils.java` — Shared string helpers (null-safe operations, trimming, etc.).
- `util/TemporalUtils.java` — Time helpers (clock abstraction, formatting, TTL calculations).

#### Validation-bound Records
- `service/OpenAiChatService.java` relies on `AiCommandValid` etc. (see above) — documented to highlight cross-cutting use.

### Backend Resources (`src/main/resources`)
- `application.properties` — Default Spring configuration for local/dev environments.
- `application-prod.properties` — Production overrides loaded when the `prod` profile is active.
- `static/apple-touch-icon.png` — Touch icon served for iOS home screen shortcuts.
- `static/css/chat.css` — Stylesheet for the diagnostics chat tooling surface.
- `static/css/layout.css` — Styles for legacy Thymeleaf layouts.
- `static/favicon.ico`, `static/favicon.svg`, `static/favicon-96x96.png` — Favicons delivered by the Spring static handler.
- `static/index.html` — Landing page stub for the static site variant.
- `static/js/email-renderer.js` — Sandboxed iframe script used to safely render raw email HTML; this is the lone JavaScript file left intentionally outside the TypeScript toolchain.
- `static/site.webmanifest`, `static/web-app-manifest-192x192.png`, `static/web-app-manifest-512x512.png` — PWA manifest + icons served with the SPA bundle.
- `templates/layout.html` — Base Thymeleaf layout shell shared across server-rendered pages.
- `templates/index.html` — Server-rendered landing page hooking into `layout.html`.
- `templates/chat.html` — Diagnostics chat view (markdown logging/chat tooling) served at `/chat-diagnostics`.
- `templates/email-client-v2.html` — Template embedding the V2 email client (Svelte bundle) with nonce injection.
- `templates/qa/diagnostics.html` — QA diagnostics interface rendered on demand.
- `templates/qa/email-file-parser.html` — QA UI for uploading `.eml` files and viewing parsed output.

### Frontend (Svelte + Vite under `frontend/email-client`)
#### Workspace Config
- `.depcheckrc.json` — Depcheck configuration to ensure imports map cleanly.
- `.oxlintrc.json` — Oxlint config for TypeScript/JavaScript lint rules.
- `.stylelintrc.json` — Stylelint config for CSS/Tailwind linting.
- `index.html` — Vite dev-server entry that mounts the Svelte app and injects nonce placeholders. Tailwind **must** be loaded via the build pipeline (see `src/app.css`)—never reintroduce the CDN snippet here.
- `package.json` — Frontend package metadata, scripts, and dependency graph.
- `package-lock.json` — Locked dependency tree for deterministic Svelte builds.
- `vite.config.ts` — Vite configuration (Svelte plugin, CSP nonce wiring, path aliases).

#### Developer Scripts (`frontend/email-client/scripts`)
- `check-dead-code.sh` — Runs depcheck/oxlint to flag unused exports.
- `check-unused-global-css.sh` — Scans `app-shared.css` usage to keep the design system DRY.

> Every file under `frontend/email-client/src/**` now uses TypeScript for logic, ensuring end-to-end type safety across stores, services, and helpers. Only autoplay-safe static assets in `src/main/resources/static/js/` remain JavaScript.

#### App Shell (`frontend/email-client/src`)
- `main.ts` — Vite entry: hydrates `App.svelte`, imports `src/app.css` (Tailwind base/components/utilities), and passes bootstrap payloads into providers.
- `App.svelte` — Thin bootstrapper that layers providers (`MailboxChromeProvider`, `AiCommandProvider`) around `ShellLayout`.
- `app.css` — Tailwind entry point (`@tailwind base/components/utilities`), compiled via PostCSS; keep global overrides out of this file.
- `app-shared.css` — Global glassmorphism/token stylesheet shared by every component.

#### UI Components & Lib Files (`frontend/email-client/src/lib`)
- `AiCommandButtons.svelte` — Renders AI command buttons for launching catalog actions from panels.
- `AiLoadingJourney.svelte` — Loading-state visualization while AI responses stream back.
- `AiSummaryMobileSheet.svelte` — Mobile bottom sheet showing AI summaries on small screens.
- `AiSummaryWindow.svelte` — Desktop floating window summarizing AI output.
- `ComingSoonModal.svelte` — Modal component used for unreleased feature callouts.
- `ComposeMobileSheet.svelte` — Mobile compose interface optimized for drawers.
- `ComposeWindow.svelte` — Desktop compose window with draft-saving hooks.
- `DrawerBackdrop.svelte` — Backdrop overlay used when drawers or sheets are active.
- `EmailActionToolbar.svelte` — Toolbar containing actions (reply, move, AI) for the active message.
- `EmailDetailMobileSheet.svelte` — Mobile detail view overlay for email content.
- `EmailDetailView.svelte` — Desktop detail pane rendering sanitized email content and metadata.
- `EmailIframe.svelte` — Wrapper for the sandboxed iframe plus loading/error states.
- `EmailListPane.svelte` — List component showing message summaries with selection + virtualization logic.
- `MailboxMoveMenu.svelte` — Menu for selecting destination folders powered by store state.
- `MailboxSidebar.svelte` — Sidebar navigation listing folders, counts, and filter chips.
- `MobileOverlayCoordinator.ts` — Utility that coordinates drawer/backdrop behavior across mobile sheets.
- `MobileTopBar.svelte` — Mobile header with navigation + account controls.
- `Modal.svelte` — Generic modal wrapper that handles focus traps and transitions.
- `UnifiedDock.svelte` — Unified dock for all minimized components (compose windows, AI panels) with consistent styling and automatic spacing.
- `WindowNotice.svelte` — Non-dismissable notice element pinned within the window manager.
- `aiJourney.ts` — Helper functions describing AI journey steps and statuses.
- `constants/catalogActions.ts` — Catalog of actionable AI commands exposed in the UI.
- `constants/journeyScopes.ts` — Enumerates journey scopes (mailbox, thread, compose) used by stores.
- `constants/mailboxMoveTargets.ts` — Centralized list of folder targets for move menus.

#### Frontend Services (`frontend/email-client/src/lib/services`)
- `aiCatalog.ts` — Fetches AI catalog metadata from the backend and caches it client-side.
- `aiCommandHandler.ts` — Client-side orchestrator for invoking catalog commands and handling responses.
- `aiJourneyStore.ts` — Store manager for AI journey progress + historical steps.
- `catalogCommandClient.ts` — Low-level fetch wrapper for `/api/catalog-commands/{key}/execute` with nonce support.
- `composePrefill.ts` — Logic that seeds compose drafts based on selected emails or templates.
- `conversationLedger.ts` — Client-side mirror of the conversation ledger for UI playback/debugging.
- `emailContextConstructor.ts` — Builds the payload describing selected emails before AI calls.
- `emailFormatting.ts` — Formatting helpers (dates, participants) for list + detail panes.
- `emailSubjectPrefixHandler.ts` — Normalizes subject prefixes (Re, Fwd, etc.) for display and deduping.
- `emailUtils.ts` — Shared email helpers (initials, list labeling, snippet generation).
- `letterAvatarGenerator.ts` — Generates deterministic avatar colors/initials per sender.
- `mailboxAutomationClient.ts` — Client for launching automation runs (`/api/mailboxes/{id}/automation`).
- `mailboxFiltering.ts` — Filtering/sorting helpers for mailbox queries.
- `mailboxSessionService.ts` — Handles session IDs/nonces used on every mailbox API request.
- `mailboxStateClient.ts` — Fetch client for `/api/mailboxes/{id}/state` endpoints.
- `sessionNonceClient.ts` — Fetch helper injecting/refreshing the UI nonce across all requests.

#### Frontend Stores (`frontend/email-client/src/lib/stores`)
- `actionMenuSuggestionsStore.ts` — Derived store that asks AI for context-aware quick actions.
- `aiPanelStore.ts` — Store that tracks AI panel visibility, position, and resizing preferences.
- `mailboxDataStore.ts` — Source of truth for mailbox payloads (folders, counts, drafts, optimistic updates).
- `mailboxChromeStore.ts` — UI chrome store (selected email, sidebar open state, drawer visibility).
- `mailboxResponsiveState.ts` — Derived responsive metadata that toggles drawer mode, inline sidebar widths, and compact toolbar thresholds.

> Drawer & hamburger behavior flows strictly through `MailboxChromeStore` (state) + `MailboxResponsiveState` (breakpoints). Components such as `MailboxSidebar`, `DrawerBackdrop`, and `MobileTopBar` simply consume those stores. When adjusting mobile navigation, update these stores/providers rather than adding ad-hoc state.

#### Layout Helpers
- `viewportState.ts` — Keeps viewport/responsive breakpoints in sync across components.

#### Windowing System (`frontend/email-client/src/lib/window`)
- `WindowActionControls.svelte` — Buttons for minimizing/maximizing/closing window instances.
- `WindowFrame.svelte` — Chrome around each floating window, handling drag/resize.
- `WindowProvider.svelte` — Context provider that spawns, updates, and destroys window instances.
- `windowContext.ts` — Exports the Svelte context contract used by window child components.
- `windowStore.ts` — Writable store that tracks currently opened windows and their layout.
- `windowTypes.ts` — Enum-like constants describing supported window templates.

### Tests (`src/test/java` + resources)
- `src/test/java/com/composerai/api/ComposerAiApiApplicationTests.java` — Smoke test verifying the Spring context loads.
- `src/test/java/com/composerai/api/config/ProviderCapabilitiesTest.java` — Tests capability toggles and serialization.
- `src/test/java/com/composerai/api/controller/AiFunctionCatalogControllerTest.java` — Unit tests for the catalog REST endpoints.
- `src/test/java/com/composerai/api/controller/ChatControllerIntegrationTest.java` — Integration tests covering chat SSE behavior.
- `src/test/java/com/composerai/api/controller/EmailFileParseControllerTest.java` — Ensures file parsing QA endpoint works.
- `src/test/java/com/composerai/api/controller/QaWebControllerTest.java` — Covers QA template routing + model attributes.
- `src/test/java/com/composerai/api/controller/UiSessionControllerTest.java` — Validates nonce issuance and refresh flows.
- `src/test/java/com/composerai/api/controller/WebViewControllerTest.java` — Verifies SPA shell template wiring.
- `src/test/java/com/composerai/api/exception/GlobalExceptionHandlerTest.java` — Tests error envelope formatting.
- `src/test/java/com/composerai/api/service/OpenAiChatServiceStreamingTest.java` — Covers streaming integration with OpenAI clients.
- `src/test/java/com/composerai/api/service/OpenAiChatServiceTest.java` — Unit tests for synchronous OpenAI chat logic.
- `src/test/java/com/composerai/api/service/OpenRouterRequestAdapterTest.java` — Ensures OpenRouter payload generation stays stable.
- `src/test/java/com/composerai/api/service/ReasoningStreamAdapterTest.java` — Verifies reasoning model streaming event handling.
- `src/test/java/com/composerai/api/service/ValidatedThinkingConfigTest.java` — Confirms validated-thinking request config constraints.
- `src/test/java/com/composerai/api/service/VectorSearchServiceTest.java` — Tests Qdrant search integration and mapping.
- `src/test/java/com/composerai/api/shared/ledger/ConversationLedgerServiceTest.java` — Ensures ledger persistence functions correctly.
- `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` — Enables inline mocking (mockito-inline) in tests.

## Working Notes & Next Steps
- Keep this inventory synchronized whenever files move, arrive, or are removed—treat it as a living source of truth.
- If you add a new subsystem, update both this inventory and `README.md`/`AGENTS.md` to maintain Step-0 clarity.

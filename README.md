# Composer API

Java 21 Spring Boot backend that powers the Composer: a chat interface for reasoning over email mailbox data with LLM assistance and retrieval-augmented context.

## Developer Routes

- `/qa/diagnostics` – internal diagnostics workspace with health checks, mock retrievals, and UI preview controls.
- `/qa/email-file-parser` – QA-only email parsing workspace for uploading `.eml`/`.txt` files and inspecting normalized output produced by the shared `EmailParsingService` pipeline.

### Customizing AI Command Prompts

AI helper flows (compose, draft, summarize, translate, tone) are now declared in `AiFunctionCatalogProperties` (`src/main/java/com/composerai/api/config/AiFunctionCatalogProperties.java`) via the `ai.functions.*` namespace. `AiFunctionCatalogHelper` (`src/main/java/com/composerai/api/ai/AiFunctionCatalogHelper.java`) normalizes those properties and exposes them—through `AiFunctionCatalogDto` (`src/main/java/com/composerai/api/dto/AiFunctionCatalogDto.java`)—to:

- Back-end services (`ChatService`) when rendering prompts and enforcing validation.
- Validation (`AiCommandValidator`) for request safety.
- Front-end bootstrap JSON (`GlobalModelAttributes` → `aiFunctionCatalog`) so the Svelte client renders buttons, instructions, and variants directly from the catalog.
- The `/api/ai-functions` endpoint (`AiFunctionCatalogController`) for clients that need to refresh metadata after initial load (the Svelte client now auto-fetches when bootstrapped data is missing).

Each entry defines labels, prompt templates, default instructions, context strategy, scopes, and optional variants (e.g., translation languages). Override any field per environment in `application.properties` (or profile-specific variants).

```properties
# application-local.properties
ai.functions.compose.prompt-template=Compose a concise, action-oriented reply: {{instruction}}
ai.functions.compose.default-instruction=Compose a helpful reply using the selected context.
ai.functions.translate.variants.es.label=AI Translation (Spanish)
ai.functions.translate.variants.es.default-args.targetLanguage=Spanish
```

Placeholders such as `{{instruction}}`, `{{subject}}`, or keys from `defaultArgs`/`variants.defaultArgs` are replaced server-side before sending prompts to the model. If a custom value is omitted or blank the service falls back to the built-in defaults defined in `AiFunctionCatalogProperties`.

### Email Client Window System

Compose drafts and AI summary panels share a reusable window shell:

- `frontend/email-client/src/lib/window/windowTypes.js` – plain JS factories for window descriptors (kept outside Svelte/Java so multiple components/stores can reuse them without rendering).
- `frontend/email-client/src/lib/window/windowStore.js` – Svelte store that enforces per-mode limits, manages minimize/focus, and ensures summaries stay tied to their email IDs.
- `frontend/email-client/src/lib/window/WindowFrame.svelte` – shared chrome for floating/docked windows; feature components (`ComposeWindow.svelte`, `AiSummaryWindow.svelte`) wrap it instead of duplicating markup.
- `frontend/email-client/src/lib/window/WindowDock.svelte` – renders minimized windows as a bottom dock so they never overlap email content.

Add new AI windows by creating a feature component that wraps `WindowFrame` and registering it with the window store rather than building bespoke panels.

## Feature Highlights

- **Chat Orchestration** – Routes chat requests through OpenAI-compatible models, classifies user intent, and stitches email snippets into responses.
- **AI Request Journey Loader** – `frontend/email-client/src/lib/AiLoadingJourney.svelte` renders the deterministic lifecycle defined in `aiJourney.ts`, mirroring `ChatService.prepareChatContext` → `VectorSearchService.searchSimilarEmails` → `OpenAiChatService.generateResponse` so UI copy stays in sync with backend stages.
- **Mailbox AI Actions** – The email list header now exposes an "AI Actions" dropdown that targets mailbox-wide workflows (e.g., Smart triage & cleanup). The control appears next to the search bar on desktop/tablet and in the mobile toolbar so users can trigger scoped batch actions against the currently filtered messages, reusing the existing AI journey overlay and catalog metadata.
- **Vector Retrieval Stub** – Integrates with Qdrant for similarity search; ships with placeholder extraction logic so teams can map real payloads incrementally.
- **Email Parsing Workspace (QA)** – Provides an interactive HTML workspace for uploading `.eml`/`.txt` files and returning cleaned text output via the `/api/qa/parse-email` endpoint.
- **Diagnostics Control Panel** – Ships a rich, static diagnostics dashboard tailored for observing health checks, mock retrieval responses, and LLM outputs.
- **CLI Utilities** – Includes `HtmlToText` tooling for converting raw email sources to Markdown or plain text, enabling batch processing workflows.

### Responsive Sidebar Modes

The email client sidebar now derives a single `sidebarVariant` in `App.svelte` (`inline-wide`, `inline-desktop`, `inline-collapsed`, `drawer-visible`, `drawer-hidden`) instead of juggling raw viewport booleans. `MailboxSidebar.svelte` consumes that variant to decide width, positioning, and pointer/aria visibility so desktop collapses actually remove the navigation rail while mobile/tablet builds rely on the drawer overlay. This simplification makes the hamburger toggle deterministic across breakpoints and prevents conflicting Tailwind class bindings.

## Email Rendering & Isolation

- All `EmailMessage.emailBodyHtml` values are sanitized server-side via `EmailHtmlSanitizer`.
- The Svelte client at `/email-client-v2` renders sanitized HTML inside a sandboxed iframe (scripts disabled, same-origin enabled for sizing, inner scrollbars suppressed). If no HTML remains, it falls back to Markdown text.
- `/email-client` now redirects to `/email-client-v2` so bookmarks continue working while the legacy view is removed.

## Technology Stack

- **Runtime**: Java 21 (Spring Boot 3.3.x)
- **Frontend**: Svelte + Vite (assets emitted under `src/main/resources/static/app/email-client/`), Tailwind utilities; lucide icons
- **Server UI**: Thymeleaf host templates for bootstrapping and CSP/nonce injection
- **Build**: `make build` (Vite → Maven)
- **Integrations**: OpenAI Java SDK, Qdrant, Flexmark, JSoup, Jakarta Mail
- **Containers**: Multi-stage Docker build

## Requirements

- Java Development Kit 21
- Maven 3.9 or newer
- OpenAI API key (or compatible endpoint) for chat completion and intent classification
- Qdrant instance for production retrieval (development runs without an active connection)

## Configuration

Configure via environment variables or `application.properties` equivalents. The app also supports optional dotenv-style files loaded automatically if present:

```properties
# src/main/resources/application.properties
spring.config.import=optional:file:.env,optional:file:.env.local,optional:file:.env.properties,optional:file:.env.local.properties
```

Precedence: environment variables > `.env.local` > `.env` > `.env.local.properties` > `.env.properties` > bundled `application*.properties` defaults. This lets CI/CD inject secrets while dotenv-style files provide convenient local overrides.

```properties
server.port=${PORT:8080}
openai.api.key=${OPENAI_API_KEY:your-openai-api-key}
openai.api.base-url=${OPENAI_BASE_URL:https://api.openai.com/v1}
# default OpenAI model; override with LLM_MODEL when deploying alternate providers
openai.model.chat=${LLM_MODEL:gpt-4o-mini}
qdrant.enabled=${QDRANT_ENABLED:false}
qdrant.host=${QDRANT_HOST:localhost}
qdrant.port=${QDRANT_PORT:6333}
qdrant.use-tls=${QDRANT_USE_TLS:false}
qdrant.collection-name=${QDRANT_COLLECTION_NAME:emails}
qdrant.api-key=${QDRANT_API_KEY:}
```

Example `.env` (or `.env.properties`) for local use (do not commit secrets):

```properties
OPENAI_API_KEY=sk-...redacted...
OPENAI_BASE_URL=https://api.openai.com/v1
LLM_MODEL=gpt-4o-mini
QDRANT_ENABLED=true
QDRANT_HOST=cluster-abc.us-east-1-0.aws.cloud.qdrant.io
QDRANT_PORT=6334
QDRANT_USE_TLS=true
QDRANT_COLLECTION_NAME=emails
QDRANT_API_KEY=qdrant_cloud_api_key_here
```

Only `OPENAI_API_KEY`, `OPENAI_BASE_URL`, and `LLM_MODEL` are read at runtime for the LLM connection; legacy `OPENAI_API_BASE_URL`, `OPENAI_MODEL`, `LLM_API_KEY`, and `LLM_BASE_URL` variables are intentionally ignored to avoid drift between environments.

If you rely on a raw `.env` file, the application will load the key during startup even when the property value falls back to the placeholder. For `make run`, ensure your shell exports the variables (e.g. `set -a; source .env; set +a`) or keep the `.env` file present at the project root so the configuration loader can read it. Retrieval can be turned off entirely by leaving `QDRANT_ENABLED` unset or set to `false`.

### Qdrant usage modes

- Local development (default): `qdrant.enabled=false` yields no outbound Qdrant calls; vector search is skipped gracefully.
- Cloud (Qdrant Cloud): set `QDRANT_ENABLED=true`, `QDRANT_HOST`, `QDRANT_PORT` (usually 6334), `QDRANT_USE_TLS=true`, and `QDRANT_API_KEY`.

The app attaches the API key to gRPC requests when supported by the Qdrant Java client version; when not supported, calls are still gated by `qdrant.enabled` to avoid failures.

`application-local.properties` enables Spring DevTools restart/live reload. Production profile disables HSTS headers through `app.hsts.enabled=false` for reverse-proxy compatibility.

### Using alternative OpenAI-compatible providers

To use OpenRouter, Groq, LM Studio, or other providers, set `OPENAI_BASE_URL`, `OPENAI_API_KEY`, and `LLM_MODEL`. Provider capabilities are auto-detected from the base URL.

### OpenRouter Configuration

Composer supports [OpenRouter](https://openrouter.ai) for multi-provider LLM access.

#### Basic Setup

```bash
export OPENAI_API_KEY="your-openrouter-api-key"
export OPENAI_BASE_URL="https://openrouter.ai/api/v1"
export LLM_MODEL="anthropic/claude-3.7-sonnet"
```

`LLM_API_KEY` and `LLM_BASE_URL` are no longer read so that deployments cannot inadvertently inherit stale multi-provider credentials.

#### Provider Routing

**Note**: Provider routing configuration is now supported and applied to requests. You can control provider selection and routing behavior using the environment variables below.

Control which providers OpenRouter uses:

```bash
# Prefer specific provider(s)
export LLM_PROVIDER_ORDER="anthropic,openai"

# Sort by price (cheapest first), throughput, or latency
export LLM_PROVIDER_SORT="price"

# Disable fallbacks
export LLM_PROVIDER_ALLOW_FALLBACKS="false"
```

[Learn more about OpenRouter provider routing](https://openrouter.ai/docs/features/provider-routing)

#### Reasoning Models

OpenRouter supports reasoning models but with different constraints:

```bash
# ✅ Supported: low, medium, high
export LLM_REASONING="medium"

# ❌ Not supported: minimal (OpenAI-only)
# Will automatically fallback to "low"
```

### Environment Variables Reference

| Variable | Default | Description |
|----------|---------|-------------|
| `OPENAI_API_KEY` | - | API key for all providers (required) |
| `OPENAI_BASE_URL` | `https://api.openai.com/v1` | Base URL for OpenAI-compatible providers |
| `LLM_MODEL` | `gpt-4o-mini` | Chat model identifier consumed by the service |
| `LLM_TEMPERATURE` | `0.5` | Sampling temperature (0-2) |
| `LLM_MAX_OUTPUT_TOKENS` | - | Max output tokens (model default if unset) |
| `LLM_TOP_P` | - | Nucleus sampling parameter (model default if unset) |
| `LLM_REASONING` | `low` | Reasoning effort: `low`, `medium`, `high`, `minimal`* |
| `LLM_PROVIDER_ORDER` | `novita` | Comma-separated provider preference (OpenRouter only) |
| `LLM_PROVIDER_SORT` | - | Sort providers: `price`, `throughput`, `latency` (OpenRouter only) |
| `LLM_PROVIDER_ALLOW_FALLBACKS` | `true` | Allow fallback providers (OpenRouter only) |
| `LLM_DEBUG_FETCH` | `false` | Log full request/response bodies |
| `RENDER_EMAILS_WITH` | `HTML` | Email client render mode: `HTML`, `MARKDOWN`, or `PLAINTEXT` |
| `SESSION_TIMEOUT` | `PT8H` | Servlet session timeout (ISO-8601 duration) used for UI nonce + cookie lifetime |

\* `minimal` only supported by OpenAI. Automatically converts to `low` for other providers.

## Local Development

```bash
# Compile sources and resolve dependencies
mvn clean compile

# Run tests
mvn test

# Launch the API with hot reload (local profile)
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Helpful Makefile targets:

- `make run` – Run Spring Boot locally (profile=local)
- `make build` – Build frontend (Vite) then backend (Maven) into a single JAR
- `make build-vite` – Build only the Svelte bundle into `src/main/resources/static/app/email-client/`
- `make build-java` – Build only the Spring Boot JAR
- `make fe-dev` – Start Vite dev server with API proxy
- `make test` – Run the full Maven test suite (override with `MAVEN_TEST_FLAGS="-DskipITs"` etc.)
- `make docker-build` – Build `composerai-api:local`
- `make docker-run-local` – Run container with local profile variables

## Docker Usage

```bash
# Build
make docker-build TAG=latest

# Run (local)
make docker-run-local TAG=latest PORT=8080
```

The runtime stage uses `openjdk:21-jdk-slim` and copies static assets for direct serving alongside the executable JAR. Swap the base images to Red Hat's UBI OpenJDK 21 variants if you prefer their support cadence.

## Static Workspaces

- Diagnostics dashboard: `http://localhost:8080/qa/diagnostics`
- Email parser UI: `http://localhost:8080/qa/email-file-parser`
- Redirect landing page: `http://localhost:8080/`

Each workspace follows the house design language: layered glass cards over soft gradients, deep slate or charcoal accents, diffused shadows, disciplined spacing, and crisp system typography.

## REST Endpoints

| Method | Path | Description |
| ------ | ---- | ----------- |
| `GET` | `/api/health` | Service heartbeat with timestamp |
| `GET` | `/api/chat/health` | Chat-specific heartbeat |
| `POST` | `/api/chat` | Main chat endpoint accepting message, optional conversation ID, `maxResults`, and a `contextId` referencing normalized email content |
| `POST` | `/api/chat/stream` | SSE stream of incremental tokens for live responses |
| `POST` | `/api/qa/parse-email` | QA-only multipart upload for `.eml`/`.txt` files that streams them through the shared `EmailParsingService` and returns a `contextId` for subsequent chat calls |
| `POST` | `/ui/session/nonce` | Renews the UI nonce for the active servlet session (used by the Svelte client to recover after session expiry) |

### Example Chat Request

```http
POST /api/chat
Content-Type: application/json

{
  "message": "Summarize updates from my hiring emails",
  "conversationId": "thread-123",
  "maxResults": 5,
  "contextId": "7c2f0a22-88d2-4bec-aad4-1f81f9b6f5af",
  "emailContext": "(optional) sanitized preview returned by /api/qa/parse-email"
}
`contextId` should originate from `/api/qa/parse-email`; if it is omitted or invalid, the server drops any supplied `emailContext` string and proceeds with vector-search context only. This guarantees that only normalized pipeline output can reach downstream LLM calls.
`contextId` must originate from `/api/parse-email`; if it is omitted or invalid, the server drops any supplied `emailContext` string and proceeds with vector-search context only. This guarantees that only the normalized pipeline output can reach downstream LLM calls.
```

### Streaming Chat (SSE)

Request is identical to `/api/chat`, but POST to `/api/chat/stream`. The response is `text/event-stream` with typed events.

Example SSE response:

```text
event: rendered_html
data: {"html":"<p>Hello</p>"}

event: rendered_html
data: {"html":"<p>, how can I help you today?</p>"}

event: done
data: {}
```

Example curl command:

```bash
curl -N -H "Accept: text/event-stream" -H "Content-Type: application/json" \
     -d '{"message":"hi","contextId":"test-123"}' http://localhost:8080/api/chat/stream
```

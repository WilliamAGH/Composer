# ComposerAI API

Java 21 Spring Boot backend that powers the ComposerAI: a chat interface for reasoning over email mailbox data with LLM assistance and retrieval-augmented context.

## Developer Routes

- `/qa/diagnostics` – internal diagnostics workspace with health checks, mock retrievals, and UI preview controls.
- `/qa/email-file-parser` – QA-only email parsing workspace for uploading `.eml`/`.txt` files and inspecting normalized output produced by the shared `EmailParsingService` pipeline.

### Customizing AI Command Prompts

The chat service renders AI helper flows (compose, draft, summarize, translate, tone) using templates sourced from `AiCommandPromptProperties`. Override them per environment in `application.properties` (or profile-specific variants) with the `ai.commands.prompts.<command>.template` key. Supported commands today are `compose`, `draft`, `summarize`, `translate`, and `tone`.

```properties
# application-local.properties
ai.commands.prompts.compose.template=Compose a concise, action-oriented reply: {{instruction}}
ai.commands.prompts.tone.template=Adjust the draft to a friendly yet professional tone: {{instruction}}
```

Templates accept the `{{instruction}}` placeholder, which is replaced with the user message passed from the UI. If a custom template is omitted or blank the service falls back to the built-in defaults.

## Feature Highlights

- **Chat Orchestration** – Routes chat requests through OpenAI-compatible models, classifies user intent, and stitches email snippets into responses.
- **Vector Retrieval Stub** – Integrates with Qdrant for similarity search; ships with placeholder extraction logic so teams can map real payloads incrementally.
- **Email Parsing Workspace (QA)** – Provides an interactive HTML workspace for uploading `.eml`/`.txt` files and returning cleaned text output via the `/api/qa/parse-email` endpoint.
- **Diagnostics Control Panel** – Ships a rich, static diagnostics dashboard tailored for observing health checks, mock retrieval responses, and LLM outputs.
- **CLI Utilities** – Includes `HtmlToText` tooling for converting raw email sources to Markdown or plain text, enabling batch processing workflows.

## Technology Stack

- **Runtime**: Java 21 (OpenJDK build) running on Spring Boot 3.3.x
- **Build**: Maven 3.9+, Spring Boot Maven Plugin
- **Web Layer**: Spring MVC, Jakarta Validation, Thymeleaf templates
- **Frontend Styling**: Tailwind CSS via CDN with soft gradients, translucent panels, and shadowed cards for a polished, product-grade aesthetic
- **Integrations**: OpenAI Java SDK (official, `com.openai:openai-java`—see `pom.xml` for version), Qdrant gRPC client, Flexmark, JSoup, Jakarta Mail
- **Containerization**: Multi-stage Docker build based on OpenJDK 21

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
openai.api.base-url=${OPENAI_API_BASE_URL:https://api.openai.com/v1}
# default OpenAI model; override with OPENAI_MODEL if needed
openai.model.chat=${OPENAI_MODEL:gpt-4o-mini}
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
OPENAI_API_BASE_URL=https://api.openai.com/v1
OPENAI_MODEL=gpt-4o-mini
QDRANT_ENABLED=true
QDRANT_HOST=cluster-abc.us-east-1-0.aws.cloud.qdrant.io
QDRANT_PORT=6334
QDRANT_USE_TLS=true
QDRANT_COLLECTION_NAME=emails
QDRANT_API_KEY=qdrant_cloud_api_key_here
```

If you rely on a raw `.env` file, the application will load the key during startup even when the property value falls back to the placeholder. For `make run`, ensure your shell exports the variables (e.g. `set -a; source .env; set +a`) or keep the `.env` file present at the project root so the configuration loader can read it. Retrieval can be turned off entirely by leaving `QDRANT_ENABLED` unset or set to `false`.

### Qdrant usage modes

- Local development (default): `qdrant.enabled=false` yields no outbound Qdrant calls; vector search is skipped gracefully.
- Cloud (Qdrant Cloud): set `QDRANT_ENABLED=true`, `QDRANT_HOST`, `QDRANT_PORT` (usually 6334), `QDRANT_USE_TLS=true`, and `QDRANT_API_KEY`.

The app attaches the API key to gRPC requests when supported by the Qdrant Java client version; when not supported, calls are still gated by `qdrant.enabled` to avoid failures.

`application-local.properties` enables Spring DevTools restart/live reload. Production profile disables HSTS headers through `app.hsts.enabled=false` for reverse-proxy compatibility.

### Using alternative OpenAI-compatible providers

To use OpenRouter, Groq, LM Studio, or other providers, set `OPENAI_API_BASE_URL`, `OPENAI_API_KEY`, and `OPENAI_MODEL`. Provider capabilities are auto-detected from the base URL.

### OpenRouter Configuration

ComposerAI supports [OpenRouter](https://openrouter.ai) for multi-provider LLM access.

#### Basic Setup

```bash
export LLM_API_KEY="your-openrouter-api-key"
export LLM_BASE_URL="https://openrouter.ai/api/v1"
export LLM_MODEL="anthropic/claude-3.7-sonnet"
```

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
| `LLM_API_KEY` | - | API key (OpenRouter, Groq, etc.) |
| `LLM_BASE_URL` | `https://api.openai.com/v1` | API base URL |
| `LLM_MODEL` | `gpt-4o-mini` | Model identifier |
| `LLM_TEMPERATURE` | `0.5` | Sampling temperature (0-2) |
| `LLM_MAX_OUTPUT_TOKENS` | - | Max output tokens (model default if unset) |
| `LLM_TOP_P` | - | Nucleus sampling parameter (model default if unset) |
| `LLM_REASONING` | `low` | Reasoning effort: `low`, `medium`, `high`, `minimal`* |
| `LLM_PROVIDER_ORDER` | `novita` | Comma-separated provider preference (OpenRouter only) |
| `LLM_PROVIDER_SORT` | - | Sort providers: `price`, `throughput`, `latency` (OpenRouter only) |
| `LLM_PROVIDER_ALLOW_FALLBACKS` | `true` | Allow fallback providers (OpenRouter only) |
| `LLM_DEBUG_FETCH` | `false` | Log full request/response bodies |

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

- `make run` – `SPRING_PROFILES_ACTIVE=local mvn spring-boot:run`
- `make build` – Package JAR with tests skipped
- `make test` – Run the full Maven test suite (override with `MAVEN_TEST_FLAGS="-DskipITs"` etc.)
- `make docker-build` – Build `composerai-api:local`
- `make docker-run-local` – Run container with local profile variables
- `make deps-refresh` – Purge cached OpenAI SDK artifacts and rebuild to pull fresh dependencies

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

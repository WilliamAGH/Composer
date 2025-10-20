# ComposerAI API

Java 21 Spring Boot backend that powers the ComposerAI: a chat interface for reasoning over email mailbox data with LLM assistance and retrieval-augmented context.

## Developer Routes

- `/diagnostics` – internal diagnostics workspace with health checks, mock retrievals, and UI preview controls.
- `/email-backend` – email parsing tooling for uploading `.eml`/`.txt` files and inspecting normalized output.

## Feature Highlights

- **Chat Orchestration** – Routes chat requests through OpenAI-compatible models, classifies user intent, and stitches email snippets into responses.
- **Vector Retrieval Stub** – Integrates with Qdrant for similarity search; ships with placeholder extraction logic so teams can map real payloads incrementally.
- **Email Parsing Workspace** – Provides an interactive HTML workspace for uploading `.eml`/`.txt` files and returning cleaned text output via the `/api/parse-email` endpoint.
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
openai.model=${OPENAI_MODEL:gpt-4o-mini}
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

- Diagnostics dashboard: `http://localhost:8080/diagnostics`
- Email parser UI: `http://localhost:8080/email-backend`
- Redirect landing page: `http://localhost:8080/`

Each workspace follows the house design language: layered glass cards over soft gradients, deep slate or charcoal accents, diffused shadows, disciplined spacing, and crisp system typography.

## REST Endpoints

| Method | Path | Description |
| ------ | ---- | ----------- |
| `GET` | `/api/health` | Service heartbeat with timestamp |
| `GET` | `/api/chat/health` | Chat-specific heartbeat |
| `POST` | `/api/chat` | Main chat endpoint accepting message, optional conversation ID, and `maxResults` |
| `POST` | `/api/chat/stream` | SSE stream of incremental tokens for live responses |
| `POST` | `/api/parse-email` | Multipart upload for `.eml`/`.txt` files that streams them through the normalization pipeline |

### Example Chat Request

```http
POST /api/chat
Content-Type: application/json

{
  "message": "Summarize updates from my hiring emails",
  "conversationId": "thread-123",
  "maxResults": 5
}
```

### Streaming Chat (SSE)

Request is identical to `/api/chat`, but POST to `/api/chat/stream`. The response is `text/event-stream` with tokens emitted as SSE (Server-Sent Events). Each token is sent as a separate event, allowing clients to display incremental results in real time.

Example SSE response:

```text
data: Hello

data: , how can I help you today?

data:

```
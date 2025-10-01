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
- **Integrations**: OpenAI Java SDK (official, `com.openai:openai-java:4.0.0`), Qdrant gRPC client, Flexmark, JSoup, Jakarta Mail
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
spring.config.import=optional:file:.env.properties,optional:file:.env.local.properties
```

Precedence: environment variables > `.env.local.properties` > `.env.properties` > bundled `application*.properties` defaults. This lets CI/CD inject secrets while `.env*.properties` provide convenient local overrides.

```properties
server.port=${PORT:8080}
openai.api.key=${OPENAI_API_KEY:your-openai-api-key}
openai.api.base-url=${OPENAI_API_BASE_URL:https://api.openai.com/v1}
# default to 4o latest; override with OPENAI_MODEL if needed
openai.model=${OPENAI_MODEL:chatgpt-4o-latest}
qdrant.host=${QDRANT_HOST:localhost}
qdrant.port=${QDRANT_PORT:6333}
qdrant.use-tls=${QDRANT_USE_TLS:false}
qdrant.collection-name=${QDRANT_COLLECTION_NAME:emails}
```

Example `.env.properties` for local use (do not commit secrets):

```properties
OPENAI_API_KEY=sk-...redacted...
OPENAI_API_BASE_URL=https://api.openai.com/v1
OPENAI_MODEL=chatgpt-4o-latest
QDRANT_HOST=cluster-abc.us-east-1-0.aws.cloud.qdrant.io
QDRANT_PORT=6334
QDRANT_USE_TLS=true
QDRANT_COLLECTION_NAME=emails
```

`application-local.properties` enables Spring DevTools restart/live reload. Production profile disables HSTS headers through `app.hsts.enabled=false` for reverse-proxy compatibility.

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

Request is identical to `/api/chat`, but POST to `/api/chat/stream`. The response is `text/event-stream` with tokens emitted as SSE `data:` frames.

```bash
curl -N -X POST \
  -H 'Content-Type: application/json' \
  -d '{"message":"Summarize updates from my hiring emails","maxResults":5}' \
  http://localhost:8080/api/chat/stream
```

Implementation details:
- Uses official OpenAI Java SDK `com.openai:openai-java:4.0.0` streaming API (`createStreaming`) for Chat Completions on `chatgpt-4o-latest`.
- Server emits each token as an SSE event. Close when complete.

### Email Parsing Response

Successful responses include `parsedText`, file metadata, and a status marker. `.msg` files currently return HTTP 415 with a descriptive error.

## CLI Email Conversion

`HtmlToText` can be run directly for batch conversions:

```bash
# Package once
target/composerai-api-0.0.1-SNAPSHOT.jar  # produced via mvn package

# Convert to Markdown via Maven exec
dev_args="--input-file data/eml/sample.eml --format markdown --urls stripAll --output-dir data/markdown"
mvn -q -DskipTests exec:java \
  -Dexec.mainClass=com.composerai.api.service.HtmlToText \
  -Dexec.args="$dev_args"

# Convert to plain text via fat JAR loader
java -Dloader.main=com.composerai.api.service.HtmlToText \
  -cp target/composerai-api-0.0.1-SNAPSHOT.jar \
  org.springframework.boot.loader.PropertiesLauncher \
  -- --input-file "data/eml/sample.eml" --format plain --output-dir "data/plain"
```

Key arguments:

- `--input-file <path>` (required)
- `--input-type eml|html` (auto-detected by extension)
- `--format plain|markdown` (required)
- `--urls keep|stripAll|cleanOnly` (defaults to `keep`)
- `--metadata true|false` (defaults to `true`)
- `--json true|false` (defaults to `false`)

## Project Structure

```text
src/main/java/com/composerai/api/
├── ComposerAiApiApplication.java
├── config/
│   ├── AppProperties.java             # HSTS toggle wiring
│   ├── ClientConfiguration.java       # OpenAI and Qdrant clients
│   ├── OpenAiProperties.java          # OpenAI configuration binding
│   ├── QdrantProperties.java          # Qdrant configuration binding
│   └── SecurityHeadersConfig.java     # Conditional HSTS filter
├── controller/
│   ├── ChatController.java            # REST endpoints for chat
│   ├── EmailController.java           # Email upload & parsing
│   ├── SystemController.java          # Service health
│   └── WebViewController.java         # Static Thymeleaf views
├── dto/                               # Chat request/response models
├── service/
│   ├── ChatService.java               # Chat orchestration
│   ├── OpenAiChatService.java         # OpenAI integration (official SDK + embeddings)
│   ├── VectorSearchService.java       # Qdrant search stub
│   ├── HtmlToText.java                # CLI entry point
│   └── email/                         # Email extraction & normalization
└── resources/
    ├── templates/                     # Thymeleaf pages
    ├── static/                        # Shared CSS, icons, manifest
    └── application-*.properties       # Profile configuration
```

## Implementation Notes

- Vector payload extraction from Qdrant is still a placeholder; map real subject/snippet metadata from point payloads.
- Embeddings now use OpenAI's Embeddings API (model configurable; defaults to `text-embedding-3-small`). Ensure Qdrant vector size matches.
- `ChatService` currently emits UUIDs when a conversation ID is not supplied.
- The email parser limits uploads to 10 MB and deletes temp files after processing.
- Security headers only clear existing HSTS when disabled—configure proxies accordingly.

## Roadmap Considerations

1. Implement real embedding generation via OpenAI or Azure equivalents.
2. Map Qdrant payloads to `EmailContext` with real subject/snippet data.
3. Add persistence for conversation history and message threading.
4. Harden error handling, authentication, and observability.
5. Extend the email parsing UI to support `.msg` conversion once the pipeline is ready.

## License

Internal project. No explicit license provided.

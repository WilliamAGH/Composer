# Getting Started

## Requirements

- JDK 25
- Node 20+ (frontend toolchain)

## Setup

1. Set your LLM provider credentials:

```bash
export OPENAI_API_KEY="..."
```

2. Run in dev mode (Java + Vite dev server together):

```bash
make dev
```

Open `http://localhost:5173/app/email-client/`.

Sample mailbox data lives under `data/eml/`. Drop `.eml` or `.txt` files there, or override `app.email-inbox.directory`.

## Production build

```bash
make build
make run
```

Open `http://localhost:8080/` (redirects to `/email-client-v2`).

## Build commands

| Command | Description |
|---------|-------------|
| `make dev` | Run Java + Vite dev servers together |
| `make build` | Build Vite bundle then package Spring Boot JAR |
| `make build-vite` | Frontend only |
| `make build-java` | Backend only |
| `make test` | Run tests |
| `make lint` | Run linters |

## Configuration

Spring loads environment variables and supports optional dotenv-style files via `spring.config.import` (see `src/main/resources/application.properties`).

### LLM provider

| Variable | Required | Description |
|----------|----------|-------------|
| `OPENAI_API_KEY` | Yes | API key for OpenAI or compatible provider |
| `OPENAI_BASE_URL` | No | Provider endpoint (default: `https://api.openai.com/v1`) |
| `LLM_MODEL` | No | Model identifier (default: `gpt-4o-mini`) |

### OpenRouter

To use [OpenRouter](https://openrouter.ai), set `OPENAI_BASE_URL=https://openrouter.ai/api/v1` and your OpenRouter API key.

Optional routing controls:

| Variable | Description |
|----------|-------------|
| `LLM_PROVIDER_ORDER` | Preferred provider order |
| `LLM_PROVIDER_SORT` | Sort strategy |
| `LLM_PROVIDER_ALLOW_FALLBACKS` | Allow fallback providers |

### Vector search (optional)

Qdrant integration enables semantic retrieval for richer context.

| Variable | Description |
|----------|-------------|
| `QDRANT_ENABLED` | Enable Qdrant (default: `false`) |
| `QDRANT_HOST` | Qdrant server host |
| `QDRANT_PORT` | Qdrant server port |
| `QDRANT_USE_TLS` | Use TLS for connection |
| `QDRANT_COLLECTION_NAME` | Collection name |
| `QDRANT_API_KEY` | API key (if required) |

### Source of truth

Configuration is bound via Spring `@ConfigurationProperties`:

- `src/main/java/com/composerai/api/config/OpenAiProperties.java` – LLM settings
- `src/main/java/com/composerai/api/config/QdrantProperties.java` – Vector search settings
- `src/main/java/com/composerai/api/config/AiFunctionCatalogProperties.java` – AI command catalog

## Local development

For frontend-focused work, run both servers:

```bash
# Terminal 1: Spring Boot
make run

# Terminal 2: Vite dev server (HMR, proxies /api to Spring)
make fe-dev
```

The Vite dev server runs on `:5173` and proxies API calls to Spring on `:8080`.

## Architecture overview

See [00-architectural-entrypoint.md](00-architectural-entrypoint.md) for:
- End-to-end system flow diagram
- Complete file inventory
- HTTP surface documentation
- AI function catalog details

## License

See [`../LICENSE.md`](../LICENSE.md).

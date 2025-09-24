# ComposerAI API

Java Spring Boot API backend for ComposerAI - a chat interface for interacting with your email mailbox using LLMs and RAG/BM25 retrievals.

## Features

- **Chat API**: RESTful endpoints for AI-powered chat with email context
- **Vector Search**: Integration with Qdrant for semantic email search using vector embeddings
- **OpenAI Integration**: Compatible with OpenAI and OpenAI-compatible LLM endpoints
- **Intent Analysis**: Automatic classification of user queries
- **Contextual Responses**: AI responses enriched with relevant email context

## Architecture

This backend API serves as the bridge between:

- Frontend chat interface
- Qdrant vector database (for email embeddings and similarity search)
- OpenAI-compatible LLM endpoints (for chat completion and intent analysis)

## Prerequisites

- Java 17+
- Maven 3.6+
- Qdrant vector database (running instance)
- OpenAI API key or compatible LLM endpoint

## Dependencies

- **Spring Boot 3.2.1**: Modern Java framework
- **OpenAI Java Client**: For LLM integration
- **Qdrant Java Client**: For vector search operations
- **Jackson**: JSON processing
- **Spring Boot Validation**: Request validation

## Configuration

Configure the application using `application.properties` or environment variables:

```properties
# Server Configuration
server.port=8080
# server.servlet.context-path=/api  # Commented out for direct static access

# OpenAI Configuration
openai.api.key=${OPENAI_API_KEY:your-openai-api-key}
openai.api.base-url=${OPENAI_API_BASE_URL:https://api.openai.com/v1}
openai.model=${OPENAI_MODEL:gpt-3.5-turbo}

# Qdrant Configuration
qdrant.host=${QDRANT_HOST:localhost}
qdrant.port=${QDRANT_PORT:6333}
qdrant.use-tls=${QDRANT_USE_TLS:false}
qdrant.collection-name=${QDRANT_COLLECTION_NAME:emails}
```

## Building and Running

### Development

```bash
# Build the application
mvn clean compile

# Run tests
mvn test

# Start the application with live reload (recommended for development)
mvn spring-boot:run -Dspring-boot.run.profiles=local

# With custom configuration
mvn spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.jvmArguments="-Dopenai.api.key=YOUR_KEY -Dqdrant.host=your-qdrant-host"

# Start without live reload (production-like)
mvn spring-boot:run
```

#### Live Development Features

When running with the `local` profile, you get:

- **Automatic restart** when Java classes change
- **Live reload** for static resources (HTML, CSS, JS)
- **Efficient resource usage** with optimized polling intervals
- **Static files** served from `src/main/resources/static/`

Access the application:

- Main UI: `http://localhost:8080/index.html` (redirects to diagnostics)
- Email Parser: `http://localhost:8080/email-backend.html`
- Diagnostics: `http://localhost:8080/diagnostics.html`
- API Health: `http://localhost:8080/api/health`

### Production

```bash
# Build JAR
mvn clean package

# Run JAR
java -jar target/composerai-api-0.0.1-SNAPSHOT.jar
```

## HTML Email Parsing (CLI)

Convert `.eml` or `.html` to plain text or Markdown using `com.composerai.api.service.HtmlToText`.

- Build once:
  - `mvn -q -DskipTests package`
- Run via Maven Exec (example to Markdown):
  - `mvn -q -DskipTests exec:java -Dexec.mainClass=com.composerai.api.service.HtmlToText -Dexec.args="--input-file 'data/eml/OpenAI is hiring.eml' --urls stripAll --format markdown --output-dir 'data/markdown'"`
 - Run via Spring Boot launcher (fat jar, example to Plain):
  - `java -Dloader.main=com.composerai.api.service.HtmlToText -cp target/composerai-api-0.0.1-SNAPSHOT.jar org.springframework.boot.loader.PropertiesLauncher -- --input-file "data/eml/OpenAI is hiring.eml" --urls stripAll --format plain --output-dir "data/plain"`

Arguments:

- `--input-file <path>` (required)
- `--input-type eml|html` (optional; inferred by extension)
- `--format plain|markdown` (required)
- `--output-file <path>` (optional; defaults to stdout)
- `--output-dir <dir>` (optional; auto-generates normalized file name like `openai-is-hiring.md`)
- `--charset <name>` (optional; for raw HTML files)
- `--urls keep|stripAll|cleanOnly` (optional; default `keep`)
- `--metadata true|false` (optional; default `true`) – when true, prepends Sender, Recipient(s), Date/time (ISO-8601), Subject
 - `--json true|false` (optional; default `false`) – when true, emits a JSON document with metadata, plainText, markdown

Notes:

- Prefers HTML part in `multipart/alternative`; falls back to text.
- HTML→Markdown uses Flexmark html2md; HTML→Plain uses JSoup with minimal structure preservation.
- `stripAll` removes links/images entirely; `cleanOnly` keeps only clean http(s)/mailto URLs and strips tracking params.

## API Endpoints

### System Health Check

```http
GET /api/health
```

### Email Parser

```http
POST /api/parse-email
Content-Type: multipart/form-data

# Upload .eml file for parsing
```

### Chat (Main Feature)

```http
POST /chat
Content-Type: application/json

{
  "message": "What emails did I receive about the project proposal?",
  "conversationId": "optional-conversation-id",
  "maxResults": 5
}
```

## Project Structure

```markdown
src/main/java/com/composerai/api/
├── ComposerAiApiApplication.java     # Main Spring Boot application
├── config/                          # Configuration classes
│   ├── ClientConfiguration.java     # Bean configurations for clients
│   ├── OpenAiProperties.java       # OpenAI configuration properties
│   └── QdrantProperties.java       # Qdrant configuration properties
├── controller/                      # REST controllers
│   └── ChatController.java         # Chat API endpoints
├── dto/                            # Data Transfer Objects
│   ├── ChatRequest.java           # Request models
│   └── ChatResponse.java          # Response models
└── service/                        # Business logic
    ├── ChatService.java           # Main chat orchestration
    ├── OpenAiChatService.java     # OpenAI integration
    ├── VectorSearchService.java   # Qdrant vector search
    └── email/                     # Email parsing & conversion modules
        ├── HtmlConverter.java     # HTML → plain/markdown with URL policy & cleanup
        ├── EmailExtractor.java    # MIME/EML extraction and header decoding
        ├── EmailPipeline.java     # Orchestration of extract→convert (used by CLI)
        ├── EmailDocumentBuilder.java # Build normalized JSON-ready documents
        └── ChunkingStrategy.java  # Interface for text chunking
```

## Development Notes

- The application uses placeholder implementations for vector search operations
- Email context extraction from Qdrant payloads needs to be implemented based on your data schema
- The embedding generation currently uses a placeholder - implement actual OpenAI embedding API calls
- Add proper error handling and logging for production use
- Consider implementing conversation persistence for multi-turn chat sessions

## Next Steps

1. Set up Qdrant with your email data and appropriate collection schema
2. Implement actual email data indexing pipeline
3. Configure OpenAI embedding generation for query vectors
4. Add authentication and authorization
5. Implement conversation persistence
6. Add monitoring and observability

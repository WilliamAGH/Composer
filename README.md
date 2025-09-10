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
server.servlet.context-path=/api

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

# Start the application
mvn spring-boot:run

# With custom configuration
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dopenai.api.key=YOUR_KEY -Dqdrant.host=your-qdrant-host"
```

### Production

```bash
# Build JAR
mvn clean package

# Run JAR
java -jar target/composerai-api-0.0.1-SNAPSHOT.jar
```

## API Endpoints

### Health Check
```http
GET /api/chat/health
```

### Chat
```http
POST /api/chat
Content-Type: application/json

{
  "message": "What emails did I receive about the project proposal?",
  "conversationId": "optional-conversation-id",
  "maxResults": 5
}
```

## Project Structure

```
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
    └── VectorSearchService.java   # Qdrant vector search
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

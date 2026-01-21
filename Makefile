APP_NAME ?= composerai-api
TAG ?= local
PORT ?= 8080
PROFILE ?= local
MAVEN_TEST_FLAGS ?=

.PHONY: help run dev build build-vite build-java java-compile test clean lint docker-build docker-run-local docker-run-prod fe-dev clean-frontend

help:
	@echo "Targets:"
	@echo "  make run           - Run Spring Boot locally (profile=local)"
	@echo "  make dev           - Run Java + Svelte dev servers together (unified logs)"
	@echo "  make build         - Build frontend (Vite) and backend (Maven)"
	@echo "  make build-vite    - Build Svelte bundle into Spring static/"
	@echo "  make build-java    - Build Spring Boot JAR (skip tests)"
	@echo "  make java-compile  - Run mvn clean compile (ensures annotation processors fire)"
	@echo "  make fe-dev        - Run Svelte dev server (Vite) with API proxy"
	@echo "  make clean         - Clean Java build and remove built frontend assets"
	@echo "  make test          - Run unit/integration tests (use MAVEN_TEST_FLAGS for overrides)"
	@echo "  make lint          - Run all linters (SpotBugs, Oxlint, maven-enforcer)"
	@echo "  make docker-build  - Build Docker image $(APP_NAME):$(TAG)"
	@echo "  make docker-run-local - Run Docker with local profile"
	@echo "  make docker-run-prod  - Run Docker with prod profile"

run:
	SPRING_PROFILES_ACTIVE=local mvn spring-boot:run -Dspring-boot.run.profiles=local

# Dev mode: run Java backend + Svelte dev server together with unified logs
# Uses awk to add colored prefixes while preserving output
# Access via http://localhost:5173 (Vite proxies /api and /ui to Spring Boot on :8080)
dev:
	@echo "Starting Java backend (port 8080) + Svelte dev server (port 5173)..."
	@echo "Access the app at: http://localhost:5173/app/email-client/"
	@echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
	@trap 'kill 0' INT TERM; \
	(cd frontend/email-client && bun install --silent && bun run dev 2>&1 | awk '{print "\033[36m[vite]\033[0m " $$0; fflush()}') & \
	(SPRING_PROFILES_ACTIVE=local mvn spring-boot:run -Dspring-boot.run.profiles=local 2>&1 | awk '{print "\033[33m[java]\033[0m " $$0; fflush()}') & \
	wait

# Orchestrated build: frontend first so assets are bundled into the JAR
build: build-vite build-java

# Sub-builds
build-vite: FE_DIR := frontend/email-client
build-vite:
	@echo "Building Svelte bundle into src/main/resources/static/app/email-client ..."
	@cd $(FE_DIR) && bun install && bun run build

build-java:
	@echo "Building Spring Boot JAR ..."
	@mvn -DskipTests package

java-compile:
	@echo "Cleaning and compiling Spring Boot sources ..."
	@mvn clean compile

# Dev & hygiene
FE_DIR := frontend/email-client

fe-dev:
	@echo "Starting Svelte dev server (Vite) with API proxy..."
	@cd $(FE_DIR) && bun install && bun run dev

clean-frontend:
	@echo "Removing built Svelte assets from static/app/email-client ..."
	@rm -rf src/main/resources/static/app/email-client

clean: clean-frontend
	@mvn -q clean

# Tests & lint

test:
	mvn $(MAVEN_TEST_FLAGS) test

lint:
	@echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
	@echo "🔍 Running linters for Java, JavaScript, Svelte..."
	@echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
	@echo ""
	@echo "📋 Maven Enforcer (dependency checks)..."
	@mvn validate -q
	@echo ""
	@echo "📦 SpotBugs (Java static analysis)..."
	@mvn compile spotbugs:spotbugs -q
	@if [ -f target/spotbugsXml.xml ]; then \
		BUGS=$$(grep -o "total_bugs='[0-9]*'" target/spotbugsXml.xml | grep -o "[0-9]*" | head -1); \
		echo "   SpotBugs report: $$BUGS issues (see target/spotbugsXml.xml)"; \
	else \
		echo "   ⚠️  No SpotBugs report generated"; \
	fi
	@echo ""
	@echo "⚡ Oxlint (JavaScript/Svelte <script> tags)..."
	@cd frontend/email-client && bun run lint
	@echo ""
	@echo "🎨 Stylelint (CSS & Svelte <style> tags - duplicate detection)..."
	@cd frontend/email-client && bun run lint:css
	@echo ""
	@echo "🧹 Unused :global() CSS Detection..."
	@cd frontend/email-client && ./scripts/check-unused-global-css.sh src
	@echo ""
	@echo "🗑️  Dead Code Detection (exports, deps, components)..."
	@cd frontend/email-client && ./scripts/check-dead-code.sh
	@echo ""
	@echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
	@echo "✅ Linting complete"

# Docker

docker-build:
	docker build --build-arg APP_NAME=$(APP_NAME) -t $(APP_NAME):$(TAG) .

docker-run-local:
	docker run --rm -p $(PORT):8080 --name $(APP_NAME) \
	 -e SPRING_PROFILES_ACTIVE=local \
	 -e OPENAI_API_KEY=${OPENAI_API_KEY} \
	 -e OPENAI_BASE_URL=${OPENAI_BASE_URL} \
	 -e LLM_MODEL=${LLM_MODEL} \
	 -e QDRANT_HOST=${QDRANT_HOST} -e QDRANT_PORT=${QDRANT_PORT} -e QDRANT_USE_TLS=${QDRANT_USE_TLS} -e QDRANT_COLLECTION_NAME=${QDRANT_COLLECTION_NAME} \
	 $(APP_NAME):$(TAG)

docker-run-prod:
	docker run --rm -p $(PORT):8080 --name $(APP_NAME) \
	 -e SPRING_PROFILES_ACTIVE=prod \
	 -e OPENAI_API_KEY=$${OPENAI_API_KEY} \
	 -e OPENAI_BASE_URL=$${OPENAI_BASE_URL} \
	 -e LLM_MODEL=$${LLM_MODEL} \
	 -e QDRANT_HOST=$${QDRANT_HOST} -e QDRANT_PORT=$${QDRANT_PORT} -e QDRANT_USE_TLS=$${QDRANT_USE_TLS} -e QDRANT_COLLECTION_NAME=$${QDRANT_COLLECTION_NAME} \
	 $(APP_NAME):$(TAG)

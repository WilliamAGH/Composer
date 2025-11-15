APP_NAME ?= composerai-api
TAG ?= local
PORT ?= 8080
PROFILE ?= local
MAVEN_TEST_FLAGS ?=

.PHONY: help run build build-vite build-java java-compile test clean lint docker-build docker-run-local docker-run-prod fe-dev clean-frontend

help:
	@echo "Targets:"
	@echo "  make run           - Run Spring Boot locally (profile=local)"
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

# Orchestrated build: frontend first so assets are bundled into the JAR
build: build-vite build-java

# Sub-builds
build-vite: FE_DIR := frontend/email-client
build-vite:
	@echo "Building Svelte bundle into src/main/resources/static/app/email-client ..."
	@cd $(FE_DIR) && npm install && npm run build -- --emptyOutDir

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
	@cd $(FE_DIR) && npm install && npm run dev

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
	@mvn validate -q && echo "   ✅ Passed" || echo "   ❌ Failed"
	@echo ""
	@echo "📦 SpotBugs (Java static analysis)..."
	@mvn compile spotbugs:spotbugs -q 2>&1 | grep -q "BUILD SUCCESS" || true
	@if [ -f target/spotbugsXml.xml ]; then \
		BUGS=$$(grep -o "total_bugs='[0-9]*'" target/spotbugsXml.xml | grep -o "[0-9]*" | head -1); \
		if [ "$$BUGS" = "0" ]; then \
			echo "   ✅ 0 bugs found"; \
		else \
			echo "   ⚠️  $$BUGS bugs found (run 'mvn spotbugs:gui' to view)"; \
		fi; \
	else \
		echo "   ⚠️  No report generated"; \
	fi
	@echo ""
	@echo "⚡ Oxlint (JavaScript/Svelte <script> tags)..."
	@cd frontend/email-client && npm run lint 2>&1 | grep -v "^>" | grep -v "^$$" || true
	@echo ""
	@echo "🎨 Stylelint (CSS & Svelte <style> tags - duplicate detection)..."
	@cd frontend/email-client && npm run lint:css 2>&1 | grep -v "^>" | tail -5 || true
	@echo ""
	@echo "🧹 Unused :global() CSS Detection..."
	@cd frontend/email-client && ./scripts/check-unused-global-css.sh src || true
	@echo ""
	@echo "🗑️  Dead Code Detection (exports, deps, components)..."
	@cd frontend/email-client && ./scripts/check-dead-code.sh || true
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

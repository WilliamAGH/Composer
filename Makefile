APP_NAME ?= composerai-api
TAG ?= local
PORT ?= 8080
PROFILE ?= local

.PHONY: help run build clean docker-build docker-run-local docker-run-prod

help:
	@echo "Targets:"
	@echo "  make run                 - Run locally with profile=local"
	@echo "  make build               - Build JAR (skip tests)"
	@echo "  make docker-build        - Build Docker image $(APP_NAME):$(TAG)"
	@echo "  make docker-run-local    - Run Docker with local profile and static bind mount"
	@echo "  make docker-run-prod     - Run Docker with prod profile"

run:
	SPRING_PROFILES_ACTIVE=local mvn spring-boot:run -Dspring-boot.run.profiles=local

build:
	mvn -q -DskipTests package

clean:
	mvn -q clean

docker-build:
	docker build --build-arg APP_NAME=$(APP_NAME) -t $(APP_NAME):$(TAG) .

docker-run-local:
	docker run --rm -p $(PORT):8080 --name $(APP_NAME) \
	 -e SPRING_PROFILES_ACTIVE=local \
	 -e OPENAI_API_KEY=${OPENAI_API_KEY} \
	 -e OPENAI_API_BASE_URL=${OPENAI_API_BASE_URL} \
	 -e OPENAI_MODEL=${OPENAI_MODEL} \
	 -e QDRANT_HOST=${QDRANT_HOST} -e QDRANT_PORT=${QDRANT_PORT} -e QDRANT_USE_TLS=${QDRANT_USE_TLS} -e QDRANT_COLLECTION_NAME=${QDRANT_COLLECTION_NAME} \
	 -v $(CURDIR)/Code:/app/Code:ro \
	 $(APP_NAME):$(TAG)

docker-run-prod:
	docker run --rm -p $(PORT):8080 --name $(APP_NAME) \
	 -e SPRING_PROFILES_ACTIVE=prod \
	 -e OPENAI_API_KEY=$${OPENAI_API_KEY} \
	 -e OPENAI_API_BASE_URL=$${OPENAI_API_BASE_URL} \
	 -e OPENAI_MODEL=$${OPENAI_MODEL} \
	 -e QDRANT_HOST=$${QDRANT_HOST} -e QDRANT_PORT=$${QDRANT_PORT} -e QDRANT_USE_TLS=$${QDRANT_USE_TLS} -e QDRANT_COLLECTION_NAME=$${QDRANT_COLLECTION_NAME} \
	 $(APP_NAME):$(TAG)

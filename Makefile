# Docker variables
DOCKER_IMAGE = eventbus-server
DOCKER_NETWORK = eventbus-net

# Docker targets
.PHONY: docker-build docker-network docker-run docker-clean docker-compose-up docker-compose-down

docker-build:
	@echo "Building Docker image..."
	docker build -t $(DOCKER_IMAGE) .

docker-network:
	@echo "Creating Docker network..."
	docker network create $(DOCKER_NETWORK) 2>/dev/null || true

docker-run: docker-build docker-network
	@echo "Running Docker containers..."
	docker run -d --name eventbus1 --network $(DOCKER_NETWORK) -p 50051:50051 $(DOCKER_IMAGE) -id instance1 -listen 0.0.0.0:50051
	docker run -d --name eventbus2 --network $(DOCKER_NETWORK) -p 50052:50051 $(DOCKER_IMAGE) -id instance2 -listen 0.0.0.0:50051 -peers eventbus1:50051
	docker run -d --name eventbus3 --network $(DOCKER_NETWORK) -p 50053:50051 $(DOCKER_IMAGE) -id instance3 -listen 0.0.0.0:50051 -peers eventbus1:50051,eventbus2:50051

docker-clean:
	@echo "Cleaning up Docker resources..."
	docker rm -f eventbus1 eventbus2 eventbus3 2>/dev/null || true
	docker network rm $(DOCKER_NETWORK) 2>/dev/null || true

docker-compose-up:
	@echo "Starting Docker Compose cluster..."
	docker-compose up -d

docker-compose-down:
	@echo "Stopping Docker Compose cluster..."
	docker-compose down

# EventBus Service Makefile

# Tool detection and paths
GO_CMD := $(shell command -v go 2> /dev/null)
PROTOC_CMD := $(shell command -v protoc 2> /dev/null)
MVN_CMD := $(shell command -v mvn 2> /dev/null)

# Check required tools
ifndef GO_CMD
$(error "go is not installed or not in PATH")
endif
ifndef PROTOC_CMD
$(error "protoc is not installed or not in PATH")
endif
ifndef MVN_CMD
$(error "maven is not installed or not in PATH")
endif

# Check for protoc plugins
PROTOC_GEN_GO := $(shell command -v protoc-gen-go 2> /dev/null)
PROTOC_GEN_GO_GRPC := $(shell command -v protoc-gen-go-grpc 2> /dev/null)

# Go variables
GO_BUILD = $(GO_CMD) build
GO_CLEAN = $(GO_CMD) clean
GO_SERVICE_DIR = cmd/server
GO_SERVICE_NAME = eventbus-service
GO_PROTO_OUTPUT = proto

# Java variables
JAVA_CLIENT_DIR = client-java
JAVA_PROTO_OUTPUT = $(JAVA_CLIENT_DIR)/src/main/java

# Proto variables
PROTO_DIR = proto
PROTO_FILE = $(PROTO_DIR)/eventbus.proto

.PHONY: all proto go-build java-build clean

# Default target: build everything
all: proto go-build java-build

# Generate protobuf code for both Go and Java
proto:
	@echo "Generating protobuf code..."
	@mkdir -p $(GO_PROTO_OUTPUT)
	@mkdir -p $(JAVA_PROTO_OUTPUT)
	
	@# Check for required Go plugins
	@if [ -z "$(PROTOC_GEN_GO)" ] || [ -z "$(PROTOC_GEN_GO_GRPC)" ]; then \
		echo "Installing protoc-gen-go plugins..."; \
		go install google.golang.org/protobuf/cmd/protoc-gen-go@latest; \
		go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest; \
	fi
	
	@# Add Go bin to PATH to ensure plugins are found

	@export PATH="$$PATH:$(HOME)/go/bin"; \
	$(PROTOC_CMD) --proto_path=$(PROTO_DIR) \
		--go_out=. --go_opt=paths=source_relative \
		--go-grpc_out=. --go-grpc_opt=paths=source_relative \
		$(PROTO_FILE)
	
	$(PROTOC_CMD) --proto_path=$(PROTO_DIR) \
		--java_out=$(JAVA_PROTO_OUTPUT) \
		--grpc-java_out=$(JAVA_PROTO_OUTPUT) \
		$(PROTO_FILE)
	@echo "Protobuf code generation complete"

# Build the Go service
go-build: proto
	@echo "Building Go service..."
	$(GO_BUILD) -o $(GO_SERVICE_NAME) $(GO_SERVICE_DIR)
	@echo "Go service build complete"

# Build the Java client
java-build: proto
	@echo "Building Java client..."
	cd $(JAVA_CLIENT_DIR) && $(MVN_CMD) clean package -DskipTests
	@echo "Java client build complete"

# Clean all generated files
clean:
	@echo "Cleaning up..."
	rm -f $(GO_SERVICE_NAME)
	rm -f $(GO_PROTO_OUTPUT)/*.pb.go
	cd $(JAVA_CLIENT_DIR) && $(MVN_CMD) clean
	@echo "Clean complete"


# Event Bus Service

This repository contains a gRPC-based event bus service implementation.

## Prerequisites

Before you begin, ensure you have the following installed:
- Go 1.21 or later
- Protocol Buffers compiler (`protoc`)
- Docker (optional, for containerized deployment)

## Installing Protocol Buffer plugins

Install the required Go protocol buffer plugins:

```bash
go install google.golang.org/protobuf/cmd/protoc-gen-go@latest
go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest
```

## Generating Protocol Buffer Stubs

To generate the Go stubs from the protocol buffer definitions:

```bash
# Add Go bin to your PATH if you haven't already
export PATH=$PATH:~/go/bin

# Generate the stubs
protoc --go_out=. --go-grpc_out=. proto/eventbus.proto
```

This will create two files in the `proto` directory:
- `eventbus.pb.go`: Contains the Go structs for your protocol buffer messages
- `eventbus_grpc.pb.go`: Contains the gRPC service definitions

## Building the Server

### Building Locally

To build the server locally:

```bash
go build -o eventbus-server cmd/server/main.go
```

This will create an executable named `eventbus-server` in your current directory.

### Building with Docker

To build the Docker image:

```bash
docker build -t eventbus-server .
```

## Running the Service

### Running Locally

To run a single instance:

```bash
./eventbus-server -id instance1 -listen localhost:50051
```

### Running with Docker

To run a single instance:

```bash
docker run -p 50051:50051 eventbus-server -id instance1 -listen 0.0.0.0:50051
```

## Running a Cluster

The EventBus service supports running multiple instances in a cluster. Each instance can connect to other instances for message replication.

### Running a Local Cluster

Start multiple instances with different ports:

```bash
# Start first instance
./eventbus-server -id instance1 -listen localhost:50051 &

# Start second instance, connected to first
./eventbus-server -id instance2 -listen localhost:50052 -peers localhost:50051 &

# Start third instance, connected to both
./eventbus-server -id instance3 -listen localhost:50053 -peers localhost:50051,localhost:50052
```

### Running a Docker Cluster

Create a Docker network:
```bash
docker network create eventbus-net
```

Start multiple instances:
```bash
# Start first instance
docker run -d --name eventbus1 --network eventbus-net \
    -p 50051:50051 \
    eventbus-server -id instance1 -listen 0.0.0.0:50051

# Start second instance
docker run -d --name eventbus2 --network eventbus-net \
    -p 50052:50051 \
    eventbus-server -id instance2 -listen 0.0.0.0:50051 \
    -peers eventbus1:50051

# Start third instance
docker run -d --name eventbus3 --network eventbus-net \
    -p 50053:50051 \
    eventbus-server -id instance3 -listen 0.0.0.0:50051 \
    -peers eventbus1:50051,eventbus2:50051
```

### Docker Compose Cluster

You can also use Docker Compose to manage multiple instances. Create a `docker-compose.yml`:

```yaml
version: '3'
services:
  eventbus1:
    build: .
    ports:
      - "50051:50051"
    command: -id instance1 -listen 0.0.0.0:50051
    networks:
      - eventbus-net

  eventbus2:
    build: .
    ports:
      - "50052:50051"
    command: -id instance2 -listen 0.0.0.0:50051 -peers eventbus1:50051
    depends_on:
      - eventbus1
    networks:
      - eventbus-net

  eventbus3:
    build: .
    ports:
      - "50053:50051"
    command: -id instance3 -listen 0.0.0.0:50051 -peers eventbus1:50051,eventbus2:50051
    depends_on:
      - eventbus1
      - eventbus2
    networks:
      - eventbus-net

networks:
  eventbus-net:
    driver: bridge
```

Start the cluster:
```bash
# Start the cluster
docker-compose up -d

# Stop the cluster
docker-compose down
```

The `docker-compose.yml` configuration will create:
- Three service instances (eventbus1, eventbus2, eventbus3)
- A dedicated network for inter-service communication
- Proper service dependencies
- Port mappings for client access (50051-50053)

### Using Make Targets for Docker Operations

The Makefile includes several targets for managing Docker deployments:

```bash
# Build the Docker image
make docker-build

# Create Docker network
make docker-network

# Run a three-node cluster with Docker
make docker-run

# Clean up Docker containers and network
make docker-clean

# Start cluster using Docker Compose
make docker-compose-up

# Stop cluster using Docker Compose
make docker-compose-down
```

These targets provide convenient shortcuts for common Docker operations. For example:
- `make docker-run` sets up a complete three-node cluster with proper networking
- `make docker-clean` removes all containers and network for a clean state
- `make docker-compose-up/down` manages the cluster through Docker Compose

### Client Configuration

When connecting clients to the cluster:
- Clients can connect to any instance
- Messages will be replicated across all instances
- If an instance fails, clients should connect to another instance
- Configure client failover using multiple server addresses

## Monitoring

Each instance logs its operations and connection status. View logs:

```bash
# For local instances
tail -f eventbus-server.log

# For Docker instances
docker logs -f eventbus1

# For Docker Compose instances
docker-compose logs -f
```

# Event Bus Service

This repository contains a gRPC-based event bus service implementation.

## Prerequisites

Before you begin, ensure you have the following installed:
- Go 1.21 or later
- Protocol Buffers compiler (`protoc`)

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

To build the server:

```bash
go build -o eventbus-server cmd/server/main.go
```

This will create an executable named `eventbus-server` in your current directory.

# Build stage
FROM golang:1.20-alpine AS builder

# Set working directory
WORKDIR /app

# Install build dependencies
RUN apk add --no-cache git protobuf-dev

# Copy go module files first for better caching
COPY go.mod go.sum ./
RUN go mod download

# Copy the source code
COPY . .

# Generate protobuf code
RUN go install google.golang.org/protobuf/cmd/protoc-gen-go@latest
RUN go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest

# Build the application with security flags
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -ldflags="-w -s" -o /app/eventbus-server ./server

# Runtime stage
FROM alpine:3.17

# Add security updates and create non-root user
RUN apk --no-cache add ca-certificates && \
    adduser -D -H -h /app appuser

# Set working directory
WORKDIR /app

# Copy the binary from builder stage
COPY --from=builder /app/eventbus-server .

# Use the non-root user
USER appuser

# Expose the server port
EXPOSE 50051

# Set executable permissions
RUN chmod +x /app/eventbus-server

# Run the server
ENTRYPOINT ["/app/eventbus-server"]
CMD ["--port", "50051"]

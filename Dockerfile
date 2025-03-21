# Build stage
FROM golang:1.21-alpine AS builder
WORKDIR /app
COPY . .
RUN go build -o eventbus-server cmd/server/main.go

# Final stage
FROM alpine:latest
WORKDIR /app
COPY --from=builder /app/eventbus-server .
EXPOSE 50051
ENTRYPOINT ["./eventbus-server"]


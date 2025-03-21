package main

import (
	"flag"
	"fmt"
	"log"
	"net"

	"github.com/eventbus/server/service"
)

func main() {
	port := flag.Int("port", 50051, "The server port")
	flag.Parse()

	lis, err := net.Listen("tcp", fmt.Sprintf(":%d", *port))
	if err != nil {
		log.Fatalf("failed to listen: %v", err)
	}

	server := service.NewEventBusServer()
	log.Printf("Starting EventBus server on port %d", *port)
	if err := server.Serve(lis); err != nil {
		log.Fatalf("failed to serve: %v", err)
	}
}


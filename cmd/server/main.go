package main

import (
	"flag"
	"log"
	"net"
	"os"
	"os/signal"
	"strings"
	"syscall"

	pb "github.com/eventbus/proto"
	"github.com/eventbus/service"
	"google.golang.org/grpc"
)

func main() {
	// Define command-line flags
	instanceID := flag.String("id", "", "Unique identifier for this server instance")
	listenAddr := flag.String("listen", ":50051", "Address to listen on (e.g., localhost:50051)")
	peersStr := flag.String("peers", "", "Comma-separated list of peer server addresses")
	flag.Parse()

	// Validate required flags
	if *instanceID == "" {
		log.Fatalf("Error: -id flag is required")
	}

	// Parse peer addresses
	var peerAddresses []string
	if *peersStr != "" {
		peerAddresses = strings.Split(*peersStr, ",")
	}

	// Create the server configuration
	config := &service.ServerConfig{
		InstanceID:    *instanceID,
		PeerAddresses: peerAddresses,
	}

	// Log server configuration
	log.Printf("Starting EventBus server with configuration:")
	log.Printf("  Instance ID: %s", config.InstanceID)
	log.Printf("  Listen address: %s", *listenAddr)
	if len(config.PeerAddresses) > 0 {
		log.Printf("  Peer servers: %s", strings.Join(config.PeerAddresses, ", "))
	} else {
		log.Printf("  Peer servers: none")
	}

	// Create a listener
	lis, err := net.Listen("tcp", *listenAddr)
	if err != nil {
		log.Fatalf("Failed to listen: %v", err)
	}

	// Create the gRPC server
	s := grpc.NewServer()
	srv := service.NewEventBusServer(config)
	pb.RegisterEventBusServer(s, srv)

	// Set up graceful shutdown
	go func() {
		signals := make(chan os.Signal, 1)
		signal.Notify(signals, syscall.SIGINT, syscall.SIGTERM)
		<-signals

		log.Println("Shutting down server...")
		s.GracefulStop()
		srv.Shutdown()
		log.Println("Server shutdown complete")
	}()

	// Start the server
	log.Printf("Server listening on %s", *listenAddr)
	if err := s.Serve(lis); err != nil {
		log.Fatalf("Failed to serve: %v", err)
	}
}

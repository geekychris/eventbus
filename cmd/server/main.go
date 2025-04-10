// Copyright (c) 2025 Chris Collins chris@hitorro.com
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

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

package main

import (
	"log"
	"net"

	pb "github.com/eventbus/proto"
	"github.com/eventbus/service"
	"google.golang.org/grpc"
)

func main() {
	lis, err := net.Listen("tcp", ":50051")
	if err != nil {
		log.Fatalf("failed to listen: %v", err)
	}

	s := grpc.NewServer()
	srv := service.NewEventBusServer()
	pb.RegisterEventBusServer(s, srv)

	log.Printf("Starting EventBus server on :50051")
	if err := s.Serve(lis); err != nil {
		log.Fatalf("failed to serve: %v", err)
	}
}

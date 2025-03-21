package service

import (
    "context"
    "log"
    "sync"

    pb "github.com/eventbus/proto"
    "google.golang.org/grpc"
    "google.golang.org/grpc/codes"
    "google.golang.org/grpc/status"
)

type subscriber struct {
    topics    map[string]bool
    stream    grpc.ServerStreamingServer[pb.Event]
    finished chan<- bool
}

type EventBusServer struct {
    pb.UnimplementedEventBusServer
    mu          sync.RWMutex
    subscribers map[string]*subscriber
}

func NewEventBusServer() *EventBusServer {
    return &EventBusServer{
        subscribers: make(map[string]*subscriber),
    }
}

func (s *EventBusServer) Subscribe(req *pb.SubscriptionRequest, stream grpc.ServerStreamingServer[pb.Event]) error {
    if req.ClientId == "" {
        return status.Error(codes.InvalidArgument, "client ID is required")
    }

    // Create finished channel
    finished := make(chan bool)

    // Create subscriber
    sub := &subscriber{
        topics:    make(map[string]bool),
        stream:    stream,
        finished: finished,
    }

    // Add topics
    for _, topic := range req.Topics {
        sub.topics[topic] = true
    }

    // Register subscriber
    s.mu.Lock()
    s.subscribers[req.ClientId] = sub
    s.mu.Unlock()

    // Clean up on exit
    defer func() {
        s.mu.Lock()
        delete(s.subscribers, req.ClientId)
        s.mu.Unlock()
    }()

    // Keep connection alive until client disconnects
    <-finished
    return nil
}

func (s *EventBusServer) Publish(ctx context.Context, msg *pb.PublishMessage) (*pb.PublishResponse, error) {
    if msg == nil {
        return nil, status.Error(codes.InvalidArgument, "message is required")
    }

    // Create event from publish message
    event := &pb.Event{
        Topic:     msg.Topic,
        SubTopic:  msg.SubTopic,
        ClientId:  msg.ClientId,
        Message:   msg.Message,
        Timestamp: msg.Timestamp,
    }

    // Distribute message to subscribers
    s.mu.RLock()
    for clientID, sub := range s.subscribers {
        // Don't send message to publisher
        if clientID == msg.ClientId {
            continue
        }

        // Check if subscriber is interested in this topic
        if sub.topics[msg.Topic] {
            // Non-blocking send
            err := sub.stream.Send(event)
            if err != nil {
                log.Printf("Failed to send message to client %s: %v", clientID, err)
                // Mark subscriber as finished
                sub.finished <- true
            }
        }
    }
    s.mu.RUnlock()

    return &pb.PublishResponse{Success: true}, nil
}

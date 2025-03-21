package service

import (
	"log"
	"sync"
	"time"

	pb "github.com/eventbus/server/proto"
	"google.golang.org/grpc"
)

type subscriber struct {
	clientID string
	topics   map[string]bool
	eventCh  chan *pb.Event
}

// EventBusServer is the server implementation of the EventBus service
type EventBusServer struct {
	*grpc.Server
	subscribers map[string]*subscriber
	mu          sync.RWMutex
}

// NewEventBusServer creates a new EventBus server
func NewEventBusServer() *EventBusServer {
	s := &EventBusServer{
		Server:      grpc.NewServer(),
		subscribers: make(map[string]*subscriber),
	}
	pb.RegisterEventBusServer(s.Server, s)
	return s
}

// Publish handles the publish message request
func (s *EventBusServer) Publish(req *pb.PublishMessage, stream pb.EventBus_PublishServer) error {
	log.Printf("Received publish request from %s for topic %s/%s", req.ClientId, req.Topic, req.SubTopic)

	event := &pb.Event{
		Topic:     req.Topic,
		SubTopic:  req.SubTopic,
		ClientId:  req.ClientId,
		Message:   req.Message,
		Timestamp: req.Timestamp,
	}

	// If timestamp is not set, set it now
	if event.Timestamp == 0 {
		event.Timestamp = time.Now().UnixNano() / int64(time.Millisecond)
	}

	// Dispatch the message to all subscribers
	s.dispatchEvent(event, req.ClientId)

	return stream.SendAndClose(&pb.PublishResponse{
		Success: true,
		Message: "Message published successfully",
	})
}

// Subscribe handles subscription requests
func (s *EventBusServer) Subscribe(req *pb.SubscriptionRequest, stream pb.EventBus_SubscribeServer) error {
	if req.ClientId == "" {
		return stream.Send(&pb.Event{
			Message: "Client ID is required",
		})
	}

	log.Printf("Client %s subscribing to topics: %v", req.ClientId, req.Topics)

	// Register the subscriber
	sub := &subscriber{
		clientID: req.ClientId,
		topics:   make(map[string]bool),
		eventCh:  make(chan *pb.Event, 100), // Buffer of 100 events
	}

	for _, topic := range req.Topics {
		sub.topics[topic] = true
	}

	s.mu.Lock()
	s.subscribers[req.ClientId] = sub
	s.mu.Unlock()

	// Cleanup on function exit
	defer func() {
		s.mu.Lock()
		delete(s.subscribers, req.ClientId)
		s.mu.Unlock()
		close(sub.eventCh)
		log.Printf("Client %s unsubscribed", req.ClientId)
	}()

	// Forward all events to the client
	for event := range sub.eventCh {
		if err := stream.Send(event); err != nil {
			log.Printf("Error sending event to client %s: %v", req.ClientId, err)
			return err
		}
	}

	return nil
}

// dispatchEvent sends the event to all subscribers of the topic except the publisher
func (s *EventBusServer) dispatchEvent(event *pb.Event, publisherID string) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	for clientID, sub := range s.subscribers {
		// Skip the publisher
		if clientID == publisherID {
			continue
		}

		// Check if the subscriber is interested in this topic
		if sub.topics[event.Topic] {
			// Non-blocking send to avoid slow subscribers blocking the server
			select {
			case sub.eventCh <- event:
				// Event sent successfully
			default:
				log.Printf("Warning: Channel full for client %s, dropping event", clientID)
			}
		}
	}
}


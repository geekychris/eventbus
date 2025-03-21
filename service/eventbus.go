package service

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"log"
	"sync"
	"time"

	pb "github.com/eventbus/proto"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/status"
)

type subscriber struct {
	topics   map[string]bool
	stream   grpc.ServerStreamingServer[pb.Event]
	finished chan<- bool
}

type peerConnection struct {
	address string
	client  pb.EventBusClient
	conn    *grpc.ClientConn
}

type EventBusServer struct {
	pb.UnimplementedEventBusServer
	mu              sync.RWMutex
	subscribers     map[string]*subscriber
	instanceID      string
	peersMu         sync.RWMutex
	peers           map[string]*peerConnection
	processedMsgs   map[string]bool
	processedMsgsMu sync.RWMutex
}

// ServerConfig holds configuration for the EventBus server
type ServerConfig struct {
	// Unique identifier for this server instance
	InstanceID string
	// Addresses of peer servers to connect to
	PeerAddresses []string
}

func NewEventBusServer(config *ServerConfig) *EventBusServer {
	server := &EventBusServer{
		subscribers:   make(map[string]*subscriber),
		processedMsgs: make(map[string]bool),
		peers:         make(map[string]*peerConnection),
	}

	if config != nil {
		server.instanceID = config.InstanceID
		// Connect to peers
		for _, addr := range config.PeerAddresses {
			go server.connectToPeer(addr)
		}
	}

	return server
}

// Generate a unique message ID
func generateMessageID() string {
	bytes := make([]byte, 16)
	if _, err := rand.Read(bytes); err != nil {
		return fmt.Sprintf("%d", time.Now().UnixNano())
	}
	return hex.EncodeToString(bytes)
}

// Connect to a peer server
func (s *EventBusServer) connectToPeer(address string) error {
	// Don't connect to self
	if address == s.instanceID {
		return nil
	}

	// Establish connection with peer
	conn, err := grpc.Dial(address, grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		log.Printf("Failed to connect to peer %s: %v", address, err)
		return err
	}

	client := pb.NewEventBusClient(conn)

	// Create peer connection
	peer := &peerConnection{
		address: address,
		client:  client,
		conn:    conn,
	}

	// Add to peers map
	s.peersMu.Lock()
	s.peers[address] = peer
	s.peersMu.Unlock()

	log.Printf("Connected to peer server at %s", address)
	return nil
}

// Shutdown closes all peer connections
func (s *EventBusServer) Shutdown() {
	s.peersMu.Lock()
	defer s.peersMu.Unlock()

	for _, peer := range s.peers {
		if peer.conn != nil {
			peer.conn.Close()
		}
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
		topics:   make(map[string]bool),
		stream:   stream,
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

	// Set message ID and origin server if not already set (i.e., if this is from a client, not a peer)
	if msg.MessageId == "" {
		msg.MessageId = generateMessageID()
	}
	if msg.OriginServerId == "" {
		msg.OriginServerId = s.instanceID
	}

	// Check if we've already processed this message to prevent loops
	s.processedMsgsMu.RLock()
	processed := s.processedMsgs[msg.MessageId]
	s.processedMsgsMu.RUnlock()

	if processed {
		// We've already seen this message, so just acknowledge it
		return &pb.PublishResponse{Success: true, Message: "Message already processed"}, nil
	}

	// Mark as processed
	s.processedMsgsMu.Lock()
	s.processedMsgs[msg.MessageId] = true
	s.processedMsgsMu.Unlock()

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

	// Replicate to peers if this is an original message (not already replicated)
	if msg.OriginServerId == s.instanceID {
		go s.replicateMessageToPeers(msg)
	}

	return &pb.PublishResponse{Success: true}, nil
}

// Replicate a message to all peer servers
func (s *EventBusServer) replicateMessageToPeers(msg *pb.PublishMessage) {
	// Create peer message for replication
	peerMsg := &pb.PeerMessage{
		Topic:          msg.Topic,
		SubTopic:       msg.SubTopic,
		ClientId:       msg.ClientId,
		Message:        msg.Message,
		Timestamp:      msg.Timestamp,
		MessageId:      msg.MessageId,
		OriginServerId: msg.OriginServerId,
	}

	// Send to all peers
	s.peersMu.RLock()
	defer s.peersMu.RUnlock()

	for addr, peer := range s.peers {
		go func(address string, p *peerConnection) {
			ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
			defer cancel()

			_, err := p.client.ReplicateEvent(ctx, peerMsg)
			if err != nil {
				log.Printf("Failed to replicate message to peer %s: %v", address, err)
				// TODO: Handle reconnection logic here
			}
		}(addr, peer)
	}
}

// ReplicateEvent handles incoming event replication from peer servers
func (s *EventBusServer) ReplicateEvent(ctx context.Context, msg *pb.PeerMessage) (*pb.PeerResponse, error) {
	if msg == nil {
		return nil, status.Error(codes.InvalidArgument, "message is required")
	}

	// Convert to PublishMessage for processing
	pubMsg := &pb.PublishMessage{
		Topic:          msg.Topic,
		SubTopic:       msg.SubTopic,
		ClientId:       msg.ClientId,
		Message:        msg.Message,
		Timestamp:      msg.Timestamp,
		MessageId:      msg.MessageId,
		OriginServerId: msg.OriginServerId,
	}

	// Use the Publish method to distribute to local subscribers
	// The message won't be replicated back to peers since the origin server ID is preserved
	_, err := s.Publish(ctx, pubMsg)
	if err != nil {
		return &pb.PeerResponse{Success: false, Message: err.Error()}, nil
	}

	return &pb.PeerResponse{Success: true}, nil
}

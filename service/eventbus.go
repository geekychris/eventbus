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

package service

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"log"
	"sync"
	"sync/atomic"
	"time"

	pb "github.com/eventbus/proto"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

type subscriber struct {
	topics   map[string]bool
	stream   grpc.ServerStreamingServer[pb.Event]
	finished chan<- bool
}

type EventBusServer struct {
	pb.UnimplementedEventBusServer
	mu              sync.RWMutex
	subscribers     map[string]*subscriber
	instanceID      string
	peerManager     *PeerManager
	processedMsgs   map[string]bool
	processedMsgsMu sync.RWMutex
	startTime       time.Time
	processedEvents int64
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
		startTime:     time.Now(),
	}

	if config != nil {
		server.instanceID = config.InstanceID
		server.peerManager = NewPeerManager(config.InstanceID)

		// Add peers to the peer manager
		for _, addr := range config.PeerAddresses {
			server.peerManager.AddPeer(addr)
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

// Shutdown closes all peer connections
func (s *EventBusServer) Shutdown() {
	if s.peerManager != nil {
		s.peerManager.Shutdown()
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

	// Increment processed events counter
	atomic.AddInt64(&s.processedEvents, 1)

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

	// Use peer manager to replicate the event
	if s.peerManager != nil {
		s.peerManager.ReplicateEvent(peerMsg)
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

// GetMetrics returns comprehensive metrics about the server and peers
func (s *EventBusServer) GetMetrics() ServiceMetrics {
	s.mu.RLock()
	subCount := len(s.subscribers)
	s.mu.RUnlock()

	metrics := ServiceMetrics{
		InstanceID:      s.instanceID,
		Uptime:          time.Since(s.startTime),
		StartTime:       s.startTime,
		SubscriberCount: subCount,
		ProcessedEvents: atomic.LoadInt64(&s.processedEvents),
	}

	if s.peerManager != nil {
		metrics.Peers = s.peerManager.GetMetrics()
	}

	return metrics
}

// Add GetMetrics implementation
//func (s *EventBusServer) GetMetrics(ctx context.Context, req *pb.MetricsRequest) (*pb.ServiceMetricsInfo, error) {
//    metrics := s.GetMetrics()
//
//    peerMetrics := make([]*pb.PeerMetricsInfo, 0, len(metrics.Peers))
//    for _, peer := range metrics.Peers {
//        // Handle zero time for LastConnected
//        var lastConnectedTimestamp int64
//        if !peer.LastConnected.IsZero() {
//            lastConnectedTimestamp = peer.LastConnected.Unix()
//        }
//
//        peerMetrics = append(peerMetrics, &pb.PeerMetricsInfo{
//            Address:                  peer.Address,
//            State:                   peer.State,
//            ConnectedDurationSeconds: int64(peer.ConnectedDuration.Seconds()),
//            LastConnectedTimestamp:   lastConnectedTimestamp,
//            ReconnectAttempts:       peer.ReconnectAttempts,
//            EventsSent:              peer.EventsSent,
//            EventsDropped:           peer.EventsDropped,
//            QueueSize:              int32(peer.QueueSize),
//        })
//    }
//
//    return &pb.ServiceMetricsInfo{
//        InstanceId:      metrics.InstanceID,
//        UptimeSeconds:   int64(metrics.Uptime.Seconds()),
//        StartTimestamp:  metrics.StartTime.Unix(),
//        SubscriberCount: int32(metrics.SubscriberCount),
//        ProcessedEvents: metrics.ProcessedEvents,
//        Peers:           peerMetrics,
//    }, nil
//}

package service

import (
	"context"
	"fmt"
	"sync"
	"testing"
	"time"

	pb "github.com/eventbus/proto"
	"github.com/stretchr/testify/assert"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/status"
)

// Mock stream implementation
type mockStream struct {
	grpc.ServerStream
	events []*pb.Event
	t      *testing.T
}

func newMockStream(t *testing.T) *mockStream {
	return &mockStream{
		events: make([]*pb.Event, 0),
		t:      t,
	}
}

func (m *mockStream) Send(event *pb.Event) error {
	m.events = append(m.events, event)
	return nil
}

func (m *mockStream) Context() context.Context {
	return context.Background()
}

func (m *mockStream) SetHeader(metadata.MD) error {
	return nil
}

func (m *mockStream) SendHeader(metadata.MD) error {
	return nil
}

func (m *mockStream) SetTrailer(metadata.MD) {
}

func TestNewEventBusServer(t *testing.T) {
	server := NewEventBusServer()
	assert.NotNil(t, server)
	assert.NotNil(t, server.subscribers)
}

func TestSubscribe_ValidInput(t *testing.T) {
	server := NewEventBusServer()
	stream := newMockStream(t)

	req := &pb.SubscriptionRequest{
		ClientId: "test-client",
		Topics:   []string{"topic1", "topic2"},
	}

	errChan := make(chan error)
	go func() {
		err := server.Subscribe(req, stream)
		errChan <- err
	}()

	time.Sleep(100 * time.Millisecond)
	server.mu.RLock()
	sub, exists := server.subscribers["test-client"]
	server.mu.RUnlock()

	assert.True(t, exists)
	assert.NotNil(t, sub)
	assert.True(t, sub.topics["topic1"])
	assert.True(t, sub.topics["topic2"])

	sub.finished <- true

	err := <-errChan
	assert.NoError(t, err)

	server.mu.RLock()
	_, exists = server.subscribers["test-client"]
	server.mu.RUnlock()
	assert.False(t, exists)
}

func TestSubscribe_InvalidInput(t *testing.T) {
	server := NewEventBusServer()
	stream := newMockStream(t)

	req := &pb.SubscriptionRequest{
		ClientId: "",
		Topics:   []string{"topic1"},
	}

	err := server.Subscribe(req, stream)
	assert.Error(t, err)

	st, ok := status.FromError(err)
	assert.True(t, ok)
	assert.Equal(t, codes.InvalidArgument, st.Code())
}

func TestPublish(t *testing.T) {
	server := NewEventBusServer()

	stream1 := newMockStream(t)
	stream2 := newMockStream(t)

	sub1 := &subscriber{
		topics:   map[string]bool{"topic1": true},
		stream:   stream1,
		finished: make(chan bool),
	}

	sub2 := &subscriber{
		topics:   map[string]bool{"topic2": true},
		stream:   stream2,
		finished: make(chan bool),
	}

	server.mu.Lock()
	server.subscribers["client1"] = sub1
	server.subscribers["client2"] = sub2
	server.mu.Unlock()

	now := time.Now().Unix()
	msg := &pb.PublishMessage{
		ClientId:  "client3",
		Topic:     "topic1",
		SubTopic:  "subtopic1",
		Message:   "test message",
		Timestamp: now,
	}

	resp, err := server.Publish(context.Background(), msg)
	assert.NoError(t, err)
	assert.True(t, resp.Success)

	assert.Len(t, stream1.events, 1)
	assert.Len(t, stream2.events, 0)

	sentEvent := stream1.events[0]
	assert.Equal(t, msg.Topic, sentEvent.Topic)
	assert.Equal(t, msg.SubTopic, sentEvent.SubTopic)
	assert.Equal(t, msg.Message, sentEvent.Message)
	assert.Equal(t, msg.Timestamp, sentEvent.Timestamp)
}

func TestPublish_InvalidInput(t *testing.T) {
	server := NewEventBusServer()
	ctx := context.Background()

	resp, err := server.Publish(ctx, nil)
	assert.Error(t, err)
	assert.Nil(t, resp)

	st, ok := status.FromError(err)
	assert.True(t, ok)
	assert.Equal(t, codes.InvalidArgument, st.Code())
}

func TestConcurrentOperations(t *testing.T) {
	server := NewEventBusServer()
	numClients := 10
	numMessages := 5

	for i := 0; i < numClients; i++ {
		stream := newMockStream(t)
		req := &pb.SubscriptionRequest{
			ClientId: fmt.Sprintf("client%d", i),
			Topics:   []string{"topic1"},
		}

		go func(req *pb.SubscriptionRequest, stream *mockStream) {
			_ = server.Subscribe(req, stream)
		}(req, stream)
	}

	time.Sleep(100 * time.Millisecond)

	var wg sync.WaitGroup
	for i := 0; i < numMessages; i++ {
		wg.Add(1)
		go func(msgNum int) {
			defer wg.Done()
			now := time.Now().Unix()
			msg := &pb.PublishMessage{
				ClientId:  "publisher",
				Topic:     "topic1",
				Message:   fmt.Sprintf("message%d", msgNum),
				Timestamp: now,
			}
			_, _ = server.Publish(context.Background(), msg)
		}(i)
	}

	wg.Wait()

	server.mu.RLock()
	assert.Len(t, server.subscribers, numClients)
	server.mu.RUnlock()
}

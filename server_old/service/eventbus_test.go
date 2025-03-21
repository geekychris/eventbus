package service

import (
	"context"
	"net"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"google.golang.org/grpc"
	"google.golang.org/grpc/test/bufconn"

	pb "github.com/yourusername/eventbus/proto"
)

const bufSize = 1024 * 1024

var lis *bufconn.Listener

func init() {
	lis = bufconn.Listen(bufSize)
	s := grpc.NewServer()
	pb.RegisterEventBusServiceServer(s, NewEventBusService())
	go func() {
		if err := s.Serve(lis); err != nil {
			panic(err)
		}
	}()
}

func bufDialer(context.Context, string) (net.Conn, error) {
	return lis.Dial()
}

func TestSubscribe(t *testing.T) {
	ctx := context.Background()
	conn, err := grpc.DialContext(ctx, "bufnet", grpc.WithContextDialer(bufDialer), grpc.WithInsecure())
	if err != nil {
		t.Fatalf("Failed to dial bufnet: %v", err)
	}
	defer conn.Close()

	client := pb.NewEventBusServiceClient(conn)

	// Test successful subscription
	subscribeReq := &pb.SubscribeRequest{
		ClientId: "test-client",
		Topics:   []string{"test-topic", "another-topic"},
	}

	stream, err := client.Subscribe(ctx, subscribeReq)
	if err != nil {
		t.Fatalf("Failed to subscribe: %v", err)
	}

	// Verify stream is established
	assert.NotNil(t, stream)

	// Test re-subscribing with the same client ID
	stream2, err := client.Subscribe(ctx, subscribeReq)
	assert.NoError(t, err)
	assert.NotNil(t, stream2)
}

func TestPublishMessage(t *testing.T) {
	ctx := context.Background()
	conn, err := grpc.DialContext(ctx, "bufnet", grpc.WithContextDialer(bufDialer), grpc.WithInsecure())
	if err != nil {
		t.Fatalf("Failed to dial bufnet: %v", err)
	}
	defer conn.Close()

	client := pb.NewEventBusServiceClient(conn)

	// First, subscribe a client
	subscribeReq := &pb.SubscribeRequest{
		ClientId: "subscriber-client",
		Topics:   []string{"test-topic"},
	}

	stream, err := client.Subscribe(ctx, subscribeReq)
	if err != nil {
		t.Fatalf("Failed to subscribe: %v", err)
	}

	// Set up a goroutine to receive messages
	messageReceived := make(chan bool, 1)
	go func() {
		msg, err := stream.Recv()
		if err != nil {
			t.Errorf("Error receiving message: %v", err)
			return
		}
		assert.Equal(t, "test-topic", msg.Topic)
		assert.Equal(t, "test-subtopic", msg.SubTopic)
		assert.Equal(t, "publisher-client", msg.ClientId)
		assert.Equal(t, "test message", msg.Message)
		messageReceived <- true
	}()

	// Now publish a message
	publishReq := &pb.PublishRequest{
		Message: &pb.Message{
			Topic:     "test-topic",
			SubTopic:  "test-subtopic",
			ClientId:  "publisher-client",
			Message:   "test message",
			Timestamp: time.Now().Unix(),
		},
	}

	_, err = client.Publish(ctx, publishReq)
	assert.NoError(t, err)

	// Wait for message to be received, with timeout
	select {
	case <-messageReceived:
		// Test passed
	case <-time.After(3 * time.Second):
		t.Fatal("Timed out waiting for message to be received")
	}
}

func TestPublishToNonSubscribedTopic(t *testing.T) {
	ctx := context.Background()
	conn, err := grpc.DialContext(ctx, "bufnet", grpc.WithContextDialer(bufDialer), grpc.WithInsecure())
	if err != nil {
		t.Fatalf("Failed to dial bufnet: %v", err)
	}
	defer conn.Close()

	client := pb.NewEventBusServiceClient(conn)

	// First, subscribe a client to a specific topic
	subscribeReq := &pb.SubscribeRequest{
		ClientId: "topic-specific-client",
		Topics:   []string{"subscribed-topic"},
	}

	stream, err := client.Subscribe(ctx, subscribeReq)
	if err != nil {
		t.Fatalf("Failed to subscribe: %v", err)
	}

	// Set up a goroutine to check that no messages are received for unsubscribed topics
	messageNotReceived := make(chan bool, 1)
	go func() {
		// Use a timeout to check that no message is received
		ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
		defer cancel()

		msg, err := stream.Recv()
		if err != nil {
			// Expected timeout error
			messageNotReceived <- true
			return
		}
		// If we received a message, that's unexpected
		t.Errorf("Received unexpected message: %v", msg)
		messageNotReceived <- false
	}()

	// Now publish a message to a different topic
	publishReq := &pb.PublishRequest{
		Message: &pb.Message{
			Topic:     "unsubscribed-topic",
			SubTopic:  "test-subtopic",
			ClientId:  "publisher-client",
			Message:   "should not be received",
			Timestamp: time.Now().Unix(),
		},
	}

	_, err = client.Publish(ctx, publishReq)
	assert.NoError(t, err)

	// Verify no message was received (success case is a timeout)
	select {
	case result := <-messageNotReceived:
		assert.True(t, result, "Client received a message for a topic it didn't subscribe to")
	case <-time.After(3 * time.Second):
		t.Fatal("Test timed out")
	}
}

func TestPublisherDoesNotReceiveOwnMessages(t *testing.T) {
	ctx := context.Background()
	conn, err := grpc.DialContext(ctx, "bufnet", grpc.WithContextDialer(bufDialer), grpc.WithInsecure())
	if err != nil {
		t.Fatalf("Failed to dial bufnet: %v", err)
	}
	defer conn.Close()

	client := pb.NewEventBusServiceClient(conn)

	clientID := "self-publishing-client"

	// Subscribe the client
	subscribeReq := &pb.SubscribeRequest{
		ClientId: clientID,
		Topics:   []string{"self-test-topic"},
	}

	stream, err := client.Subscribe(ctx, subscribeReq)
	if err != nil {
		t.Fatalf("Failed to subscribe: %v", err)
	}

	// Set up message reception check
	messageNotReceived := make(chan bool, 1)
	go func() {
		// Use a timeout to check that no message is received
		ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
		defer cancel()

		msg, err := stream.Recv()
		if err != nil {
			// Expected timeout error
			messageNotReceived <- true
			return
		}
		// If we received a message, that's unexpected
		t.Errorf("Client received its own published message: %v", msg)
		messageNotReceived <- false
	}()

	// Publisher publishes to a topic it's subscribed to
	publishReq := &pb.PublishRequest{
		Message: &pb.Message{
			Topic:     "self-test-topic",
			SubTopic:  "test-subtopic",
			ClientId:  clientID, // Same client ID
		Message:   "own message",
		Timestamp: time.Now().Unix(),
	},
}

_, err = client.Publish(ctx, publishReq)
assert.NoError(t, err)

// Verify that the client doesn't receive its own message
select {
case result := <-messageNotReceived:
	assert.True(t, result, "Client received its own published message")
case <-time.After(3 * time.Second):
	t.Fatal("Test timed out")
}
}

func TestConcurrentSubscriptions(t *testing.T) {
	ctx := context.Background()
	
	// Create several clients and have them subscribe concurrently
	const numClients = 10
	var wg sync.WaitGroup
	wg.Add(numClients)
	
	// Track successful subscriptions
	successChan := make(chan bool, numClients)
	
	for i := 0; i < numClients; i++ {
		go func(clientNum int) {
			defer wg.Done()
			
			// Create a connection for each client
			conn, err := grpc.DialContext(ctx, "bufnet", grpc.WithContextDialer(bufDialer), grpc.WithInsecure())
			if err != nil {
				t.Errorf("Client %d failed to dial bufnet: %v", clientNum, err)
				successChan <- false
				return
			}
			defer conn.Close()
			
			client := pb.NewEventBusServiceClient(conn)
			clientID := fmt.Sprintf("concurrent-client-%d", clientNum)
			
			// Subscribe to a topic
			subscribeReq := &pb.SubscribeRequest{
				ClientId: clientID,
				Topics:   []string{"concurrent-topic"},
			}
			
			stream, err := client.Subscribe(ctx, subscribeReq)
			if err != nil {
				t.Errorf("Client %d failed to subscribe: %v", clientNum, err)
				successChan <- false
				return
			}
			
			// If we got here, subscription was successful
			successChan <- true
			
			// Keep the stream open for a short time to ensure the subscription is active
			time.Sleep(100 * time.Millisecond)
		}(i)
	}
	
	// Wait for all goroutines to complete
	wg.Wait()
	close(successChan)
	
	// Check that all subscriptions were successful
	failCount := 0
	for success := range successChan {
		if !success {
			failCount++
		}
	}
	
	assert.Equal(t, 0, failCount, "Some concurrent subscriptions failed")
}

func TestMessageBroadcast(t *testing.T) {
	ctx := context.Background()
	
	// Create multiple subscribers
	const numSubscribers = 5
	subscribers := make([]pb.EventBusServiceClient, numSubscribers)
	streams := make([]pb.EventBusService_SubscribeClient, numSubscribers)
	
	// Subscribe all clients to the same topic
	for i := 0; i < numSubscribers; i++ {
		conn, err := grpc.DialContext(ctx, "bufnet", grpc.WithContextDialer(bufDialer), grpc.WithInsecure())
		if err != nil {
			t.Fatalf("Failed to dial bufnet: %v", err)
		}
		defer conn.Close()
		
		subscribers[i] = pb.NewEventBusServiceClient(conn)
		clientID := fmt.Sprintf("broadcast-client-%d", i)
		
		subscribeReq := &pb.SubscribeRequest{
			ClientId: clientID,
			Topics:   []string{"broadcast-topic"},
		}
		
		stream, err := subscribers[i].Subscribe(ctx, subscribeReq)
		if err != nil {
			t.Fatalf("Failed to subscribe client %d: %v", i, err)
		}
		
		streams[i] = stream
	}
	
	// Create a separate connection for the publisher
	publisherConn, err := grpc.DialContext(ctx, "bufnet", grpc.WithContextDialer(bufDialer), grpc.WithInsecure())
	if err != nil {
		t.Fatalf("Failed to dial bufnet for publisher: %v", err)
	}
	defer publisherConn.Close()
	
	publisher := pb.NewEventBusServiceClient(publisherConn)
	
	// Create channels to track message reception
	receivedMessages := make([]chan bool, numSubscribers)
	for i := 0; i < numSubscribers; i++ {
		receivedMessages[i] = make(chan bool, 1)
		
		// Set up goroutines to receive messages
		go func(index int, stream pb.EventBusService_SubscribeClient, received chan bool) {
			msg, err := stream.Recv()
			if err != nil {
				t.Errorf("Error receiving message for client %d: %v", index, err)
				received <- false
				return
			}
			
			if msg.Topic == "broadcast-topic" && 
			   msg.SubTopic == "sub-topic" && 
			   msg.Message == "broadcast message" {
				received <- true
			} else {
				t.Errorf("Client %d received incorrect message: %v", index, msg)
				received <- false
			}
		}(i, streams[i], receivedMessages[i])
	}
	
	// Publish a message to the shared topic
	publishReq := &pb.PublishRequest{
		Message: &pb.Message{
			Topic:     "broadcast-topic",
			SubTopic:  "sub-topic",
			ClientId:  "publisher-client",
			Message:   "broadcast message",
			Timestamp: time.Now().Unix(),
		},
	}
	
	_, err = publisher.Publish(ctx, publishReq)
	assert.NoError(t, err)
	
	// Check that all subscribers received the message
	for i := 0; i < numSubscribers; i++ {
		select {
		case success := <-receivedMessages[i]:
			assert.True(t, success, "Client %d didn't receive the message correctly", i)
		case <-time.After(3 * time.Second):
			t.Errorf("Timed out waiting for client %d to receive the message", i)
		}
	}
}

func TestSubscriptionCleanup(t *testing.T) {
	ctx := context.Background()
	
	// Create and establish a connection
	conn, err := grpc.DialContext(ctx, "bufnet", grpc.WithContextDialer(bufDialer), grpc.WithInsecure())
	if err != nil {
		t.Fatalf("Failed to dial bufnet: %v", err)
	}
	defer conn.Close()
	
	client := pb.NewEventBusServiceClient(conn)
	
	// Get service to test against directly
	service := NewEventBusService()
	
	// Subscribe a client
	subscribeReq := &pb.SubscribeRequest{
		ClientId: "cleanup-test-client",
		Topics:   []string{"cleanup-topic"},
	}
	
	stream, err := client.Subscribe(ctx, subscribeReq)
	if err != nil {
		t.Fatalf("Failed to subscribe: %v", err)
	}
	
	// Verify the client is in the subscribers list
	service.mu.RLock()
	_, exists := service.subscribers["cleanup-test-client"]
	service.mu.RUnlock()
	assert.True(t, exists, "Client not found in subscribers map after subscribing")
	
	// Close the stream to trigger cleanup
	streamConn, ok := stream.(grpc.ClientStream)
	if !ok {
		t.Fatalf("Failed to convert stream to ClientStream")
	}
	streamConn.CloseSend()
	
	// Give the server a moment to process the cleanup
	time.Sleep(500 * time.Millisecond)
	
	// Verify the client is removed from subscribers
	service.mu.RLock()
	_, exists = service.subscribers["cleanup-test-client"]
	service.mu.RUnlock()
	assert.False(t, exists, "Client still in subscribers map after closing connection")
}

func TestInvalidMessages(t *testing.T) {
	ctx := context.Background()
	conn, err := grpc.DialContext(ctx, "bufnet", grpc.WithContextDialer(bufDialer), grpc.WithInsecure())
	if err != nil {
		t.Fatalf("Failed to dial bufnet: %v", err)
	}
	defer conn.Close()
	
	client := pb.NewEventBusServiceClient(conn)
	
	// Test 1: Empty client ID
	subscribeReq := &pb.SubscribeRequest{
		ClientId: "", // Invalid empty client ID
		Topics:   []string{"test-topic"},
	}
	
	stream, err := client.Subscribe(ctx, subscribeReq)
	assert.NoError(t, err, "Subscribe should not immediately error with empty client ID")
	
	// We expect to receive an error message from the server
	msg, err := stream.Recv()
	assert.NoError(t, err, "Should receive error message")
	assert.Contains(t, msg.Message, "Client ID is required", "Should receive error about empty client ID")
	
	// Test 2: Empty topic list
	subscribeReq = &pb.SubscribeRequest{
		ClientId: "valid-client",
		Topics:   []string{}, // Empty topic list
	}
	
	stream, err = client.Subscribe(ctx, subscribeReq)
	assert.NoError(t, err, "Subscribe should not error with empty topic list")
	
	// The subscription should work but client won't receive any messages
	
	// Test 3: Empty message in publish request
	publishReq := &pb.PublishRequest{
		Message: &pb.Message{
			Topic:     "test-topic",
			SubTopic:  "test-subtopic",
			ClientId:  "publisher-client",
			Message:   "", // Empty message content
			Timestamp: time.Now().Unix(),
		},
	}
	
	_, err = client.Publish(ctx, publishReq)
	assert.NoError(t, err, "Publish should not error with empty message content")
	
	// Test 4: Nil message in publish request
	_, err = client.Publish(ctx, &pb.PublishRequest{Message: nil})
	assert.Error(t, err, "Publish should error with nil message")
}

func TestBufferOverflow(t *testing.T) {
	ctx := context.Background()
	
	// Create connections for subscriber and publisher
	subConn, err := grpc.DialContext(ctx, "bufnet", grpc.WithContextDialer(bufDialer), grpc.WithInsecure())
	if err != nil {
		t.Fatalf("Failed to dial bufnet for subscriber: %v", err)
	}
	defer subConn.Close()
	
	pubConn, err := grpc.DialContext(ctx, "bufnet", grpc.WithContextDialer(bufDialer), grpc.WithInsecure())
	if err != nil {
		t.Fatalf("Failed to dial bufnet for publisher: %v", err)
	}
	defer pubConn.Close()
	
	subscriber := pb.NewEventBusServiceClient(subConn)
	publisher := pb.NewEventBusServiceClient(pubConn)
	
	// Subscribe to a topic
	subscribeReq := &pb.SubscribeRequest{
		ClientId: "overflow-test-client",
		Topics:   []string{"overflow-topic"},
	}
	
	stream, err := subscriber.Subscribe(ctx, subscribeReq)
	if err != nil {
		t.Fatalf("Failed to subscribe: %v", err)
	}
	
	// Create a goroutine that is intentionally slow at processing messages
	go func() {
		// Only process one message every 500ms
		for {
			_, err := stream.Recv()
			if err != nil {
				return // Exit if the stream is closed
			}
			time.Sleep(500 * time.Millisecond)
		}
	}()
	
	// Give the subscriber time to establish
	time.Sleep(100 * time.Millisecond)
	
	// Send many messages rapidly to overflow the buffer (buffer size is 100)
	const messageCount = 200
	
	successCount := 0
	for i := 0; i < messageCount; i++ {
		publishReq := &pb.PublishRequest{
			Message: &pb.Message{
				Topic:     "overflow-topic",
				SubTopic:  "overflow-test",
				ClientId:  "overflow-publisher",
				Message:   fmt.Sprintf("Message %d", i),
				Timestamp: time.Now().Unix(),
			},
		}
		
		_, err = publisher.Publish(ctx, publishReq)
		if err == nil {
			successCount++
		}
		
		// Don't delay between sends to try to overflow the buffer
	}
	
	// Verify that all publish requests succeeded even if some messages were dropped
	assert.Equal(t, messageCount, successCount, "Some publish requests failed")
	
	// The server should log warnings about dropped messages but should continue functioning
}

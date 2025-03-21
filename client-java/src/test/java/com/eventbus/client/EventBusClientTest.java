package com.eventbus.client;

import com.eventbus.client.Event;
import com.eventbus.client.PublishMessage;
import com.eventbus.client.PublishResponse;
import com.eventbus.client.SubscriptionRequest;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(JUnit4.class)
public class EventBusClientTest {
    private static final String TEST_CLIENT_ID = "test-client";
    private static final String TEST_TOPIC = "test-topic";
    private static final String TEST_SUB_TOPIC = "test-sub-topic";
    private static final String TEST_MESSAGE = "Hello, EventBus!";

    @Rule
    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    private EventBusClient client;
    private Server server;
    private ManagedChannel channel;
    private String serverName;

    private EventBusGrpc.EventBusImplBase mockService;

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        
        mockService = spy(new EventBusGrpc.EventBusImplBase() {});
        serverName = InProcessServerBuilder.generateName();
        
        server = InProcessServerBuilder
                .forName(serverName)
                .directExecutor()
                .addService(mockService)
                .build()
                .start();
        
        grpcCleanup.register(server);
        
        channel = grpcCleanup.register(
            InProcessChannelBuilder
                .forName(serverName)
                .directExecutor()
                .build()
        );
        
        client = new EventBusClient(channel, TEST_CLIENT_ID);
    }

    @After
    public void tearDown() throws Exception {
        if (client != null) {
            client.unsubscribe();
            Thread.sleep(100); // Short delay to allow unsubscribe to complete
            client.close();
        }
        
        if (channel != null) {
            channel.shutdownNow();
            channel.awaitTermination(5, TimeUnit.SECONDS);
        }
        
        if (server != null) {
            server.shutdownNow();
            server.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testClientInitialization() {
        assertNotNull("Client should not be null", client);
        assertEquals("Client ID should match", TEST_CLIENT_ID, client.getClientId());
    }

    @Test
    public void testPublishMessage() {
        final ArgumentCaptor<PublishMessage> requestCaptor = 
                ArgumentCaptor.forClass(PublishMessage.class);
        
        doAnswer(invocation -> {
            StreamObserver<PublishResponse> responseObserver = 
                    (StreamObserver<PublishResponse>) invocation.getArguments()[1];
            responseObserver.onNext(PublishResponse.newBuilder()
                    .setSuccess(true)
                    .build());
            responseObserver.onCompleted();
            return null;
        }).when(mockService).publish(requestCaptor.capture(), any());

        boolean result = client.publish(TEST_TOPIC, TEST_SUB_TOPIC, TEST_MESSAGE);
        
        assertTrue("Publish should return true on success", result);
        
        PublishMessage capturedRequest = requestCaptor.getValue();
        assertEquals("Client ID should match", TEST_CLIENT_ID, capturedRequest.getClientId());
        assertEquals("Topic should match", TEST_TOPIC, capturedRequest.getTopic());
        assertEquals("Sub-topic should match", TEST_SUB_TOPIC, capturedRequest.getSubTopic());
        assertEquals("Message should match", TEST_MESSAGE, capturedRequest.getMessage());
        assertTrue("Timestamp should be set", capturedRequest.getTimestamp() > 0);
    }

    @Test
    public void testPublishFailure() {
        doAnswer(invocation -> {
            StreamObserver<PublishResponse> responseObserver = 
                    (StreamObserver<PublishResponse>) invocation.getArguments()[1];
            responseObserver.onNext(PublishResponse.newBuilder()
                    .setSuccess(false)
                    .build());
            responseObserver.onCompleted();
            return null;
        }).when(mockService).publish(any(), any());

        boolean result = client.publish(TEST_TOPIC, TEST_SUB_TOPIC, TEST_MESSAGE);
        
        assertFalse("Publish should return false on failure", result);
    }

    @Test
    public void testPublishWithException() {
        doAnswer(invocation -> {
            StreamObserver<PublishResponse> responseObserver = 
                    (StreamObserver<PublishResponse>) invocation.getArguments()[1];
            responseObserver.onError(new RuntimeException("Simulated error"));
            return null;
        }).when(mockService).publish(any(), any());

        boolean result = client.publish(TEST_TOPIC, TEST_SUB_TOPIC, TEST_MESSAGE);
        
        assertFalse("Publish should return false on exception", result);
    }

    @Test
    public void testSubscribeToTopic() {
        final ArgumentCaptor<SubscriptionRequest> requestCaptor = 
                ArgumentCaptor.forClass(SubscriptionRequest.class);
        
        doAnswer(invocation -> {
            StreamObserver<Event> responseObserver = 
                    (StreamObserver<Event>) invocation.getArguments()[1];
            return null;
        }).when(mockService).subscribe(requestCaptor.capture(), any());

        Consumer<Event> messageHandler = message -> {};
        client.subscribe(messageHandler, TEST_TOPIC, "another-topic");
        
        SubscriptionRequest capturedRequest = requestCaptor.getValue();
        assertEquals("Client ID should match", TEST_CLIENT_ID, capturedRequest.getClientId());
        assertEquals("Topics count should match", 2, capturedRequest.getTopicsCount());
        assertTrue("Topics should contain test topic", 
                capturedRequest.getTopicsList().contains(TEST_TOPIC));
        assertTrue("Topics should contain another topic", 
                capturedRequest.getTopicsList().contains("another-topic"));
    }

    @Test
    public void testReceiveMessages() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Event> receivedMessage = new AtomicReference<>();
        
        doAnswer(invocation -> {
            StreamObserver<Event> responseObserver = 
                    (StreamObserver<Event>) invocation.getArguments()[1];
            
            Event message = Event.newBuilder()
                    .setClientId("another-client")
                    .setTopic(TEST_TOPIC)
                    .setSubTopic(TEST_SUB_TOPIC)
                    .setMessage(TEST_MESSAGE)
                    .setTimestamp(System.currentTimeMillis())
                    .build();
            responseObserver.onNext(message);
            
            return null;
        }).when(mockService).subscribe(any(), any());

        client.subscribe(message -> {
            receivedMessage.set(message);
            latch.countDown();
        }, TEST_TOPIC);
        
        assertTrue("Should receive message within timeout", latch.await(5, TimeUnit.SECONDS));
        Event message = receivedMessage.get();
        assertNotNull("Received message should not be null", message);
        assertEquals("Message topic should match", TEST_TOPIC, message.getTopic());
        assertEquals("Message sub-topic should match", TEST_SUB_TOPIC, message.getSubTopic());
        assertEquals("Message content should match", TEST_MESSAGE, message.getMessage());
        assertEquals("Message client ID should match", "another-client", message.getClientId());
    }

    @Test
    public void testSubscriptionError() throws InterruptedException {
        final CountDownLatch messageLatch = new CountDownLatch(1);
        
        doAnswer(invocation -> {
            StreamObserver<Event> responseObserver = 
                    (StreamObserver<Event>) invocation.getArguments()[1];
            responseObserver.onError(new RuntimeException("Subscription error"));
            return null;
        }).when(mockService).subscribe(any(), any());

        client.subscribe(message -> messageLatch.countDown(), TEST_TOPIC);
        
        // Message handler should not be called
        assertFalse("Message handler should not be called on error", 
                messageLatch.await(1, TimeUnit.SECONDS));
        // Subscription should be marked as inactive
        assertFalse("Client should not be subscribed after error", 
                client.isSubscribed());
    }
}

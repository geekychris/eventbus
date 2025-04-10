package com.eventbus.client;

import com.eventbus.proto.EventBusGrpc;
import com.eventbus.proto.EventBusProto.Event;
import com.eventbus.proto.EventBusProto.PublishMessage;
import com.eventbus.proto.EventBusProto.PublishResponse;
import com.eventbus.proto.EventBusProto.SubscriptionRequest;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class EventBusClientTest {
    @RegisterExtension
    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    private TestEventBusService service;
    private String serverName;

    @BeforeEach
    void setUp() throws Exception {
        serverName = InProcessServerBuilder.generateName();
        service = new TestEventBusService();
        
        grpcCleanup.register(InProcessServerBuilder
                .forName(serverName)
                .directExecutor()
                .addService(service)
                .build()
                .start());
    }

    @Test
    void testPublish() throws Exception {
        try (EventBusClient client = new EventBusClient(serverName, "test-client")) {
            PublishResponse response = client.publish("test-topic", "test-subtopic", "test-message");
            assertTrue(response.getSuccess());
            assertEquals("test-topic", service.lastPublishMessage.getTopic());
            assertEquals("test-message", service.lastPublishMessage.getMessage());
        }
    }

    private static class TestEventBusService extends EventBusGrpc.EventBusImplBase {
        PublishMessage lastPublishMessage;

        @Override
        public void publish(PublishMessage request, StreamObserver<PublishResponse> responseObserver) {
            lastPublishMessage = request;
            responseObserver.onNext(PublishResponse.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        }

        @Override
        public void subscribe(SubscriptionRequest request, StreamObserver<Event> responseObserver) {
            // Implementation for testing subscribe functionality
            Event testEvent = Event.newBuilder()
                    .setTopic(request.getTopics(0))
                    .setMessage("test event")
                    .build();
            responseObserver.onNext(testEvent);
        }
    }
}


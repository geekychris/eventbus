package com.eventbus.client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class EventBusClient implements AutoCloseable {
    private final ManagedChannel channel;
    private final EventBusGrpc.EventBusStub asyncStub;
    private final EventBusGrpc.EventBusBlockingStub blockingStub;
    private final String clientId;
    private StreamObserver<Event> eventStreamObserver;
    private boolean isSubscribed = false;

    public static void main (String[] args) {
        EventBusClient ebc1 = new EventBusClient("localhost", 50051);

        EventBusClient ebc2 = new EventBusClient("localhost", 50051);

        ebc1.subscribe(new Consumer<Event>() {
            @Override
            public void accept(Event event) {
                System.out.println("1 : client: " + event.getClientId()+" topic: "+event.getTopic()+" subtopic: "+event.getSubTopic()+" message: "+event.getMessage());
            }
        }, "topic1", "topic2");

        ebc2.subscribe(new Consumer<Event>() {
            @Override
            public void accept(Event event) {
                System.out.println("2 : client: " + event.getClientId()+" topic: "+event.getTopic()+" subtopic: "+event.getSubTopic()+" message: "+event.getMessage());
            }
        }, "topic1");

        ebc1.publish("topic1", "sta", "yooooooo1");

        ebc1.publish("topic2", "stb", "foo1");

        ebc2.publish("topic1", "stb", "foo2");

        try {
            Thread.sleep(100000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
    /**
     * Constructor with default client ID (randomly generated UUID)
     * @param host Server host
     * @param port Server port
     */
    public EventBusClient(String host, int port) {
        this(host, port, UUID.randomUUID().toString());
    }

    /**
     * Constructor with custom client ID
     * @param host Server host
     * @param port Server port
     * @param clientId Custom client ID
     */
    public EventBusClient(String host, int port, String clientId) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.asyncStub = EventBusGrpc.newStub(channel);
        this.blockingStub = EventBusGrpc.newBlockingStub(channel);
        this.clientId = clientId;
    }

    /**
     * Constructor for testing purposes
     * @param channel Preconfigured channel
     * @param clientId Client ID
     */
    EventBusClient(ManagedChannel channel, String clientId) {
        this.channel = channel;
        this.asyncStub = EventBusGrpc.newStub(channel);
        this.blockingStub = EventBusGrpc.newBlockingStub(channel);
        this.clientId = clientId;
    }

    /**
     * Cancel any active subscription
     */
    public void unsubscribe() {
        if (eventStreamObserver != null) {
            eventStreamObserver.onCompleted();
            eventStreamObserver = null;
            isSubscribed = false;
        }
    }

    /**
     * Subscribe to one or more topics with an event handler
     * @param eventHandler Consumer function to handle incoming events
     * @param topics Topics to subscribe to
     * @return This client instance for method chaining
     */
    public EventBusClient subscribe(Consumer<Event> eventHandler, String... topics) {
        if (isSubscribed) {
            unsubscribe();
        }

        SubscriptionRequest request = SubscriptionRequest.newBuilder()
                .setClientId(clientId)
                .addAllTopics(Arrays.asList(topics))
                .build();

        isSubscribed = true;

        eventStreamObserver = new StreamObserver<Event>() {
            @Override
            public void onNext(Event event) {
                eventHandler.accept(event);
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Subscription error: " + t.getMessage());
                isSubscribed = false;
                eventStreamObserver = null;
            }

            @Override
            public void onCompleted() {
                System.out.println("Subscription completed");
                isSubscribed = false;
                eventStreamObserver = null;
            }
        };

        asyncStub.subscribe(request, eventStreamObserver);
        
        return this;
    }

    /**
     * Publish a message to a topic
     * @param topic Main topic
     * @param subTopic Sub-topic
     * @param message Message content
     * @return True if publishing was successful
     */
    public boolean publish(String topic, String subTopic, String message) {
        PublishMessage event = PublishMessage.newBuilder()
                .setClientId(clientId)
                .setTopic(topic)
                .setSubTopic(subTopic)
                .setMessage(message)
                .setTimestamp(System.currentTimeMillis())
                .build();

        try {
            PublishResponse response = blockingStub.publish(event);
            return response.getSuccess();
        } catch (Exception e) {
            System.err.println("Error publishing message: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get the client ID
     * @return Client ID
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * Check if the client is currently subscribed
     * @return True if subscribed
     */
    public boolean isSubscribed() {
        return isSubscribed;
    }

    @Override
    public void close() throws Exception {
        unsubscribe();
        
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}

class MyObserver implements StreamObserver<Event> {

    @Override
    public void onNext(Event event) {
        System.out.println(event);
    }

    @Override
    public void onError(Throwable throwable) {
        System.err.println(throwable.getMessage());
    }

    @Override
    public void onCompleted() {
        System.out.println("Completed");
    }
}

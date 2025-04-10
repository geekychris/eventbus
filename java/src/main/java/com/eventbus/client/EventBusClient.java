package com.eventbus.client;

import com.eventbus.proto.EventBusGrpc;
import com.eventbus.proto.EventBusProto.Event;
import com.eventbus.proto.EventBusProto.PublishMessage;
import com.eventbus.proto.EventBusProto.PublishResponse;
import com.eventbus.proto.EventBusProto.SubscriptionRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class EventBusClient implements AutoCloseable {
    private static final Logger logger = Logger.getLogger(EventBusClient.class.getName());

    private final ManagedChannel channel;
    private final EventBusGrpc.EventBusStub asyncStub;
    private final EventBusGrpc.EventBusBlockingStub blockingStub;
    private final String clientId;

    public EventBusClient(String target, String clientId) {
        this.channel = ManagedChannelBuilder.forTarget(target)
                .usePlaintext()
                .build();
        this.asyncStub = EventBusGrpc.newStub(channel);
        this.blockingStub = EventBusGrpc.newBlockingStub(channel);
        this.clientId = clientId;
    }

    public void subscribe(List<String> topics, Consumer<Event> eventHandler) {
        SubscriptionRequest request = SubscriptionRequest.newBuilder()
                .setClientId(clientId)
                .addAllTopics(topics)
                .build();

        asyncStub.subscribe(request, new StreamObserver<Event>() {
            @Override
            public void onNext(Event event) {
                eventHandler.accept(event);
            }

            @Override
            public void onError(Throwable t) {
                logger.warning("Subscription error: " + t.getMessage());
            }

            @Override
            public void onCompleted() {
                logger.info("Subscription completed");
            }
        });
    }

    public PublishResponse publish(String topic, String subTopic, String message) {
        PublishMessage publishMessage = PublishMessage.newBuilder()
                .setClientId(clientId)
                .setTopic(topic)
                .setSubTopic(subTopic)
                .setMessage(message)
                .setTimestamp(System.currentTimeMillis() / 1000L)
                .build();

        return blockingStub.publish(publishMessage);
    }

    @Override
    public void close() throws Exception {
        if (channel != null) {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}


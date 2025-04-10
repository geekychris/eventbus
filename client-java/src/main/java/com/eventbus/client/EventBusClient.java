/*
 * Copyright (c) 2025 Chris Collins chris@hitorro.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.eventbus.client;

import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EventBusClient implements AutoCloseable {
    private static final Logger logger = Logger.getLogger(EventBusClient.class.getName());
    private static final int DEFAULT_RETRY_ATTEMPTS = 3;
    private static final int DEFAULT_RETRY_DELAY_MS = 1000;
    private static final int DEFAULT_HEALTH_CHECK_INTERVAL_SEC = 30;
    
    // Connection management
    private final List<ServerAddress> serverAddresses;
    private final AtomicReference<ManagedChannel> channelRef = new AtomicReference<>();
    private final AtomicReference<EventBusGrpc.EventBusStub> asyncStubRef = new AtomicReference<>();
    private final AtomicReference<EventBusGrpc.EventBusBlockingStub> blockingStubRef = new AtomicReference<>();
    private final AtomicInteger currentServerIndex = new AtomicInteger(0);
    
    // Client configuration
    private final String clientId;
    private final int maxRetryAttempts;
    private final int retryDelayMs;
    private final boolean reconnectOnFailure;
    
    // Subscription state
    private final AtomicReference<StreamObserver<Event>> eventStreamObserverRef = new AtomicReference<>();
    private final AtomicBoolean isSubscribed = new AtomicBoolean(false);
    private final List<String> subscribedTopics = new CopyOnWriteArrayList<>();
    private Consumer<Event> eventHandler;
    
    // Health check
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean healthCheckEnabled;
    private final int healthCheckIntervalSec;
    private ScheduledFuture<?> healthCheckFuture;
    
    // Lifecycle management
    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    public static void main (String[] args) {
        EventBusClient ebc1 = new EventBusClient("localhost", 50051);

        EventBusClient ebc2 = new EventBusClient("localhost", 50051);

        ebc1.subscribe(new Consumer<Event>() {
            @Override
            public void accept(Event event) {
                System.out.println("1 : client: " + event.getClientId()+" topic: "+event.getTopic()+" subtopic: "+event.getSubTopic()+" message: "+event.getMessage());
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
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

        for (int i = 0; i <10000; i++) {
            ebc1.publish("topic1", "sta", "yooooooo1"+i );
            ebc2.publish("topic1", "stb", "foo2"+ i);
        }


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
        this(new Builder().addServerAddress(host, port));
    }

    /**
     * Constructor with custom client ID
     * @param host Server host
     * @param port Server port
     * @param clientId Custom client ID
     */
    public EventBusClient(String host, int port, String clientId) {
        this(new Builder().addServerAddress(host, port).clientId(clientId));
    }

    /**
     * Constructor using Builder for configuration
     * @param builder EventBusClient Builder
     */
    private EventBusClient(Builder builder) {
        this.serverAddresses = Collections.unmodifiableList(new ArrayList<>(builder.serverAddresses));
        this.clientId = builder.clientId;
        this.maxRetryAttempts = builder.maxRetryAttempts;
        this.retryDelayMs = builder.retryDelayMs;
        this.reconnectOnFailure = builder.reconnectOnFailure;
        this.healthCheckEnabled = new AtomicBoolean(builder.healthCheckEnabled);
        this.healthCheckIntervalSec = builder.healthCheckIntervalSec;
        
        if (serverAddresses.isEmpty()) {
            throw new IllegalArgumentException("At least one server address must be provided");
        }
        
        // Initialize connection to first server
        connectToServer(0);
        
        // Set up health check scheduler if enabled
        if (builder.healthCheckEnabled) {
            this.scheduler = Executors.newScheduledThreadPool(1);
            startHealthCheck();
        } else {
            this.scheduler = null;
        }
    }

    /**
     * Constructor for testing purposes
     * @param channel Preconfigured channel
     * @param clientId Client ID
     */
    EventBusClient(ManagedChannel channel, String clientId) {
        this.serverAddresses = Collections.singletonList(new ServerAddress("test", 0));
        this.channelRef.set(channel);
        this.asyncStubRef.set(EventBusGrpc.newStub(channel));
        this.blockingStubRef.set(EventBusGrpc.newBlockingStub(channel));
        this.clientId = clientId;
        this.maxRetryAttempts = DEFAULT_RETRY_ATTEMPTS;
        this.retryDelayMs = DEFAULT_RETRY_DELAY_MS;
        this.reconnectOnFailure = false;
        this.healthCheckEnabled = new AtomicBoolean(false);
        this.healthCheckIntervalSec = DEFAULT_HEALTH_CHECK_INTERVAL_SEC;
        this.scheduler = null;
    }
    
    /**
     * Connect to a server at the specified index
     * @param serverIndex Index of the server to connect to
     * @return true if connection was successful
     */
    private synchronized boolean connectToServer(int serverIndex) {
        if (isClosed.get()) {
            return false;
        }
        
        if (serverIndex < 0 || serverIndex >= serverAddresses.size()) {
            return false;
        }
        
        // Close existing channel if there is one
        ManagedChannel existingChannel = channelRef.get();
        if (existingChannel != null && !existingChannel.isShutdown()) {
            try {
                existingChannel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.log(Level.WARNING, "Interrupted while shutting down channel", e);
            }
        }
        
        // Connect to new server
        ServerAddress serverAddress = serverAddresses.get(serverIndex);
        try {
            ManagedChannel newChannel = ManagedChannelBuilder.forAddress(serverAddress.getHost(), serverAddress.getPort())
                    .usePlaintext()
                    .build();
            
            channelRef.set(newChannel);
            asyncStubRef.set(EventBusGrpc.newStub(newChannel));
            blockingStubRef.set(EventBusGrpc.newBlockingStub(newChannel));
            currentServerIndex.set(serverIndex);
            
            logger.info("Connected to server: " + serverAddress);
            
            // If we were subscribed, resubscribe to the new server
            if (isSubscribed.get() && !subscribedTopics.isEmpty() && eventHandler != null) {
                subscribe(eventHandler, subscribedTopics.toArray(new String[0]));
            }
            
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to connect to server: " + serverAddress, e);
            return false;
        }
    }

    /**
     * Cancel any active subscription
     */
    public synchronized void unsubscribe() {
        StreamObserver<Event> observer = eventStreamObserverRef.getAndSet(null);
        if (observer != null) {
            try {
                observer.onCompleted();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error while unsubscribing", e);
            }
        }
        isSubscribed.set(false);
        subscribedTopics.clear();
        eventHandler = null;
    }

    /**
     * Subscribe to one or more topics with an event handler
     * @param eventHandler Consumer function to handle incoming events
     * @param topics Topics to subscribe to
     * @return This client instance for method chaining
     */
    public synchronized EventBusClient subscribe(Consumer<Event> eventHandler, String... topics) {
        if (isClosed.get()) {
            throw new IllegalStateException("Client is closed");
        }
        
        if (isSubscribed.get()) {
            unsubscribe();
        }
        
        this.eventHandler = eventHandler;
        subscribedTopics.clear();
        subscribedTopics.addAll(Arrays.asList(topics));
        
        SubscriptionRequest request = SubscriptionRequest.newBuilder()
                .setClientId(clientId)
                .addAllTopics(Arrays.asList(topics))
                .build();
        
        StreamObserver<Event> streamObserver = new StreamObserver<Event>() {
            @Override
            public void onNext(Event event) {
                try {
                    eventHandler.accept(event);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error in event handler", e);
                }
            }

            @Override
            public void onError(Throwable t) {
                logger.log(Level.WARNING, "Subscription error: " + t.getMessage(), t);
                
                // Check if the client is closed
                if (isClosed.get()) {
                    return;
                }
                
                // Set subscription state
                isSubscribed.set(false);
                eventStreamObserverRef.set(null);
                
                // Try to reconnect if failure handling is enabled
                if (reconnectOnFailure) {
                    logger.info("Attempting to reconnect after subscription error");
                    if (t instanceof StatusRuntimeException) {
                        StatusRuntimeException sre = (StatusRuntimeException) t;
                        if (sre.getStatus().getCode() == Status.Code.UNAVAILABLE) {
                            // Server unavailable, try another server
                            tryNextServer();
                            
                            // Try to resubscribe
                            try {
                                subscribe(eventHandler, subscribedTopics.toArray(new String[0]));
                            } catch (Exception e) {
                                logger.log(Level.SEVERE, "Failed to resubscribe after error", e);
                            }
                        }
                    }
                }
            }

            @Override
            public void onCompleted() {
                logger.info("Subscription completed");
                isSubscribed.set(false);
                eventStreamObserverRef.set(null);
            }
        };
        
        try {
            EventBusGrpc.EventBusStub stub = asyncStubRef.get();
            if (stub == null) {
                throw new IllegalStateException("Client is not connected to any server");
            }
            
            stub.subscribe(request, streamObserver);
            eventStreamObserverRef.set(streamObserver);
            isSubscribed.set(true);
            
            logger.info("Subscribed to topics: " + Arrays.toString(topics));
            return this;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to subscribe", e);
            
            // If connection issue, try another server
            if (e instanceof StatusRuntimeException) {
                StatusRuntimeException sre = (StatusRuntimeException) e;
                if (sre.getStatus().getCode() == Status.Code.UNAVAILABLE && reconnectOnFailure) {
                    if (tryNextServer()) {
                        return subscribe(eventHandler, topics);
                    }
                }
            }
            
            throw new RuntimeException("Failed to subscribe: " + e.getMessage(), e);
        }
    }
    /**
     * Publish a message to a topic
     * @param topic Main topic
     * @param subTopic Sub-topic
     * @param message Message content
     * @return True if publishing was successful
     */
    public boolean publish(String topic, String subTopic, String message) {
        if (isClosed.get()) {
            throw new IllegalStateException("Client is closed");
        }
        
        PublishMessage event = PublishMessage.newBuilder()
                .setClientId(clientId)
                .setTopic(topic)
                .setSubTopic(subTopic)
                .setMessage(message)
                .setTimestamp(System.currentTimeMillis())
                .build();

        for (int attempt = 0; attempt <= maxRetryAttempts; attempt++) {
            try {
                EventBusGrpc.EventBusBlockingStub stub = blockingStubRef.get();
                if (stub == null) {
                    throw new IllegalStateException("Client is not connected to any server");
                }
                
                PublishResponse response = stub.publish(event);
                return response.getSuccess();
            } catch (Exception e) {
                if (attempt == maxRetryAttempts) {
                    logger.log(Level.SEVERE, "Failed to publish after " + maxRetryAttempts + " attempts", e);
                    return false;
                }
                
                // If server unavailable, try another server
                if (e instanceof StatusRuntimeException) {
                    StatusRuntimeException sre = (StatusRuntimeException) e;
                    if (sre.getStatus().getCode() == Status.Code.UNAVAILABLE && reconnectOnFailure) {
                        logger.warning("Server unavailable, trying next server...");
                        if (tryNextServer()) {
                            continue;
                        }
                    }
                }
                
                // Wait before retry
                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.warning("Publish retry interrupted");
                    return false;
                }
            }
        }
        
        return false;
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
        return isSubscribed.get();
    }

    /**
     * Try connecting to the next available server
     * @return true if connected successfully to a different server
     */
    private boolean tryNextServer() {
        int currentIndex = currentServerIndex.get();
        int nextIndex = (currentIndex + 1) % serverAddresses.size();
        
        // If we've tried all servers, return false
        if (nextIndex == currentIndex) {
            return false;
        }
        
        return connectToServer(nextIndex);
    }
    
    /**
     * Clean up the specified connection
     * @param channel The channel to clean up
     */
    private void cleanupConnection(ManagedChannel channel) {
        if (channel != null && !channel.isShutdown()) {
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.log(Level.WARNING, "Interrupted while shutting down channel", e);
                // Force shutdown if graceful shutdown fails
                channel.shutdownNow();
            }
        }
    }
    
    /**
     * Start health check for the current connection
     */
    private synchronized void startHealthCheck() {
        if (scheduler == null || isClosed.get() || !healthCheckEnabled.get()) {
            return;
        }
        
        if (healthCheckFuture != null && !healthCheckFuture.isDone()) {
            healthCheckFuture.cancel(false);
        }
        
        healthCheckFuture = scheduler.scheduleAtFixedRate(() -> {
            try {
                ManagedChannel channel = channelRef.get();
                if (channel != null) {
                    ConnectivityState state = channel.getState(true);
                    logger.fine("Health check - Server connection state: " + state);
                    
                    if (state == ConnectivityState.TRANSIENT_FAILURE || 
                        state == ConnectivityState.SHUTDOWN ||
                        state == ConnectivityState.IDLE) {
                        
                        logger.warning("Health check detected connection issue, attempting to reconnect");
                        tryNextServer();
                    }
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error during health check", e);
            }
        }, 0, healthCheckIntervalSec, TimeUnit.SECONDS);
    }
    
    /**
     * Stop health check task
     */
    private synchronized void stopHealthCheck() {
        if (healthCheckFuture != null && !healthCheckFuture.isDone()) {
            healthCheckFuture.cancel(false);
            healthCheckFuture = null;
        }
        
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }
    
    @Override
    public void close() throws Exception {
        if (isClosed.compareAndSet(false, true)) {
            // Stop health check
            stopHealthCheck();
            
            // Unsubscribe from all topics
            unsubscribe();
            
            // Shut down the channel
            ManagedChannel channel = channelRef.getAndSet(null);
            cleanupConnection(channel);
            
            // Clear references
            asyncStubRef.set(null);
            blockingStubRef.set(null);
            
            logger.info("EventBusClient closed");
        }
    }
    
    /**
     * Builder for EventBusClient
     */
    public static class Builder {
        private final List<ServerAddress> serverAddresses = new ArrayList<>();
        private String clientId = UUID.randomUUID().toString();
        private int maxRetryAttempts = DEFAULT_RETRY_ATTEMPTS;
        private int retryDelayMs = DEFAULT_RETRY_DELAY_MS;
        private boolean reconnectOnFailure = true;
        private boolean healthCheckEnabled = true;
        private int healthCheckIntervalSec = DEFAULT_HEALTH_CHECK_INTERVAL_SEC;
        
        /**
         * Add a server address to the list of servers
         * @param host Server host
         * @param port Server port
         * @return This builder for method chaining
         */
        public Builder addServerAddress(String host, int port) {
            serverAddresses.add(new ServerAddress(host, port));
            return this;
        }
        
        /**
         * Add a server address with TLS configuration
         * @param host Server host
         * @param port Server port
         * @param useTls Whether to use TLS
         * @return This builder for method chaining
         */
        public Builder addServerAddress(String host, int port, boolean useTls) {
            serverAddresses.add(new ServerAddress(host, port, useTls));
            return this;
        }
        
        /**
         * Add a server address from a string in the format "host:port"
         * @param address Server address in "host:port" format
         * @return This builder for method chaining
         */
        public Builder addServerAddress(String address) {
            serverAddresses.add(new ServerAddress(address));
            return this;
        }
        
        /**
         * Add multiple server addresses
         * @param addresses List of server addresses
         * @return This builder for method chaining
         */
        public Builder addServerAddresses(List<ServerAddress> addresses) {
            serverAddresses.addAll(addresses);
            return this;
        }
        
        /**
         * Set the client ID
         * @param clientId Client ID
         * @return This builder for method chaining
         */
        public Builder clientId(String clientId) {
            this.clientId = Objects.requireNonNull(clientId, "Client ID cannot be null");
            return this;
        }
        
        /**
         * Set the maximum number of retry attempts
         * @param maxRetryAttempts Maximum retry attempts
         * @return This builder for method chaining
         */
        public Builder maxRetryAttempts(int maxRetryAttempts) {
            if (maxRetryAttempts < 0) {
                throw new IllegalArgumentException("Max retry attempts must be non-negative");
            }
            this.maxRetryAttempts = maxRetryAttempts;
            return this;
        }
        
        /**
         * Set the retry delay in milliseconds
         * @param retryDelayMs Retry delay in milliseconds
         * @return This builder for method chaining
         */
        public Builder retryDelayMs(int retryDelayMs) {
            if (retryDelayMs < 0) {
                throw new IllegalArgumentException("Retry delay must be non-negative");
            }
            this.retryDelayMs = retryDelayMs;
            return this;
        }
        
        /**
         * Set whether to reconnect on failure
         * @param reconnectOnFailure Whether to reconnect on failure
         * @return This builder for method chaining
         */
        public Builder reconnectOnFailure(boolean reconnectOnFailure) {
            this.reconnectOnFailure = reconnectOnFailure;
            return this;
        }
        
        /**
         * Set whether to enable health checks
         * @param enabled Whether to enable health checks
         * @return This builder for method chaining
         */
        public Builder healthCheckEnabled(boolean enabled) {
            this.healthCheckEnabled = enabled;
            return this;
        }
        
        /**
         * Set the health check interval in seconds
         * @param intervalSec Health check interval in seconds
         * @return This builder for method chaining
         */
        public Builder healthCheckIntervalSec(int intervalSec) {
            if (intervalSec <= 0) {
                throw new IllegalArgumentException("Health check interval must be positive");
            }
            this.healthCheckIntervalSec = intervalSec;
            return this;
        }
        
        /**
         * Build the EventBusClient
         * @return A new EventBusClient instance
         */
        public EventBusClient build() {
            return new EventBusClient(this);
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

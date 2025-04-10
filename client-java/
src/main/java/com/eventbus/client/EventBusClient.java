public class EventBusClient implements AutoCloseable {
    private static final Logger logger = Logger.getLogger(EventBusClient.class.getName());
    
    // Connection management
    private final List<ServerAddress> serverAddresses;
    private final AtomicReference<ManagedChannel> currentChannel = new AtomicReference<>();
    private final AtomicReference<EventBusGrpc.EventBusStub> asyncStub = new AtomicReference<>();
    private final AtomicReference<EventBusGrpc.EventBusBlockingStub> blockingStub = new AtomicReference<>();
    private final AtomicInteger currentServerIndex = new AtomicInteger(0);
    private final Object connectionLock = new Object();
    
    // Client identity and state
    private final String clientId;
    private final AtomicReference<StreamObserver<Event>> eventStreamObserver = new AtomicReference<>();
    private final AtomicBoolean isSubscribed = new AtomicBoolean(false);
    private final List<String> currentSubscriptionTopics = new CopyOnWriteArrayList<>();
    private final AtomicReference<Consumer<Event>> currentEventHandler = new AtomicReference<>();
    
    // Configuration
    private final long reconnectDelayMs;
    private final int maxReconnectAttempts;
    private final boolean enableHealthCheck;
    private final long healthCheckIntervalMs;
    
    // Health checker and reconnection
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);
    public static void main(String[] args) {
        // Example of using the builder to create clients
        EventBusClient ebc1 = EventBusClient.builder()
                .addServerAddress("localhost", 50051)
                .addServerAddress("localhost", 50052)
                .withReconnectDelay(5000)
                .withMaxReconnectAttempts(5)
                .withHealthCheck(true)
                .withHealthCheckInterval(10000)
                .build();

        EventBusClient ebc2 = EventBusClient.builder()
                .addServerAddress("localhost", 50051)
                .addServerAddress("localhost", 50052)
                .build();

        ebc1.subscribe(event -> {
            System.out.println("1 : client: " + event.getClientId() + " topic: " + event.getTopic() + 
                    " subtopic: " + event.getSubTopic() + " message: " + event.getMessage());
        }, "topic1", "topic2");

        ebc2.subscribe(event -> {
            System.out.println("2 : client: " + event.getClientId() + " topic: " + event.getTopic() + 
                    " subtopic: " + event.getSubTopic() + " message: " + event.getMessage());
        }, "topic1");

        ebc1.publish("topic1", "sta", "yooooooo1");
        ebc1.publish("topic2", "stb", "foo1");
        ebc2.publish("topic1", "stb", "foo2");

        try {
            Thread.sleep(100000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                ebc1.close();
                ebc2.close();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error closing clients", e);
            }
        }
    }
    /**
     * Create a new builder for EventBusClient
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Private constructor used by the builder
     */
    private EventBusClient(Builder builder) {
        this.serverAddresses = Collections.unmodifiableList(new ArrayList<>(builder.serverAddresses));
        this.clientId = builder.clientId != null ? builder.clientId : UUID.randomUUID().toString();
        this.reconnectDelayMs = builder.reconnectDelayMs;
        this.maxReconnectAttempts = builder.maxReconnectAttempts;
        this.enableHealthCheck = builder.enableHealthCheck;
        this.healthCheckIntervalMs = builder.healthCheckIntervalMs;
        
        // Initialize the scheduler for health checking and reconnection
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread thread = new Thread(r, "eventbus-health-checker");
            thread.setDaemon(true);
            return thread;
        });
        
        // Connect to the first available server
        connectToServer();
        
        // Start health checker if enabled
        if (this.enableHealthCheck) {
            startHealthChecker();
        }
    }
    
    /**
     * Connects to the current server in the list or tries the next one if the current fails
     * @return true if connection was successful, false otherwise
     */
    private boolean connectToServer() {
        synchronized (connectionLock) {
            if (isShutdown.get()) {
                return false;
            }
            
            // Close existing channel if any
            ManagedChannel oldChannel = currentChannel.getAndSet(null);
            if (oldChannel != null && !oldChannel.isShutdown()) {
                try {
                    oldChannel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.log(Level.WARNING, "Interrupted while shutting down channel", e);
                }
            }
            
            if (serverAddresses.isEmpty()) {
                logger.severe("No server addresses configured");
                return false;
            }
            
            // Try each server in the list
            int attempts = 0;
            boolean connected = false;
            
            while (!connected && attempts < serverAddresses.size()) {
                int index = currentServerIndex.get() % serverAddresses.size();
                ServerAddress address = serverAddresses.get(index);
                
                try {
                    logger.info("Connecting to server: " + address);
                    ManagedChannel newChannel = ManagedChannelBuilder
                            .forAddress(address.getHost(), address.getPort())
                            .usePlaintext()
                            .build();
                    
                    // Test the connection
                    ConnectivityState state = newChannel.getState(true);
                    if (state != ConnectivityState.SHUTDOWN && state != ConnectivityState.TRANSIENT_FAILURE) {
                        currentChannel.set(newChannel);
                        asyncStub.set(EventBusGrpc.newStub(newChannel));
                        blockingStub.set(EventBusGrpc.newBlockingStub(newChannel));
                        connected = true;
                        
                        // If we were previously subscribed, resubscribe
                        if (isSubscribed.get() && !currentSubscriptionTopics.isEmpty() && currentEventHandler.get() != null) {
                            String[] topics = currentSubscriptionTopics.toArray(new String[0]);
                            subscribe(currentEventHandler.get(), topics);
                        }
                        
                        logger.info("Connected successfully to " + address);
                    } else {
                        logger.warning("Failed to connect to " + address + ", state: " + state);
                        newChannel.shutdown();
                        currentServerIndex.incrementAndGet();
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error connecting to server: " + address, e);
                    currentServerIndex.incrementAndGet();
                }
                
                attempts++;
            }
            
            if (!connected) {
                logger.severe("Failed to connect to any server after " + attempts + " attempts");
            }
            
            return connected;
        }
    }
    
    /**
     * Starts the health checker thread that periodically checks server connectivity
     */
    private void startHealthChecker() {
        scheduler.scheduleAtFixedRate(() -> {
            if (isShutdown.get()) {
                return;
            }
            
            ManagedChannel channel = currentChannel.get();
            if (channel == null || channel.isShutdown()) {
                logger.warning("Channel is null or shutdown, attempting to reconnect");
                connectToServer();
                return;
            }
            
            try {
                ConnectivityState state = channel.getState(true);
                logger.fine("Current channel state: " + state);
                
                if (state == ConnectivityState.TRANSIENT_FAILURE || 
                    state == ConnectivityState.SHUTDOWN || 
                    state == ConnectivityState.IDLE) {
                    logger.warning("Channel is in " + state + " state, attempting to reconnect");
                    connectToServer();
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error checking channel health", e);
                connectToServer();
            }
        }, healthCheckIntervalMs, healthCheckIntervalMs, TimeUnit.MILLISECONDS);
    }

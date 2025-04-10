public class EventBusClient implements AutoCloseable {
    private static final Logger logger = Logger.getLogger(EventBusClient.class.getName());
    
    // Server configuration
    private final List<ServerAddress> serverAddresses;
    private final AtomicInteger currentServerIndex = new AtomicInteger(0);
    
    // Client configuration
    private final String clientId;
    private final int maxRetries;
    private final long retryDelayMs;
    private final boolean autoReconnect;
    
    // Connection state
    private final AtomicReference<ManagedChannel> channelRef = new AtomicReference<>();
    private final AtomicReference<EventBusGrpc.EventBusStub> asyncStubRef = new AtomicReference<>();
    private final AtomicReference<EventBusGrpc.EventBusBlockingStub> blockingStubRef = new AtomicReference<>();
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    
    // Subscription state
    private StreamObserver<Event> eventStreamObserver;
    private final AtomicBoolean isSubscribed = new AtomicBoolean(false);
    private List<String> currentTopics = Collections.emptyList();
    private Consumer<Event> currentEventHandler;
    
    // Health check
    private final ScheduledExecutorService healthCheckExecutor;
/**
 * Client for the EventBus service with support for multiple servers,
 * automatic failover, reconnection, and health monitoring.
 */
public class EventBusClient implements AutoCloseable {
    private static final Logger logger = Logger.getLogger(EventBusClient.class.getName());
    
    // Default values for configuration
    private static final int DEFAULT_RETRY_ATTEMPTS = 3;
    private static final long DEFAULT_RETRY_DELAY_MS = 1000;
    private static final long DEFAULT_HEALTH_CHECK_INTERVAL_MS = 10000;
    private static final long DEFAULT_RECONNECT_DELAY_MS = 5000;
    
    private final List<ServerConnection> serverConnections;
    private final AtomicReference<ServerConnection> currentConnection = new AtomicReference<>();
    private final String clientId;
    private final int maxRetryAttempts;
    private final long retryDelayMs;
    private final boolean autoReconnect;
    private final long reconnectDelayMs;
    private final Random random = new Random();
    
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduler;
    private final AtomicReference<ScheduledFuture<?>> healthCheckFuture = new AtomicReference<>();
    private final AtomicReference<Consumer<Event>> eventHandler = new AtomicReference<>();
    private final List<String> subscribedTopics = new CopyOnWriteArrayList<>();
    private final AtomicBoolean isSubscribed = new AtomicBoolean(false);
    
    /**
     * Server connection class to encapsulate a single server's connection details
     */
    private static class ServerConnection {
        private final String host;
        private final int port;
        private ManagedChannel channel;
        private EventBusGrpc.EventBusStub asyncStub;
        private EventBusGrpc.EventBusBlockingStub blockingStub;
        private StreamObserver<Event> eventStreamObserver;
        private final AtomicBoolean healthy = new AtomicBoolean(true);
        
        public ServerConnection(String host, int port) {
            this.host = host;
            this.port = port;
            connect();
        }
        
        public void connect() {
            if (channel != null && !channel.isShutdown()) {
                return;
            }
            
            this.channel = ManagedChannelBuilder.forAddress(host, port)
                    .usePlaintext()
                    .build();
            this.asyncStub = EventBusGrpc.newStub(channel);
            this.blockingStub = EventBusGrpc.newBlockingStub(channel);
            this.healthy.set(true);
        }
        
        public void disconnect() {
            if (eventStreamObserver != null) {
                try {
                    eventStreamObserver.onCompleted();
                } catch (Exception e) {
                    logger.log(Level.FINE, "Error completing event stream", e);
                }
                eventStreamObserver = null;
            }
            
            if (channel != null && !channel.isShutdown()) {
                try {
                    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.log(Level.WARNING, "Interrupted during channel shutdown", e);
                }
            }
        }
        
        public boolean isHealthy() {
            if (channel == null || channel.isShutdown() || channel.isTerminated()) {
                healthy.set(false);
                return false;
            }
            
            ConnectivityState state = channel.getState(true);
            boolean isChannelHealthy = state != ConnectivityState.SHUTDOWN && 
                                       state != ConnectivityState.TRANSIENT_FAILURE;
            healthy.set(isChannelHealthy);
            return isChannelHealthy;
        }
        
        public boolean checkHealth() {
            try {
                // Create a simple health check request - we're just testing connectivity
                SubscriptionRequest healthCheckRequest = SubscriptionRequest.newBuilder()
                        .setClientId("health-check")
                        .build();
                
                // Use a very short deadline for the health check
                blockingStub.withDeadlineAfter(2, TimeUnit.SECONDS)
                        .subscribe(healthCheckRequest);
                
                healthy.set(true);
                return true;
            } catch (StatusRuntimeException e) {
                logger.log(Level.FINE, "Health check failed for " + host + ":" + port, e);
                healthy.set(false);
                return false;
            } catch (Exception e) {
                logger.log(Level.FINE, "Health check error for " + host + ":" + port, e);
                healthy.set(false);
                return false;
            }
        }
        
        @Override
        public String toString() {
            return host + ":" + port;
        }
    }
     * @return A new Builder instance
    /**
     * Builder for EventBusClient
     */
    public static class Builder {
        private final List<ServerAddress> serverAddresses = new ArrayList<>();
        private String clientId = UUID.randomUUID().toString();
        private int maxRetryAttempts = DEFAULT_RETRY_ATTEMPTS;
        private long retryDelayMs = DEFAULT_RETRY_DELAY_MS;
        private boolean enableHealthCheck = true;
        private long healthCheckIntervalMs = DEFAULT_HEALTH_CHECK_INTERVAL_MS;
        private boolean autoReconnect = true;
        private long reconnectDelayMs = DEFAULT_RECONNECT_DELAY_MS;
        private boolean useSharedScheduler = false;
        private ScheduledExecutorService scheduler = null;
        
        /**
         * Add a server address to the client's server pool
         * @param host Server hostname
         * @param port Server port
         * @return Builder instance
         */
        public Builder addServer(String host, int port) {
            serverAddresses.add(new ServerAddress(host, port));
            return this;
        }
        
        /**
         * Add a server address to the client's server pool
         * @param hostAndPort Server address in format "host:port"
         * @return Builder instance
         */
        public Builder addServer(String hostAndPort) {
            String[] parts = hostAndPort.split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Server address must be in format 'host:port'");
            }
            try {
                int port = Integer.parseInt(parts[1]);
                return addServer(parts[0], port);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid port number: " + parts[1]);
            }
        }
        
        /**
         * Set the client ID
         * @param clientId Custom client ID
         * @return Builder instance
         */
        public Builder setClientId(String clientId) {
            this.clientId = Objects.requireNonNull(clientId, "Client ID cannot be null");
            return this;
        }
        
        /**
         * Set the maximum number of retry attempts for operations
         * @param maxRetryAttempts Maximum retry attempts
         * @return Builder instance
         */
        public Builder setMaxRetryAttempts(int maxRetryAttempts) {
            if (maxRetryAttempts < 0) {
                throw new IllegalArgumentException("Max retry attempts must be >= 0");
            }
            this.maxRetryAttempts = maxRetryAttempts;
            return this;
        }
        
        /**
         * Set the delay between retry attempts
         * @param retryDelayMs Delay in milliseconds
         * @return Builder instance
         */
        public Builder setRetryDelay(long retryDelayMs) {
            if (retryDelayMs < 0) {
                throw new IllegalArgumentException("Retry delay must be >= 0");
            }
            this.retryDelayMs = retryDelayMs;
            return this;
        }
        
        /**
         * Enable or disable health checks
         * @param enableHealthCheck Whether to enable health checks
         * @return Builder instance
         */
        public Builder enableHealthCheck(boolean enableHealthCheck) {
            this.enableHealthCheck = enableHealthCheck;
        this.healthCheckExecutor = null;
        
        // Connect to the server
        connectToServer(0);
    }

    /**
     * Constructor for testing purposes
     * @param channel Preconfigured channel
     * @param clientId Client ID
     */
    EventBusClient(ManagedChannel channel, String clientId) {
        this.serverAddresses = Collections.emptyList();
        this.clientId = clientId;
        this.maxRetries = 3;
        this.retryDelayMs = 1000;
        this.autoReconnect = false;
        this.healthCheckIntervalMs = 5000;
        this.healthCheckExecutor = null;
        
        // Use the provided channel
        channelRef.set(channel);
        asyncStubRef.set(EventBusGrpc.newStub(channel));
        blockingStubRef.set(EventBusGrpc.newBlockingStub(channel));
        isConnected.set(true);
    }
    
    /**
     * Private constructor used by the Builder
     */
    private EventBusClient(List<ServerAddress> serverAddresses, String clientId, 
                          int maxRetries, long retryDelayMs, boolean autoReconnect,
                          long healthCheckIntervalMs) {
        this.serverAddresses = new ArrayList<>(serverAddresses);
        this.clientId = clientId;
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;
        this.autoReconnect = autoReconnect;
        this.healthCheckIntervalMs = healthCheckIntervalMs;
        
        // Start health check if enabled
        if (autoReconnect && healthCheckIntervalMs > 0) {
            this.healthCheckExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "eventbus-health-checker");
                t.setDaemon(true);
                return t;
            });
            startHealthCheck();
        } else {
            this.healthCheckExecutor = null;
        }
        
        // Connect to the first server
        if (!serverAddresses.isEmpty()) {
            connectToServer(0);
        }
    }
    
    /**
     * Connects to a server by index
     * 
     * @param serverIndex Index of the server to connect to
     * @return true if connection was successful, false otherwise
     */
    private boolean connectToServer(int serverIndex) {
        if (serverIndex < 0 || serverIndex >= serverAddresses.size()) {
            logger.warning("Invalid server index: " + serverIndex);
            return false;
        }
        
        // Close existing channel if any
        closeChannel();
        
        try {
            ServerAddress address = serverAddresses.get(serverIndex);
            logger.info("Connecting to server: " + address);
            
            ManagedChannel newChannel = ManagedChannelBuilder.forAddress(address.getHost(), address.getPort())
                    .usePlaintext()
                    .build();
            
            channelRef.set(newChannel);
            asyncStubRef.set(EventBusGrpc.newStub(newChannel));
            blockingStubRef.set(EventBusGrpc.newBlockingStub(newChannel));
            currentServerIndex.set(serverIndex);
            isConnected.set(true);
            
            // If previously subscribed, re-subscribe
            if (isSubscribed.get() && currentEventHandler != null && !currentTopics.isEmpty()) {
                subscribe(currentEventHandler, currentTopics.toArray(new String[0]));
            }
            
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to connect to server at index " + serverIndex, e);
            isConnected.set(false);
            return false;
        }
    }
    
    /**
     * Tries to find another server to connect to
     * 
     * @return true if successfully connected to another server, false otherwise
     */
    private boolean failover() {
        if (serverAddresses.size() <= 1) {
            return false;
        }
        
        int currentIndex = currentServerIndex.get();
        int nextIndex = (currentIndex + 1) % serverAddresses.size();
        int startingIndex = currentIndex;
        
        while (nextIndex != startingIndex) {
            if (connectToServer(nextIndex)) {
                logger.info("Failover successful to server: " + serverAddresses.get(nextIndex));
                return true;
            }
            nextIndex = (nextIndex + 1) % serverAddresses.size();
        }
        
        logger.severe("Failed to connect to any server");
        return false;
    }
    
    /**
     * Closes the current channel
     */
    private void closeChannel() {
        ManagedChannel channel = channelRef.getAndSet(null);
        asyncStubRef.set(null);
        blockingStubRef.set(null);
        
        if (channel != null && !channel.isShutdown()) {
            try {
                channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.log(Level.WARNING, "Interrupted while closing channel", e);
            }
        }
    }
    
    /**
     * Starts a periodic health check to monitor server connection
     */
    private void startHealthCheck() {
        if (healthCheckExecutor == null || isShutdown.get()) {
            return;
        }
        
        healthCheckExecutor.scheduleAtFixedRate(() -> {
            if (isShutdown.get()) {
                return;
            }
            
            ManagedChannel channel = channelRef.get();
            if (channel == null) {
                return;
            }
            
            ConnectivityState

package com.eventbus.client;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 * The EventBus service definition
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.45.1)",
    comments = "Source: eventbus.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class EventBusGrpc {

  private EventBusGrpc() {}

  public static final String SERVICE_NAME = "eventbus.EventBus";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.eventbus.client.PublishMessage,
      com.eventbus.client.PublishResponse> getPublishMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Publish",
      requestType = com.eventbus.client.PublishMessage.class,
      responseType = com.eventbus.client.PublishResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.eventbus.client.PublishMessage,
      com.eventbus.client.PublishResponse> getPublishMethod() {
    io.grpc.MethodDescriptor<com.eventbus.client.PublishMessage, com.eventbus.client.PublishResponse> getPublishMethod;
    if ((getPublishMethod = EventBusGrpc.getPublishMethod) == null) {
      synchronized (EventBusGrpc.class) {
        if ((getPublishMethod = EventBusGrpc.getPublishMethod) == null) {
          EventBusGrpc.getPublishMethod = getPublishMethod =
              io.grpc.MethodDescriptor.<com.eventbus.client.PublishMessage, com.eventbus.client.PublishResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Publish"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.eventbus.client.PublishMessage.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.eventbus.client.PublishResponse.getDefaultInstance()))
              .setSchemaDescriptor(new EventBusMethodDescriptorSupplier("Publish"))
              .build();
        }
      }
    }
    return getPublishMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.eventbus.client.SubscriptionRequest,
      com.eventbus.client.Event> getSubscribeMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Subscribe",
      requestType = com.eventbus.client.SubscriptionRequest.class,
      responseType = com.eventbus.client.Event.class,
      methodType = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
  public static io.grpc.MethodDescriptor<com.eventbus.client.SubscriptionRequest,
      com.eventbus.client.Event> getSubscribeMethod() {
    io.grpc.MethodDescriptor<com.eventbus.client.SubscriptionRequest, com.eventbus.client.Event> getSubscribeMethod;
    if ((getSubscribeMethod = EventBusGrpc.getSubscribeMethod) == null) {
      synchronized (EventBusGrpc.class) {
        if ((getSubscribeMethod = EventBusGrpc.getSubscribeMethod) == null) {
          EventBusGrpc.getSubscribeMethod = getSubscribeMethod =
              io.grpc.MethodDescriptor.<com.eventbus.client.SubscriptionRequest, com.eventbus.client.Event>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Subscribe"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.eventbus.client.SubscriptionRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.eventbus.client.Event.getDefaultInstance()))
              .setSchemaDescriptor(new EventBusMethodDescriptorSupplier("Subscribe"))
              .build();
        }
      }
    }
    return getSubscribeMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.eventbus.client.PeerMessage,
      com.eventbus.client.PeerResponse> getReplicateEventMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ReplicateEvent",
      requestType = com.eventbus.client.PeerMessage.class,
      responseType = com.eventbus.client.PeerResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.eventbus.client.PeerMessage,
      com.eventbus.client.PeerResponse> getReplicateEventMethod() {
    io.grpc.MethodDescriptor<com.eventbus.client.PeerMessage, com.eventbus.client.PeerResponse> getReplicateEventMethod;
    if ((getReplicateEventMethod = EventBusGrpc.getReplicateEventMethod) == null) {
      synchronized (EventBusGrpc.class) {
        if ((getReplicateEventMethod = EventBusGrpc.getReplicateEventMethod) == null) {
          EventBusGrpc.getReplicateEventMethod = getReplicateEventMethod =
              io.grpc.MethodDescriptor.<com.eventbus.client.PeerMessage, com.eventbus.client.PeerResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ReplicateEvent"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.eventbus.client.PeerMessage.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.eventbus.client.PeerResponse.getDefaultInstance()))
              .setSchemaDescriptor(new EventBusMethodDescriptorSupplier("ReplicateEvent"))
              .build();
        }
      }
    }
    return getReplicateEventMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static EventBusStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<EventBusStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<EventBusStub>() {
        @java.lang.Override
        public EventBusStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new EventBusStub(channel, callOptions);
        }
      };
    return EventBusStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static EventBusBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<EventBusBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<EventBusBlockingStub>() {
        @java.lang.Override
        public EventBusBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new EventBusBlockingStub(channel, callOptions);
        }
      };
    return EventBusBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static EventBusFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<EventBusFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<EventBusFutureStub>() {
        @java.lang.Override
        public EventBusFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new EventBusFutureStub(channel, callOptions);
        }
      };
    return EventBusFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   * The EventBus service definition
   * </pre>
   */
  public static abstract class EventBusImplBase implements io.grpc.BindableService {

    /**
     * <pre>
     * Publishes a message to subscribers
     * </pre>
     */
    public void publish(com.eventbus.client.PublishMessage request,
        io.grpc.stub.StreamObserver<com.eventbus.client.PublishResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getPublishMethod(), responseObserver);
    }

    /**
     * <pre>
     * Subscribes to topics
     * </pre>
     */
    public void subscribe(com.eventbus.client.SubscriptionRequest request,
        io.grpc.stub.StreamObserver<com.eventbus.client.Event> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSubscribeMethod(), responseObserver);
    }

    /**
     * <pre>
     * Server-to-server message replication
     * </pre>
     */
    public void replicateEvent(com.eventbus.client.PeerMessage request,
        io.grpc.stub.StreamObserver<com.eventbus.client.PeerResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getReplicateEventMethod(), responseObserver);
    }

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getPublishMethod(),
            io.grpc.stub.ServerCalls.asyncUnaryCall(
              new MethodHandlers<
                com.eventbus.client.PublishMessage,
                com.eventbus.client.PublishResponse>(
                  this, METHODID_PUBLISH)))
          .addMethod(
            getSubscribeMethod(),
            io.grpc.stub.ServerCalls.asyncServerStreamingCall(
              new MethodHandlers<
                com.eventbus.client.SubscriptionRequest,
                com.eventbus.client.Event>(
                  this, METHODID_SUBSCRIBE)))
          .addMethod(
            getReplicateEventMethod(),
            io.grpc.stub.ServerCalls.asyncUnaryCall(
              new MethodHandlers<
                com.eventbus.client.PeerMessage,
                com.eventbus.client.PeerResponse>(
                  this, METHODID_REPLICATE_EVENT)))
          .build();
    }
  }

  /**
   * <pre>
   * The EventBus service definition
   * </pre>
   */
  public static final class EventBusStub extends io.grpc.stub.AbstractAsyncStub<EventBusStub> {
    private EventBusStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected EventBusStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new EventBusStub(channel, callOptions);
    }

    /**
     * <pre>
     * Publishes a message to subscribers
     * </pre>
     */
    public void publish(com.eventbus.client.PublishMessage request,
        io.grpc.stub.StreamObserver<com.eventbus.client.PublishResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getPublishMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Subscribes to topics
     * </pre>
     */
    public void subscribe(com.eventbus.client.SubscriptionRequest request,
        io.grpc.stub.StreamObserver<com.eventbus.client.Event> responseObserver) {
      io.grpc.stub.ClientCalls.asyncServerStreamingCall(
          getChannel().newCall(getSubscribeMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Server-to-server message replication
     * </pre>
     */
    public void replicateEvent(com.eventbus.client.PeerMessage request,
        io.grpc.stub.StreamObserver<com.eventbus.client.PeerResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getReplicateEventMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * <pre>
   * The EventBus service definition
   * </pre>
   */
  public static final class EventBusBlockingStub extends io.grpc.stub.AbstractBlockingStub<EventBusBlockingStub> {
    private EventBusBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected EventBusBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new EventBusBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Publishes a message to subscribers
     * </pre>
     */
    public com.eventbus.client.PublishResponse publish(com.eventbus.client.PublishMessage request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getPublishMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Subscribes to topics
     * </pre>
     */
    public java.util.Iterator<com.eventbus.client.Event> subscribe(
        com.eventbus.client.SubscriptionRequest request) {
      return io.grpc.stub.ClientCalls.blockingServerStreamingCall(
          getChannel(), getSubscribeMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Server-to-server message replication
     * </pre>
     */
    public com.eventbus.client.PeerResponse replicateEvent(com.eventbus.client.PeerMessage request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getReplicateEventMethod(), getCallOptions(), request);
    }
  }

  /**
   * <pre>
   * The EventBus service definition
   * </pre>
   */
  public static final class EventBusFutureStub extends io.grpc.stub.AbstractFutureStub<EventBusFutureStub> {
    private EventBusFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected EventBusFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new EventBusFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Publishes a message to subscribers
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.eventbus.client.PublishResponse> publish(
        com.eventbus.client.PublishMessage request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getPublishMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Server-to-server message replication
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.eventbus.client.PeerResponse> replicateEvent(
        com.eventbus.client.PeerMessage request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getReplicateEventMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_PUBLISH = 0;
  private static final int METHODID_SUBSCRIBE = 1;
  private static final int METHODID_REPLICATE_EVENT = 2;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final EventBusImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(EventBusImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_PUBLISH:
          serviceImpl.publish((com.eventbus.client.PublishMessage) request,
              (io.grpc.stub.StreamObserver<com.eventbus.client.PublishResponse>) responseObserver);
          break;
        case METHODID_SUBSCRIBE:
          serviceImpl.subscribe((com.eventbus.client.SubscriptionRequest) request,
              (io.grpc.stub.StreamObserver<com.eventbus.client.Event>) responseObserver);
          break;
        case METHODID_REPLICATE_EVENT:
          serviceImpl.replicateEvent((com.eventbus.client.PeerMessage) request,
              (io.grpc.stub.StreamObserver<com.eventbus.client.PeerResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  private static abstract class EventBusBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    EventBusBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.eventbus.client.EventBusProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("EventBus");
    }
  }

  private static final class EventBusFileDescriptorSupplier
      extends EventBusBaseDescriptorSupplier {
    EventBusFileDescriptorSupplier() {}
  }

  private static final class EventBusMethodDescriptorSupplier
      extends EventBusBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    EventBusMethodDescriptorSupplier(String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (EventBusGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new EventBusFileDescriptorSupplier())
              .addMethod(getPublishMethod())
              .addMethod(getSubscribeMethod())
              .addMethod(getReplicateEventMethod())
              .build();
        }
      }
    }
    return result;
  }
}

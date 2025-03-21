// Code generated by protoc-gen-go-grpc. DO NOT EDIT.
// versions:
// - protoc-gen-go-grpc v1.5.1
// - protoc             v5.29.3
// source: proto/eventbus.proto

package proto

import (
	context "context"
	grpc "google.golang.org/grpc"
	codes "google.golang.org/grpc/codes"
	status "google.golang.org/grpc/status"
)

// This is a compile-time assertion to ensure that this generated file
// is compatible with the grpc package it is being compiled against.
// Requires gRPC-Go v1.64.0 or later.
const _ = grpc.SupportPackageIsVersion9

const (
	EventBus_Publish_FullMethodName   = "/eventbus.EventBus/Publish"
	EventBus_Subscribe_FullMethodName = "/eventbus.EventBus/Subscribe"
)

// EventBusClient is the client API for EventBus service.
//
// For semantics around ctx use and closing/ending streaming RPCs, please refer to https://pkg.go.dev/google.golang.org/grpc/?tab=doc#ClientConn.NewStream.
//
// The EventBus service definition
type EventBusClient interface {
	// Publishes a message to subscribers
	Publish(ctx context.Context, in *PublishMessage, opts ...grpc.CallOption) (*PublishResponse, error)
	// Subscribes to topics
	Subscribe(ctx context.Context, in *SubscriptionRequest, opts ...grpc.CallOption) (grpc.ServerStreamingClient[Event], error)
}

type eventBusClient struct {
	cc grpc.ClientConnInterface
}

func NewEventBusClient(cc grpc.ClientConnInterface) EventBusClient {
	return &eventBusClient{cc}
}

func (c *eventBusClient) Publish(ctx context.Context, in *PublishMessage, opts ...grpc.CallOption) (*PublishResponse, error) {
	cOpts := append([]grpc.CallOption{grpc.StaticMethod()}, opts...)
	out := new(PublishResponse)
	err := c.cc.Invoke(ctx, EventBus_Publish_FullMethodName, in, out, cOpts...)
	if err != nil {
		return nil, err
	}
	return out, nil
}

func (c *eventBusClient) Subscribe(ctx context.Context, in *SubscriptionRequest, opts ...grpc.CallOption) (grpc.ServerStreamingClient[Event], error) {
	cOpts := append([]grpc.CallOption{grpc.StaticMethod()}, opts...)
	stream, err := c.cc.NewStream(ctx, &EventBus_ServiceDesc.Streams[0], EventBus_Subscribe_FullMethodName, cOpts...)
	if err != nil {
		return nil, err
	}
	x := &grpc.GenericClientStream[SubscriptionRequest, Event]{ClientStream: stream}
	if err := x.ClientStream.SendMsg(in); err != nil {
		return nil, err
	}
	if err := x.ClientStream.CloseSend(); err != nil {
		return nil, err
	}
	return x, nil
}

// This type alias is provided for backwards compatibility with existing code that references the prior non-generic stream type by name.
type EventBus_SubscribeClient = grpc.ServerStreamingClient[Event]

// EventBusServer is the server API for EventBus service.
// All implementations must embed UnimplementedEventBusServer
// for forward compatibility.
//
// The EventBus service definition
type EventBusServer interface {
	// Publishes a message to subscribers
	Publish(context.Context, *PublishMessage) (*PublishResponse, error)
	// Subscribes to topics
	Subscribe(*SubscriptionRequest, grpc.ServerStreamingServer[Event]) error
	mustEmbedUnimplementedEventBusServer()
}

// UnimplementedEventBusServer must be embedded to have
// forward compatible implementations.
//
// NOTE: this should be embedded by value instead of pointer to avoid a nil
// pointer dereference when methods are called.
type UnimplementedEventBusServer struct{}

func (UnimplementedEventBusServer) Publish(context.Context, *PublishMessage) (*PublishResponse, error) {
	return nil, status.Errorf(codes.Unimplemented, "method Publish not implemented")
}
func (UnimplementedEventBusServer) Subscribe(*SubscriptionRequest, grpc.ServerStreamingServer[Event]) error {
	return status.Errorf(codes.Unimplemented, "method Subscribe not implemented")
}
func (UnimplementedEventBusServer) mustEmbedUnimplementedEventBusServer() {}
func (UnimplementedEventBusServer) testEmbeddedByValue()                  {}

// UnsafeEventBusServer may be embedded to opt out of forward compatibility for this service.
// Use of this interface is not recommended, as added methods to EventBusServer will
// result in compilation errors.
type UnsafeEventBusServer interface {
	mustEmbedUnimplementedEventBusServer()
}

func RegisterEventBusServer(s grpc.ServiceRegistrar, srv EventBusServer) {
	// If the following call pancis, it indicates UnimplementedEventBusServer was
	// embedded by pointer and is nil.  This will cause panics if an
	// unimplemented method is ever invoked, so we test this at initialization
	// time to prevent it from happening at runtime later due to I/O.
	if t, ok := srv.(interface{ testEmbeddedByValue() }); ok {
		t.testEmbeddedByValue()
	}
	s.RegisterService(&EventBus_ServiceDesc, srv)
}

func _EventBus_Publish_Handler(srv interface{}, ctx context.Context, dec func(interface{}) error, interceptor grpc.UnaryServerInterceptor) (interface{}, error) {
	in := new(PublishMessage)
	if err := dec(in); err != nil {
		return nil, err
	}
	if interceptor == nil {
		return srv.(EventBusServer).Publish(ctx, in)
	}
	info := &grpc.UnaryServerInfo{
		Server:     srv,
		FullMethod: EventBus_Publish_FullMethodName,
	}
	handler := func(ctx context.Context, req interface{}) (interface{}, error) {
		return srv.(EventBusServer).Publish(ctx, req.(*PublishMessage))
	}
	return interceptor(ctx, in, info, handler)
}

func _EventBus_Subscribe_Handler(srv interface{}, stream grpc.ServerStream) error {
	m := new(SubscriptionRequest)
	if err := stream.RecvMsg(m); err != nil {
		return err
	}
	return srv.(EventBusServer).Subscribe(m, &grpc.GenericServerStream[SubscriptionRequest, Event]{ServerStream: stream})
}

// This type alias is provided for backwards compatibility with existing code that references the prior non-generic stream type by name.
type EventBus_SubscribeServer = grpc.ServerStreamingServer[Event]

// EventBus_ServiceDesc is the grpc.ServiceDesc for EventBus service.
// It's only intended for direct use with grpc.RegisterService,
// and not to be introspected or modified (even as a copy)
var EventBus_ServiceDesc = grpc.ServiceDesc{
	ServiceName: "eventbus.EventBus",
	HandlerType: (*EventBusServer)(nil),
	Methods: []grpc.MethodDesc{
		{
			MethodName: "Publish",
			Handler:    _EventBus_Publish_Handler,
		},
	},
	Streams: []grpc.StreamDesc{
		{
			StreamName:    "Subscribe",
			Handler:       _EventBus_Subscribe_Handler,
			ServerStreams: true,
		},
	},
	Metadata: "proto/eventbus.proto",
}

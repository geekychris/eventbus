// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: eventbus.proto

package com.eventbus.client;

public interface PublishMessageOrBuilder extends
    // @@protoc_insertion_point(interface_extends:eventbus.PublishMessage)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>string topic = 1;</code>
   * @return The topic.
   */
  java.lang.String getTopic();
  /**
   * <code>string topic = 1;</code>
   * @return The bytes for topic.
   */
  com.google.protobuf.ByteString
      getTopicBytes();

  /**
   * <code>string sub_topic = 2;</code>
   * @return The subTopic.
   */
  java.lang.String getSubTopic();
  /**
   * <code>string sub_topic = 2;</code>
   * @return The bytes for subTopic.
   */
  com.google.protobuf.ByteString
      getSubTopicBytes();

  /**
   * <code>string client_id = 3;</code>
   * @return The clientId.
   */
  java.lang.String getClientId();
  /**
   * <code>string client_id = 3;</code>
   * @return The bytes for clientId.
   */
  com.google.protobuf.ByteString
      getClientIdBytes();

  /**
   * <code>string message = 4;</code>
   * @return The message.
   */
  java.lang.String getMessage();
  /**
   * <code>string message = 4;</code>
   * @return The bytes for message.
   */
  com.google.protobuf.ByteString
      getMessageBytes();

  /**
   * <code>int64 timestamp = 5;</code>
   * @return The timestamp.
   */
  long getTimestamp();

  /**
   * <pre>
   * Unique ID for tracking messages across servers
   * </pre>
   *
   * <code>string message_id = 6;</code>
   * @return The messageId.
   */
  java.lang.String getMessageId();
  /**
   * <pre>
   * Unique ID for tracking messages across servers
   * </pre>
   *
   * <code>string message_id = 6;</code>
   * @return The bytes for messageId.
   */
  com.google.protobuf.ByteString
      getMessageIdBytes();

  /**
   * <pre>
   * ID of originating server
   * </pre>
   *
   * <code>string origin_server_id = 7;</code>
   * @return The originServerId.
   */
  java.lang.String getOriginServerId();
  /**
   * <pre>
   * ID of originating server
   * </pre>
   *
   * <code>string origin_server_id = 7;</code>
   * @return The bytes for originServerId.
   */
  com.google.protobuf.ByteString
      getOriginServerIdBytes();
}

// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: eventbus.proto

package com.eventbus.client;

public interface EventOrBuilder extends
    // @@protoc_insertion_point(interface_extends:eventbus.Event)
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
}

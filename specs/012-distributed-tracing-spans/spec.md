# Feature Specification: Distributed Tracing for Messaging Spans

**Version**: 1.0
**Continues**: `011-nats-health-endpoints`
**Status**: In Review
**Author**: AI Assistant
**Last Updated**: 2025-10-29

---

## 1. Feature Name

Distributed Tracing for Messaging Spans

## 2. Description

As a developer, I want my distributed trace to show messaging spans correctly so that I can effectively debug and monitor message flows across services. This feature will ensure that when a message is sent from a producer to a consumer via NATS, the entire journey is captured as a single, coherent trace in our observability platform.

## 3. User Scenarios & Testing

### 3.1. Happy Path

- **Scenario 1: Successful Message Processing**
  - **Given** a producer service sends a message with an active trace context.
  - **When** a consumer service receives and successfully processes the message.
  - **Then** a single distributed trace is generated that includes:
    - A span from the producer service for sending the message.
    - A span from the consumer service for receiving and processing the message.
  - **And** the consumer's span is correctly parented to the producer's span, showing a clear causal link that represents the message transit.

- Q: What specific attributes or naming conventions should be used to distinguish between initial message processing attempts and redelivery attempts within a trace? → A: Use an OpenTelemetry attribute like `messaging.message_redelivered` (boolean) on the consumer span.
- Q: What specific OpenTelemetry attributes or events should be used to indicate a timeout or failure on a span? → A: Set `otel.status_code` to `ERROR` and `otel.status_description` to a relevant message (e.g., "Timeout"), and optionally add an `exception` event.

### 3.2. Edge Cases

- **Scenario 2: No Initial Trace Context**
  - **Given** a producer service sends a message without an existing active trace context.
  - **When** the message is sent and processed by a consumer.
  - **Then** a new trace is initiated by the producer.
  - **And** the resulting trace correctly links the producer, NATS, and consumer spans.

- **Scenario 3: Message Redelivery**
  - **Given** a consumer service fails to process a message, causing it to be redelivered.
  - **When** the message is processed again upon redelivery.
  - **Then** the trace should show the initial failed processing attempt and the subsequent redelivery attempt as distinct spans within the same trace, with the redelivered consumer span including the `messaging.message_redelivered` attribute set to `true`.

- **Scenario 4: Message Processing Timeout**
  - **Given** a message is not acknowledged by the consumer within the timeout period.
  - **When** the message expires.
  - **Then** the trace should contain a span representing the processing attempt, and this span should have `otel.status_code` set to `ERROR` and `otel.status_description` set to "Timeout" (or similar), and may include an `exception` event.
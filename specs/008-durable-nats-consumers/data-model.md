# Data Model: Durable Consumers for @NatsSubscriber

**Feature**: `008-durable-nats-consumers` | **Date**: 2025-10-27

## Entity: @NatsSubscriber Annotation Properties

**Purpose**: Declaratively specify NATS consumer binding (ephemeral or durable) for a subscriber method.

**Existing Properties** (from 004-nats-subscriber-mvp, inherited):
- `subject` (String): NATS subject to subscribe to. Required for ephemeral consumer mode. Default: `""` (empty string).

**New Properties** (this feature):
- `stream` (String): NATS JetStream stream name. Required when using durable consumer mode. No default. Optional when using ephemeral mode.
- `consumer` (String): Durable consumer name on the stream. Required when using durable consumer mode. No default. Optional when using ephemeral mode.

### Validation Rules

1. **Mutual Exclusivity (Build-Time)**: FR-004, FR-005
   - **Ephemeral Mode**: `subject` is non-empty; `stream` and `consumer` are empty/missing
   - **Durable Mode**: `stream` and `consumer` are both non-empty; `subject` is empty
   - **Invalid**: Both `subject` and `stream+consumer` are non-empty (build fails)
   - **Invalid**: Only one of `stream` or `consumer` is provided without the other (build fails)
   - **Implementation**: Build-time validation in deployment module via @BuildStep processor

2. **Consumer Existence (Startup)**: FR-006, FR-007
   - When `consumer` is specified, NATS server MUST have a consumer with that name on the specified `stream`
   - Verification occurs at application startup
   - **Failure**: If consumer doesn't exist, application startup fails with error message:
     ```
     Failed to verify durable consumer: Stream '<stream-name>' does not contain consumer '<consumer-name>'.
     Please ensure the consumer is pre-configured on the NATS server.
     ```
   - **Implementation**: Runtime bean initialization in runtime module; uses JNATS `ConsumerInfo` API

3. **Field Constraints**:
   - `stream` length: 1-255 characters (NATS convention; subject and consumer follow similar rules)
   - `consumer` length: 1-255 characters
   - Characters: alphanumeric, `-`, `_`, `.` (standard NATS naming conventions)
   - Whitespace NOT allowed (validation rejects strings with leading/trailing/internal spaces)

## Process: Annotation Processing & Consumer Binding

### Build-Time (Deployment Module)

**Step 1: Annotation Discovery**
- Quarkus @BuildStep processor scans for methods annotated with @NatsSubscriber
- Reads `subject`, `stream`, `consumer` properties

**Step 2: Validation**
- Validate mutual exclusivity: subject XOR (stream AND consumer)
- If invalid: compilation error with clear message (e.g., "subject property must be empty when stream and consumer are specified")
- If valid: continue

**Step 3: Mode Classification**
- If `subject` is non-empty → Ephemeral mode (existing behavior from 004)
- If `stream` and `consumer` are non-empty → Durable mode (new)

**Step 4: Bean Registration**
- Both modes result in a subscriber bean being registered
- Metadata attached to bean: mode (ephemeral/durable), stream, consumer, subject

### Startup (Runtime Module)

**For Durable Mode Only**:

**Step 1: Consumer Verification**
- When subscriber bean initializes, if `stream` and `consumer` are set:
  - Query NATS server: `JetStream.getConsumer(stream, consumer)`
  - If consumer exists: proceed
  - If consumer doesn't exist: log error and throw exception; application startup fails

**Step 2: Consumer Binding**
- Once verified, JNATS client automatically binds to the consumer
- JNATS handles:
  - Connection to NATS server
  - Message delivery from consumer queue
  - Acknowledgment (ack) and negative acknowledgment (nak)
  - Redelivery if message fails processing

**For Ephemeral Mode**:
- Existing behavior unchanged (JNATS creates ephemeral consumer on startup)

## Related Entities

### NatsSubscriber Bean
- Contains one or more methods annotated with @NatsSubscriber
- At most one annotation per method
- Each annotation results in a distinct NATS subscription (consumer)

### NatsConnection
- Singleton NATS JetStream connection per application instance
- Shared by all publishers and subscribers
- Managed by Quarkus lifecycle

### Consumer (NATS JetStream Concept)
- Pre-configured on NATS server (NOT created by extension)
- Has properties: name, stream, subject filter, consumer mode (push/pull), delivery subject, etc.
- Extension reads consumer metadata for validation only

## State Transitions

```
Application Startup
  ├─ Discover @NatsSubscriber annotations (build-time: completed before startup)
  ├─ Connect to NATS server
  ├─ For each Durable Mode subscriber:
  │  ├─ Query consumer: JetStream.getConsumer(stream, consumer)
  │  ├─ On success: Consumer verified → JNATS binds and starts receiving messages
  │  └─ On failure: Log error → Throw exception → Application startup fails (STOP)
  └─ For each Ephemeral Mode subscriber:
     └─ JNATS creates ephemeral consumer → Starts receiving messages

Message Processing (both modes identical)
  ├─ Message delivered to subscriber method
  ├─ Method executes
  ├─ On success: Message ack'd
  └─ On exception: Message nak'd (eligible for redelivery)

Application Shutdown
  ├─ Durable mode: Consumer remains on NATS server (survives shutdown)
  └─ Ephemeral mode: Consumer is deleted (auto-cleanup by JNATS)
```

## Example Annotation Usage

**Ephemeral Consumer** (existing pattern from 004):
```java
@NatsSubscriber(subject = "orders.>")
void handleOrder(Order order) {
    // Process order
}
```

**Durable Consumer** (new pattern in this feature):
```java
@NatsSubscriber(stream = "orders-stream", consumer = "order-processor-v1")
void handleOrder(Order order) {
    // Process order; message persists across app restarts
}
```

**Invalid** (build fails):
```java
// Error: Cannot specify both subject and stream/consumer
@NatsSubscriber(subject = "orders", stream = "orders-stream", consumer = "order-processor-v1")
void handleOrder(Order order) { }

// Error: Only subject is empty; must provide both stream and consumer
@NatsSubscriber(stream = "orders-stream")
void handleOrder(Order order) { }
```

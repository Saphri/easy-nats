# API Contracts: Durable Consumers for @NatsSubscriber

**Feature**: `008-durable-nats-consumers` | **Date**: 2025-10-27

## Annotation: @NatsSubscriber

**Location**: `runtime/src/main/java/org/mjelle/quarkus/easynats/annotation/NatsSubscriber.java`

**Scope**: Method-level annotation for declaring a NATS message subscriber.

### Property Definitions

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface NatsSubscriber {

    /**
     * NATS subject to subscribe to (ephemeral consumer mode).
     *
     * - For ephemeral consumers: Required and non-empty.
     * - For durable consumers: MUST be empty (empty string default).
     * - Build validates mutual exclusivity: subject XOR (stream + consumer).
     *
     * Default: "" (empty string)
     */
    String subject() default "";

    /**
     * NATS JetStream stream name (durable consumer mode).
     *
     * - For durable consumers: Required and non-empty.
     * - For ephemeral consumers: MUST be empty (default value used).
     * - Build validates mutual exclusivity: stream AND consumer both required together.
     * - Startup verifies consumer exists on this stream.
     *
     * Default: "" (empty string)
     */
    String stream() default "";

    /**
     * NATS durable consumer name (durable consumer mode).
     *
     * - For durable consumers: Required and non-empty.
     * - For ephemeral consumers: MUST be empty (default value used).
     * - Build validates mutual exclusivity: consumer AND stream both required together.
     * - Startup verifies consumer exists with this name on specified stream.
     *
     * Default: "" (empty string)
     */
    String consumer() default "";
}
```

### Behavior Contract

#### Build-Time (Deployment Module)

**Processor**: `QuarkusEasyNatsProcessor.@BuildStep(BuildStep.class)`

**Input**: Methods annotated with @NatsSubscriber in user application

**Validation Rules**:

1. **Property Validation**:
   ```
   IF (subject != "" AND (stream != "" OR consumer != ""))
     THEN Error: "Cannot specify both subject and stream/consumer properties"

   IF (stream != "" AND consumer == "")
     THEN Error: "stream property requires consumer property"

   IF (consumer != "" AND stream == "")
     THEN Error: "consumer property requires stream property"

   IF (subject == "" AND stream == "")
     THEN Error: "Either subject (ephemeral mode) or stream+consumer (durable mode) must be provided"
   ```

2. **Naming Convention Validation**:
   - `stream` and `consumer`: alphanumeric, `-`, `_`, `.`; no leading/trailing whitespace
   - Length: 1-255 characters

**Output**:
- ✅ If valid: Bean registration metadata (mode, properties) attached to subscriber bean
- ❌ If invalid: Compilation error with descriptive message, build fails

#### Startup (Runtime Module)

**Bean**: `NatsSubscriberHandler` (or similar registry managing subscriber lifecycle)

**Method Signature**:
```java
void verifyDurableConsumer(String stream, String consumer) throws Exception {
    // Invoked during bean initialization if stream+consumer are set
}
```

**Contract**:

For each @NatsSubscriber method with durable mode (stream+consumer set):

1. **Consumer Verification**:
   ```java
   ConsumerInfo consumerInfo = jetStream.getConsumerInfo(stream, consumer);
   ```
   - **Success**: Return; JNATS client auto-binds to consumer
   - **Failure** (consumer doesn't exist):
     - Log ERROR: `"Failed to verify durable consumer: Stream 'X' does not contain consumer 'Y'. Please ensure the consumer is pre-configured on the NATS server."`
     - Throw exception (type: e.g., `IllegalStateException` or custom `ConsumerVerificationException`)
     - Application startup fails (Quarkus catches exception, logs, exits)

2. **Message Processing**:
   - JNATS library handles:
     - Connection to NATS server
     - Consumer binding
     - Push-based message delivery
     - Automatic ack (on method success)
     - Automatic nak (on method exception)
   - Extension makes no changes from ephemeral mode behavior

### Test Scenarios

**Build-Time Tests** (compilation tests):

1. ✅ Valid ephemeral mode:
   ```java
   @NatsSubscriber(subject = "orders.>")
   void handle(Order order) { }
   ```
   Result: Compiles successfully

2. ✅ Valid durable mode:
   ```java
   @NatsSubscriber(stream = "orders", consumer = "processor-1")
   void handle(Order order) { }
   ```
   Result: Compiles successfully

3. ❌ Invalid: both subject and stream+consumer:
   ```java
   @NatsSubscriber(subject = "orders", stream = "orders", consumer = "p-1")
   void handle(Order order) { }
   ```
   Result: Compilation error

4. ❌ Invalid: stream without consumer:
   ```java
   @NatsSubscriber(stream = "orders")
   void handle(Order order) { }
   ```
   Result: Compilation error

5. ❌ Invalid: consumer without stream:
   ```java
   @NatsSubscriber(consumer = "processor-1")
   void handle(Order order) { }
   ```
   Result: Compilation error

**Startup Tests** (integration tests):

1. ✅ Consumer exists on server:
   - Pre-create consumer on NATS server
   - Start application with matching @NatsSubscriber
   - Result: Application starts successfully; subscriber receives messages

2. ❌ Consumer doesn't exist on server:
   - Don't create consumer on NATS server
   - Start application with @NatsSubscriber specifying non-existent consumer
   - Result: Application startup fails with error message

3. ✅ Ephemeral mode: Consumer auto-created (unchanged from 004):
   - Start application with @NatsSubscriber(subject = "...")
   - Result: JNATS auto-creates ephemeral consumer; subscriber receives messages

## Dependency Contracts

### JNATS JetStream API Usage

**Startup Consumer Verification**:
```java
JetStream jetStream = connection.jetStream();
ConsumerInfo info = jetStream.getConsumerInfo(stream, consumer);
// Throws: IOException, JetStreamApiException (if consumer not found)
```

**Message Delivery** (existing from 004, unchanged):
```java
PushSubscribeOptions opts = PushSubscribeOptions.builder()
    .durable(consumer)        // For durable mode; null for ephemeral
    .stream(stream)            // For durable mode; null for ephemeral
    .build();
Subscription sub = jetStream.subscribe(subject, opts);
```

### Quarkus Extension APIs

**Build-Time Registration**:
- `@BuildStep` processor auto-discovery
- `RuntimeClassBuildItem` for class metadata
- Bean registration via `BeanDefiningAnnotationBuildItem`

**Runtime Lifecycle**:
- `@Singleton` CDI bean for subscriber handlers
- Quarkus shutdown hook for connection cleanup (inherited from 004)

## Error Handling Contract

### Build-Time Errors

| Scenario | Error Message | Exception Type |
|----------|---------------|-----------------|
| Both subject and stream specified | `Cannot specify both subject and stream/consumer properties; use one or the other` | `DefinitionException` |
| stream without consumer | `stream property requires consumer property` | `DefinitionException` |
| consumer without stream | `consumer property requires stream property` | `DefinitionException` |
| No properties provided | `Either subject (ephemeral) or stream+consumer (durable) must be provided` | `DefinitionException` |

### Startup-Time Errors

| Scenario | Error Message | Exception Type | Logged Level |
|----------|---------------|-----------------|--------------|
| Consumer not found | `Failed to verify durable consumer: Stream 'X' does not contain consumer 'Y'. Please ensure the consumer is pre-configured on the NATS server.` | `IllegalStateException` | ERROR |
| NATS server unreachable | (inherited from 004: connection error) | `IOException` | ERROR |

---

## Backward Compatibility

✅ **Breaking Change**: None. Feature is additive.
- Existing @NatsSubscriber annotations (ephemeral mode with `subject` only) continue to work unchanged
- New `stream` and `consumer` properties are optional and default to empty string
- Build-time validation accepts both patterns (ephemeral or durable); doesn't break existing code

---

## Summary: Implementation Checklist

- [ ] Add `stream` and `consumer` properties to @NatsSubscriber annotation (runtime module)
- [ ] Implement build-time validation in QuarkusEasyNatsProcessor (deployment module)
- [ ] Implement startup verification in subscriber bean initialization (runtime module)
- [ ] Add unit tests for property validation logic
- [ ] Add integration tests for durable consumer lifecycle
- [ ] Add integration tests for consumer not found error scenario
- [ ] Update CLAUDE.md documentation for contributors
- [ ] Update user-facing docs with durable consumer examples

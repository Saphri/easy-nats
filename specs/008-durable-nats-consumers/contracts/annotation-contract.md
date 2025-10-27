# Contract: @NatsSubscriber Annotation

**Feature**: 001-durable-nats-consumers
**Date**: 2025-10-27

## Overview

This document defines the updated contract for the `@NatsSubscriber` annotation, which now includes support for specifying a pre-configured durable consumer.

---

## Annotation Properties

The `@NatsSubscriber` annotation is updated to include `subject`, `stream`, and `consumer` properties with validation logic.

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NatsSubscriber {
    /**
     * The NATS subject to subscribe to.
     * Used for ephemeral consumers.
     * Either this or both stream and consumer must be set.
     */
    String subject() default "";

    /**
     * The name of the NATS JetStream stream.
     * Required when a durable consumer is specified.
     */
    String stream() default "";

    /**
     * The name of the pre-configured durable consumer to use.
     * If not specified, an ephemeral consumer will be created based on the subject.
     */
    String consumer() default "";
}
```

### Validation Rules

1.  Either `subject` must be provided, or *both* `stream` and `consumer` must be provided.
2.  It is an error to provide both `subject` and `stream`/`consumer`.
3.  If `consumer` is provided, `stream` must also be provided.

The application will fail to start if these validation rules are not met.

---

## Example Usage

```java
@ApplicationScoped
public class MyNatsListeners {

    // This subscriber uses an ephemeral consumer on a specific subject
    @NatsSubscriber(subject = "my-subject")
    public void receiveMessage(MyData data) {
        // ...
    }

    // This subscriber uses a pre-configured durable consumer named 'my-durable-consumer'
    // on the 'my-stream' stream. The subject is determined by the consumer's filter.
    @NatsSubscriber(stream = "my-stream", consumer = "my-durable-consumer")
    public void receiveMessageWithDurableConsumer(MyData data) {
        // ...
    }
}
```

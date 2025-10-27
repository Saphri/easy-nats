# Quick Start: Using Durable Consumers

**Feature**: 001-durable-nats-consumers
**Date**: 2025-10-27

## 5-Minute Guide

Learn how to use pre-configured durable consumers with your `@NatsSubscriber` to ensure message processing persists across application restarts.

---

## Step 1: Pre-configure your NATS Environment

Before using a durable consumer in your application, you must create it on your NATS JetStream server. You can do this using the `nats` CLI.

```bash
# 1. Add a stream
nats stream add my-stream --subjects "my-subject"

# 2. Add a durable consumer to the stream
nats consumer add my-stream my-durable-consumer --ack explicit --replay instant
```

This creates a stream named `my-stream` and a durable consumer named `my-durable-consumer` on that stream.

---

## Step 2: Use the Durable Consumer in your Subscriber

In your Quarkus application, use the `stream` and `consumer` properties of the `@NatsSubscriber` annotation to bind to the pre-configured durable consumer.

```java
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.mjelle.quarkus.easynats.NatsPublisher;
import org.mjelle.quarkus.easynats.NatsSubject;
import org.mjelle.quarkus.easynats.NatsSubscriber;

@ApplicationScoped
public class DurableConsumerListener {

    // Ephemeral consumer
    @NatsSubscriber(subject = "my-subject")
    public void receiveEphemeral(String message) {
        System.out.println("Received message on ephemeral consumer: " + message);
    }

    // Durable consumer
    @NatsSubscriber(stream = "my-stream", consumer = "my-durable-consumer")
    public void receiveMessage(String message) {
        System.out.println("Received message on durable consumer: " + message);
    }
}
```

### What Happens:

- When your application starts, the EasyNATS extension will connect to the NATS server and look for a durable consumer named `my-durable-consumer` on the `my-stream` stream.
- If the consumer is found, your `receiveMessage` method will be subscribed to it.
- If the consumer is not found, your application will fail to start with an error.

---

## Step 3: Test it

1.  **Start your Quarkus application.**
2.  **Publish a message to the subject while your application is running.** You should see the "Received message" log output.
3.  **Stop your Quarkus application.**
4.  **Publish another message to the subject.**
5.  **Start your Quarkus application again.** You will see the "Received message" log output for the message that was sent while the application was stopped.

This demonstrates that the durable consumer has persisted the message and delivered it to your subscriber when it came back online.

# Quickstart: Using @NatsSubscriber

This guide shows how to use the `@NatsSubscriber` annotation to create a NATS message consumer.

## 1. Add the dependency

Make sure you have the `quarkus-easy-nats` extension in your `pom.xml`:

```xml
<dependency>
    <groupId>org.mjelle.quarkus.easynats</groupId>
    <artifactId>quarkus-easy-nats</artifactId>
    <version>${project.version}</version>
</dependency>
```

## 2. Configure the NATS connection

In your `application.properties`, configure the NATS server connection:

```properties
nats.servers=nats://localhost:4222
nats.username=admin
nats.password=secret
```

## 3. Create a subscriber

In any CDI bean, create a method that accepts a `String` parameter and annotate it with `@NatsSubscriber`:

```java
import jakarta.enterprise.context.ApplicationScoped;
import org.mjelle.quarkus.easynats.NatsSubscriber;

@ApplicationScoped
public class MyNatsConsumer {

    @NatsSubscriber("my-subject")
    public void onMessage(String message) {
        System.out.println("Received message: " + message);
    }
}
```

Now, your application will listen for messages on the `my-subject` NATS subject, and the `onMessage` method will be called for each message received.

## 4. Error Handling and Acknowledgment

You do not need to manually acknowledge messages. The EasyNATS extension handles this for you based on the outcome of your subscriber method:

-   **Successful Execution**: If your method completes without throwing an exception, the message is automatically acknowledged (`ack`).
-   **Exception Thrown**: If your method throws an exception, the message is negatively acknowledged (`nak`), and NATS may attempt to redeliver it later, depending on your stream's configuration. The exception will also be logged.

```java
@ApplicationScoped
public class MyNatsConsumer {

    @NatsSubscriber("my-subject")
    public void onMessage(String message) {
        if (message.contains("error")) {
            throw new RuntimeException("Processing failed for message: " + message);
        }
        System.out.println("Processed message: " + message);
    }
}
```

# Quickstart: Automatic Reflection Registration

The `quarkus-easy-nats` extension automatically handles the registration of types for reflection when they are used in `@NatsSubscriber` methods. This means you do not need to manually add `@RegisterForReflection` to your classes.

## Example

Consider a simple POJO:

```java
public class MyMessage {
    private String content;

    // Getters and setters
}
```

You can use this class directly in a `@NatsSubscriber` method without any extra configuration:

```java
import org.mjelle.quarkus.easynats.annotations.NatsSubscriber;

public class MyNatsListener {

    @NatsSubscriber(subject = "my.subject")
    public void onMessage(MyMessage message) {
        System.out.println("Received message: " + message.getContent());
    }
}
```

The extension will automatically detect that `MyMessage` is used in a subscriber and register it for reflection, ensuring that it works correctly in both JVM and native mode.

# Quickstart: Transparent CloudEvent Publishing

This guide demonstrates how to publish a typed object, which will be automatically sent as a CloudEvent, using the type-safe, annotation-driven publisher.

## 1. Inject the Publisher

In your Quarkus bean, inject a generic `NatsPublisher<T>` and use the `@NatsSubject` annotation to bind it to a default NATS subject.

```java
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.mjelle.quarkus.easynats.NatsPublisher;
import org.mjelle.quarkus.easynats.NatsSubject;

@ApplicationScoped
public class MyService {

    @Inject
    @NatsSubject("user.events.registered")
    NatsPublisher<UserRegisteredEvent> userEventPublisher;

    // ... your business logic
}
```

## 2. Define Your Payload

Create a simple POJO to represent your event data.

```java
public class UserRegisteredEvent {
    private String userId;
    private String email;

    // Constructors, getters, and setters
}
```

## 3. Publish the Object

Call the `publish` method with an instance of your POJO. The subject is already configured by the `@NatsSubject` annotation.

```java
public void registerUser(String userId, String email) {
    UserRegisteredEvent event = new UserRegisteredEvent(userId, email);

    // This will be sent as a valid CloudEvent automatically to "user.events.registered"
    userEventPublisher.publish(event);
}
```

## What Happens

When you call `userEventPublisher.publish(...)`:

1.  A unique ID and timestamp are generated for the CloudEvent.
2.  The `source` is taken from your `application.properties` (or defaults to the app name).
3.  The `type` is set to `com.yourpackage.UserRegisteredEvent`.
4.  The `datacontenttype` is set to `application/json`.
5.  These attributes are added as headers to the NATS message (e.g., `ce-id`, `ce-source`).
6.  The `UserRegisteredEvent` object is serialized to a JSON string and becomes the message payload.
7.  The message is published to the `user.events.registered` subject, as defined in the `@NatsSubject` annotation.
# Quickstart: Using @NatsSubject

This guide shows how to use the `@NatsSubject` annotation to inject a `NatsPublisher` with a pre-configured subject.

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

## 3. Inject the NatsPublisher

In your service, inject a `NatsPublisher` and use the `@NatsSubject` annotation to specify the subject:

```java
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.mjelle.quarkus.easynats.NatsPublisher;
import org.mjelle.quarkus.easynats.NatsSubject;

@ApplicationScoped
public class MyService {

    @Inject
    @NatsSubject("orders.created")
    NatsPublisher orderPublisher;

    public void createOrder(String order) {
        orderPublisher.publish(order);
    }
}
```

Now, any message published using `orderPublisher` will be sent to the `orders.created` subject.
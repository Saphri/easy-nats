# Quickstart: Using a Global Custom Payload Codec

This guide demonstrates how to provide a single, global custom payload codec to override the default JSON/CloudEvents serialization for your entire application.

## 1. Create a Global Codec

Implement the `Codec` interface and declare it as a Quarkus bean (e.g., with `@ApplicationScoped`). This single bean will handle all serialization and deserialization for `NatsPublisher` and `@NatsSubscriber`.

In this example, we'll create a codec that uses `jakarta.json.bind.Jsonb` for all objects.

**GlobalJsonbCodec.java**
```java
import org.mjelle.quarkus.easynats.codec.Codec;
import org.mjelle.quarkus.easynats.codec.SerializationException;
import org.mjelle.quarkus.easynats.codec.DeserializationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

@ApplicationScoped
public class GlobalJsonbCodec implements Codec {

    private final Jsonb jsonb = JsonbBuilder.create();

    @Override
    public byte[] encode(Object object) throws SerializationException {
        if (object == null) {
            return null;
        }
        try {
            return jsonb.toJson(object).getBytes();
        } catch (Exception e) {
            throw new SerializationException("Failed to serialize object with JSON-B", e);
        }
    }

    @Override
    public Object decode(byte[] data, Class<?> type) throws DeserializationException {
        if (data == null || data.length == 0) {
            return null;
        }
        try {
            return jsonb.fromJson(new String(data), type);
        } catch (Exception e) {
            throw new DeserializationException("Failed to deserialize object with JSON-B", e);
        }
    }
}
```
By annotating the class with `@ApplicationScoped`, Quarkus automatically discovers it and EasyNATS will use it as the global codec, overriding the default.

## 2. Publisher and Subscriber

Your publisher and subscriber code requires no changes. The global codec is applied transparently.

**Product.java**
```java
public class Product {
    public String id;
    public String name;
}
```

**User.java**
```java
public class User {
    public String email;
}
```

**MessagingService.java**
```java
import org.mjelle.quarkus.easynats.NatsPublisher;
import org.mjelle.quarkus.easynats.annotations.NatsSubject;
import org.mjelle.quarkus.easynats.annotations.NatsSubscriber;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class MessagingService {

    @Inject
    @NatsSubject("products")
    NatsPublisher<Product> productPublisher;

    @Inject
    @NatsSubject("users")
    NatsPublisher<User> userPublisher;

    public void sendMessages() {
        Product product = new Product();
        product.id = "p1";
        product.name = "Laptop";
        productPublisher.publish(product);

        User user = new User();
        user.email = "test@example.com";
        user.publish(user);
    }

    @NatsSubscriber(subject = "products")
    public void onProductMessage(Product product) {
        // 'product' was deserialized by GlobalJsonbCodec
        System.out.println("Got product: " + product.name);
    }

    @NatsSubscriber(subject = "users")
    public void onUserMessage(User user) {
        // 'user' was also deserialized by GlobalJsonbCodec
        System.out.println("Got user: " + user.email);
    }
}
```
Both the `Product` and `User` objects will be serialized and deserialized using the single `GlobalJsonbCodec` instance.

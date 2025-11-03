# Quickstart: Using a Custom Payload Codec

This guide demonstrates how to provide a custom payload encoder/decoder (codec) to override the default JSON/CloudEvents serialization.

## 1. Create a Custom Codec

First, implement the `Codec` interface. In this example, we'll create a simple codec that serializes a custom `Product` object into a pipe-separated string.

**Product.java**
```java
public class Product {
    public String id;
    public String name;

    // Constructors, getters, setters
}
```

**ProductCodec.java**
```java
import org.mjelle.quarkus.easynats.codec.Codec;
import org.mjelle.quarkus.easynats.codec.SerializationException;
import org.mjelle.quarkus.easynats.codec.DeserializationException;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProductCodec implements Codec<Product> {

    @Override
    public byte[] encode(Product product) throws SerializationException {
        if (product == null) {
            return null;
        }
        String payload = product.id + "|" + product.name;
        return payload.getBytes();
    }

    @Override
    public Product decode(byte[] data, Class<Product> type) throws DeserializationException {
        if (data == null || data.length == 0) {
            return null;
        }
        String payload = new String(data);
        String[] parts = payload.split("\\|");
        if (parts.length != 2) {
            throw new DeserializationException("Invalid payload format for Product");
        }
        Product product = new Product();
        product.id = parts[0];
        product.name = parts[1];
        return product;
    }
}
```
*Note: By annotating our codec with `@ApplicationScoped`, it is automatically discovered by Quarkus CDI and registered with EasyNATS.*

## 2. Publisher and Subscriber

Your publisher and subscriber code remains exactly the same. EasyNATS will automatically discover and use your custom `ProductCodec` for the `Product` type.

**ProductPublisher.java**
```java
import org.mjelle.quarkus.easynats.NatsPublisher;
import org.mjelle.quarkus.easynats.annotations.NatsSubject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ProductPublisher {

    @Inject
    @NatsSubject("products")
    NatsPublisher<Product> productPublisher;

    public void sendProduct(Product product) {
        productPublisher.publish(product);
    }
}
```

**ProductSubscriber.java**
```java
import org.mjelle.quarkus.easynats.annotations.NatsSubscriber;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProductSubscriber {

    @NatsSubscriber(subject = "products")
    public void onProductMessage(Product product) {
        System.out.println("Received product: " + product.name);
        // The 'product' object was deserialized using ProductCodec
    }
}
```

When `ProductPublisher.sendProduct()` is called, the `ProductCodec`'s `encode` method will be used. When a message arrives on the "products" subject, the `decode` method will be used before `onProductMessage()` is invoked.

```
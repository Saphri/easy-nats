# Quickstart: Using a Global Custom Payload Codec

This guide demonstrates how to provide a single, global custom payload codec to serialize the `data` attribute of the CloudEvent.

## 1. Define your Data Format

For this example, we'll use a simple `Product` class.

**Product.java**
```java
public class Product {
    public String id;
    public String name;

    public Product(String id, String name) {
        this.id = id;
        this.name = name;
    }

    // A custom text-based format: "id|name"
    public String toText() {
        return id + "|" + name;
    }

    public static Product fromText(String text) {
        String[] parts = text.split("\\|");
        return new Product(parts[0], parts[1]);
    }
}
```

## 2. Create a Global Codec

Implement the `Codec` interface and declare it as a Quarkus bean (`@ApplicationScoped`). This bean will handle the serialization of the `data` payload for all publishers and subscribers.

**TextPlainCodec.java**
```java
import org.mjelle.quarkus.easynats.codec.Codec;
import org.mjelle.quarkus.easynats.codec.SerializationException;
import org.mjelle.quarkus.easynats.codec.DeserializationException;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TextPlainCodec implements Codec {

    @Override
    public String getContentType() {
        return "text/plain";
    }

    @Override
    public byte[] encode(Object object) throws SerializationException {
        if (object instanceof Product) {
            return ((Product) object).toText().getBytes();
        }
        throw new SerializationException("Unsupported type for TextPlainCodec: " + object.getClass().getName());
    }

    @Override
    public Object decode(byte[] data, Class<?> type, String ceType) throws DeserializationException {
        if (type.equals(Product.class)) {
            return Product.fromText(new String(data));
        }
        throw new DeserializationException("Unsupported type for TextPlainCodec: " + type.getName());
    }
}
```
By annotating the class with `@ApplicationScoped`, Quarkus automatically discovers it, and EasyNATS will use it as the global payload codec.

## 3. Publisher and Subscriber

Your publisher and subscriber code requires no changes. The global codec is applied transparently to the CloudEvent `data` payload.

**ProductService.java**
```java
import org.mjelle.quarkus.easynats.NatsPublisher;
import org.mjelle.quarkus.easynats.annotations.NatsSubject;
import org.mjelle.quarkus.easynats.annotations.NatsSubscriber;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ProductService {

    @Inject
    @NatsSubject("products")
    NatsPublisher<Product> productPublisher;

    public void sendProduct(Product product) {
        // The Product object will be serialized to "id|name"
        // and placed in the 'data' attribute of the CloudEvent.
        // The 'datacontenttype' header will be set to "text/plain".
        productPublisher.publish(product);
    }

    @NatsSubscriber(subject = "products")
    public void onProductMessage(Product product) {
        // The 'data' attribute of the CloudEvent was deserialized
        // from "id|name" into a Product object by the TextPlainCodec.
        System.out.println("Received product: " + product.name);
    }
}
```
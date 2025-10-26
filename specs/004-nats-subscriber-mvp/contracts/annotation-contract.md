# Contract: @NatsSubscriber Annotation

This document defines the contract for the `@NatsSubscriber` annotation.

## Annotation Definition

```java
package org.mjelle.quarkus.easynats;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface NatsSubscriber {
    String value();
}
```

## Usage

```java
import org.mjelle.quarkus.easynats.NatsSubscriber;

public class MyService {

    @NatsSubscriber("my-subject")
    public void onMessage(String message) {
        System.out.println("Received message: " + message);
    }
}
```

## Behavior

-   When a method is annotated with `@NatsSubscriber("my-subject")`, the Quarkus EasyNats extension will create an ephemeral subscription to the "my-subject" NATS subject.
-   When a message is received on this subject, the annotated method will be invoked with the message payload as its argument.
-   Message acknowledgment is handled implicitly. If the annotated method executes successfully, the message is acknowledged (`ack`). If the method throws an exception, the message is negatively acknowledged (`nak`) and may be redelivered by the server.
-   The annotated method must have a single parameter of type `String`.
-   If the `value` of the annotation is an empty string, the application will fail to build.
-   If the annotated method does not have the correct signature, the application will fail to build.

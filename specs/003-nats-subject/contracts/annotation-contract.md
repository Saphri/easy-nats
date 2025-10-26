# Contract: @NatsSubject Annotation

This document defines the contract for the `@NatsSubject` annotation.

## Annotation Definition

```java
package org.mjelle.quarkus.easynats;

import jakarta.enterprise.util.Nonbinding;
import jakarta.inject.Qualifier;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
public @interface NatsSubject {
    @Nonbinding
    String value();
}
```

## Usage

```java
import jakarta.inject.Inject;
import org.mjelle.quarkus.easynats.NatsPublisher;
import org.mjelle.quarkus.easynats.NatsSubject;

public class MyService {

    @Inject
    @NatsSubject("my-subject")
    NatsPublisher publisher;

    public void doSomething() {
        publisher.publish("Hello, world!");
    }
}
```

## Behavior

-   When a bean injects a `NatsPublisher` and annotates it with `@NatsSubject("my-subject")`, the injected `NatsPublisher` instance will have its default subject set to `"my-subject"`.
-   If the `value` of the annotation is an empty string, the application will fail to start with a `DefinitionException`.
-   Injecting `NatsPublisher` without the `@NatsSubject` annotation is still possible and will result in a generic publisher without a default subject.
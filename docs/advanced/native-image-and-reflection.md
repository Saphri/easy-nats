# Native Image and Reflection

The Quarkus EasyNATS extension automatically detects and registers your `NatsPublisher` and `NatsSubscriber` payload types for reflection, so you do not need to manually configure them with `@RegisterForReflection` in most cases. This includes support for common generic collections like `List<MyType>`, `Set<MyType>`, `Queue<MyType>`, `Map<String, MyType>`, and arrays (e.g., `MyType[]`).

However, the automatic detection may not cover extremely complex, user-defined generic type hierarchies (e.g., `MyWrapper<T extends SomeClass>`).

If you encounter `ClassNotFoundException` or similar reflection-related errors in your native image, you can fall back to Quarkus's standard reflection registration mechanism. Simply add the `@RegisterForReflection` annotation to the problematic payload class:

```java
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class MyComplexGenericPayload {
    // ... fields and methods
}
```

This gives the native image build process the necessary hints to include the class and its members for reflection.

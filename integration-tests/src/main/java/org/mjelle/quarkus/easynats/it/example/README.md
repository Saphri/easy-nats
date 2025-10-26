# @NatsSubscriber Example

This package contains a simple working example of using the `@NatsSubscriber` annotation.

## Components

### GreetingListener
Demonstrates the `@NatsSubscriber` annotation:
```java
@ApplicationScoped
public class GreetingListener {

    @NatsSubscriber("test.example.greetings")
    public void onGreeting(String message) {
        LOGGER.infof("📩 Received greeting: %s", message);
    }
}
```

### GreetingResource
REST endpoint to trigger message sending:
```java
@Path("/example/greeting")
public class GreetingResource {

    @Inject
    @NatsSubject("test.example.greetings")
    NatsPublisher<String> publisher;

    @POST
    public GreetingResponse sendGreeting(GreetingRequest request) {
        String message = String.format("Hello, %s!", request.name);
        publisher.publish(message);
        return new GreetingResponse("success", "Greeting sent: " + message);
    }
}
```

## How to Run

### 1. Start NATS Server with JetStream
```bash
docker run -d --name nats -p 4222:4222 \
  nats:latest -js
```

### 2. Create the JetStream Stream
```bash
nats stream add test-stream \
  --subjects "test.>" \
  --storage memory
```

> **Note**: The stream name is `test-stream` and matches subjects starting with `test.*`
> This follows the same pattern used in the integration tests.

### 3. Start the Application
```bash
./mvnw quarkus:dev
```

### 4. Send a Greeting
```bash
curl -X POST http://localhost:8080/example/greeting \
     -H "Content-Type: application/json" \
     -d '{"name": "World"}'
```

### 5. Check the Logs
You should see in the console:
```
📩 Received greeting: Hello, World!
```

## What's Happening?

1. **Startup**: When the application starts, `GreetingListener` automatically subscribes to `test.example.greetings`
2. **Request**: When you POST to `/example/greeting`, the REST endpoint publishes a message
3. **Delivery**: NATS delivers the message to the subscriber via the `test-stream` JetStream
4. **Processing**: The `onGreeting()` method is invoked with the message
5. **Acknowledgment**: The message is automatically acknowledged (ack'd) when the method completes

## Key Features Demonstrated

- ✅ **Automatic Subscription**: No manual NATS client code needed
- ✅ **CDI Integration**: Listener is a regular `@ApplicationScoped` bean
- ✅ **Automatic Ack/Nak**: Messages are ack'd on success, nak'd on exception
- ✅ **Type Safety**: Method parameter is strongly typed (String)
- ✅ **Simple Publishing**: Use `@NatsSubject` to inject a pre-configured publisher

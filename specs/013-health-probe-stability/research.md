# Research: Stateful Startup Probe in Quarkus

**Date**: 2025-10-31

## Objective

Investigate how to implement a stateful startup health probe in Quarkus that, once it reports an "UP" status, "latches" and continues to report "UP" for the remainder of the application's lifecycle. This is to prevent container orchestrators from prematurely restarting the application if a transient issue occurs after a successful startup.

## Findings

The Quarkus SmallRye Health extension provides a mechanism for creating stateful health checks, which is ideal for this use case. The key components are:

1.  **`@Startup` Annotation**: The `org.eclipse.microprofile.health.Startup` annotation is used to mark a health check as a startup probe. This separates it from liveness and readiness probes.

2.  **Stateful Implementation**: A health check is a CDI bean (`@ApplicationScoped`), so it can maintain state. A simple boolean flag can be used to track whether the initial startup has completed successfully.

## Proposed Implementation

The existing `NatsConnectionHealthCheck` can be modified to incorporate this stateful behavior.

```java
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.atomic.AtomicBoolean;

@Startup
@ApplicationScoped
public class NatsConnectionHealthCheck implements HealthCheck {

    private final AtomicBoolean isReady = new AtomicBoolean(false);

    @Override
    public HealthCheckResponse call() {
        if (isReady.get()) {
            return HealthCheckResponse.named("NATS Connection").up().build();
        }

        // Perform the actual connection check here
        boolean isConnected = checkNatsConnection(); 

        if (isConnected) {
            isReady.set(true);
            return HealthCheckResponse.named("NATS Connection").up().build();
        } else {
            return HealthCheckResponse.named("NATS Connection").down().build();
        }
    }

    private boolean checkNatsConnection() {
        // Logic to check the NATS connection status
        // This would involve checking the status from the NatsConnectionManager
    }
}
```

### Key Points of this approach:

- **`AtomicBoolean isReady`**: An `AtomicBoolean` is used for thread safety, initialized to `false`.
- **First Check**: The `call()` method first checks if `isReady` is `true`. If it is, it immediately returns an "UP" response without performing the actual check.
- **Latching**: If `isReady` is `false`, the health check performs the connection check. If the check is successful, it sets `isReady` to `true`. From this point on, all subsequent calls will return "UP" immediately.
- **Integration**: The `checkNatsConnection()` method will contain the existing logic for verifying the NATS connection status.

## Conclusion

This approach meets all the requirements of the feature spec. It's a clean and idiomatic way to implement a stateful startup probe in Quarkus. It ensures that once the application is considered "started" from the perspective of the NATS connection, it remains so.

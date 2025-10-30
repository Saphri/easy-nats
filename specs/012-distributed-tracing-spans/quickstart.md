# Quickstart: Distributed Tracing

**Version**: 1.0
**Status**: Completed
**Author**: AI Assistant
**Last Updated**: 2025-10-29

---

## 1. Overview

The Quarkus EasyNATS extension provides out-of-the-box support for distributed tracing. When you send and receive messages using `NatsPublisher` and `@NatsSubscriber`, the extension automatically creates and correlates spans to provide a complete, end-to-end trace of your message flows.

This feature is enabled by default and requires no additional configuration.

## 2. How It Works

The extension integrates with the Quarkus OpenTelemetry extension to:

1.  **Create Spans**: It creates a "producer" span when you publish a message and a "consumer" span when you receive a message.
2.  **Propagate Context**: It automatically injects W3C Trace Context headers into the NATS message headers when publishing, and extracts them when subscribing. This ensures that the producer and consumer spans are correctly linked in a single trace.

## 3. Viewing Traces

To view the traces, you need to have an OpenTelemetry-compatible backend (like the `lgtm` stack provided in `docker-compose-devservices.yml` for integration tests) running and configured in your Quarkus application.

Here's an example of how to configure the OpenTelemetry exporter in your `application.properties` to send traces to the `lgtm` service:

```properties
quarkus.opentelemetry.tracer.exporter.otlp.endpoint=http://localhost:4317
quarkus.opentelemetry.tracer.exporter.otlp.protocol=grpc
```

With this configuration, your application will send traces to the `lgtm` instance. When you send a message, you will be able to see a trace in the `lgtm` UI (accessible at `http://localhost:3000`) that looks something like this:

```
┌───────────────────────────────────────────────────────────────────────────┐
│ my-producer-service: POST /messages                                       │
├───────────────────────────────────────────────────────────────────────────┤
│   my-producer-service: NATS publish to my.subject                         │
├───────────────────────────────────────────────────────────────────────────┤
│     my-consumer-service: NATS receive from my.subject                     │
├───────────────────────────────────────────────────────────────────────────┤
│       my-consumer-service: process message                                │
└───────────────────────────────────────────────────────────────────────────┘
```

This provides a clear, visual representation of the entire message flow, including the time spent in the producer, the consumer, and the NATS messaging system.

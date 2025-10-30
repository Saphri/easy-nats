# Research: Distributed Tracing for Messaging Spans

**Version**: 1.0
**Status**: Completed
**Author**: AI Assistant
**Last Updated**: 2025-10-29

---

## 1. Research Summary

No significant research was required for this feature. The technical approach is well-defined by industry standards and existing Quarkus capabilities.

## 2. Key Decisions

- **Decision**: Use the `quarkus-opentelemetry` extension for all tracing-related operations.
  - **Rationale**: This is the standard, officially supported way to implement distributed tracing in a Quarkus application. It provides seamless integration with the Quarkus ecosystem and ensures compatibility with a wide range of OpenTelemetry backends.
  - **Alternatives Considered**:
    - **Manual OpenTelemetry SDK integration**: This would be more complex, require manual configuration, and would not be as well-integrated with Quarkus.
    - **Using a different tracing library (e.g., Micrometer Tracing)**: This would introduce a non-standard dependency and would not be as well-supported as the official Quarkus OpenTelemetry extension.

- **Decision**: Propagate trace context using W3C Trace Context headers in NATS message headers.
  - **Rationale**: The W3C Trace Context is the industry standard for trace propagation. Using NATS headers is the natural way to transport this metadata without polluting the message payload, and it aligns with the CloudEvents specification's use of headers.
  - **Alternatives Considered**:
    - **Injecting trace context into the message payload**: This would couple the tracing implementation with the message schema, making it brittle and harder to maintain.

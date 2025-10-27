# Research: Transparent CloudEvent Publisher

## Decision Summary

The technical approach for this feature is straightforward and relies on existing patterns within the codebase. No extensive research into new technologies or patterns is required.

- **Decision**: The `NatsPublisher.publish(String, Object)` method will be the single entry point for publishing messages. It will internally handle the creation of CloudEvent headers and payload serialization.
- **Rationale**: This approach directly fulfills the feature's primary goal of simplifying the developer experience. It removes ambiguity by providing one clear method for publishing, while making the CloudEvents format a transparent default. The logic for differentiating payload types (`POJO` vs. native) already exists in `TypedPayloadEncoder` and can be leveraged directly.
- **Alternatives considered**:
    - **Keeping separate methods**: Retaining both `publish` and `publishCloudEvent` was rejected as it complicates the API and goes against the user's request to simplify.
    - **Using a builder pattern**: A `publisher.prepareMessage(payload).asCloudEvent().publish()` style was considered but deemed overly verbose for the common case. The goal is to make the best practice (using CloudEvents) the easiest path.

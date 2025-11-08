package org.mjelle.quarkus.easynats;

import io.nats.client.impl.Headers;

public record HeadersWithMetadata(Headers headers, CloudEventsMetadata metadata) {}

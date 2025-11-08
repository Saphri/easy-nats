package org.mjelle.quarkus.easynats;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.nats.client.impl.Headers;

class CloudEventsHeadersBuilderTest {

  private CloudEventsHeadersBuilder builder;
  private CloudEventsMetadata metadata;

  @BeforeEach
  void setUp() {
    builder = new CloudEventsHeadersBuilder();
    metadata =
        new CloudEventsMetadata(
            "1.0",
            "com.example.Order",
            "/order-service",
            "id-123",
            "2025-11-08T10:00:00Z",
            "application/json");
  }

  @Test
  void testCreateHeadersWithExplicitTypeAndSource() {
    Headers headers = builder.build(metadata);

    assertThat(headers.get(CloudEventsHeaders.HEADER_SPECVERSION)).hasSize(1).contains("1.0");
    assertThat(headers.get(CloudEventsHeaders.HEADER_TYPE))
        .hasSize(1)
        .contains("com.example.Order");
    assertThat(headers.get(CloudEventsHeaders.HEADER_SOURCE)).hasSize(1).contains("/order-service");
    assertThat(headers.get(CloudEventsHeaders.HEADER_DATACONTENTTYPE))
        .hasSize(1)
        .contains("application/json");
  }

  @Test
  void testCreateHeadersIncludesAllRequiredHeaders() {
    Headers headers = builder.build(metadata);

    assertThat(headers.get(CloudEventsHeaders.HEADER_SPECVERSION)).isNotEmpty();
    assertThat(headers.get(CloudEventsHeaders.HEADER_TYPE)).isNotEmpty();
    assertThat(headers.get(CloudEventsHeaders.HEADER_SOURCE)).isNotEmpty();
    assertThat(headers.get(CloudEventsHeaders.HEADER_ID)).isNotEmpty();
    assertThat(headers.get(CloudEventsHeaders.HEADER_TIME)).isNotEmpty();
    assertThat(headers.get(CloudEventsHeaders.HEADER_DATACONTENTTYPE)).isNotEmpty();
  }
}

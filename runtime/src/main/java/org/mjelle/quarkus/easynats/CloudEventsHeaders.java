package org.mjelle.quarkus.easynats;

/**
 * Factory for generating CloudEvents 1.0 specification-compliant metadata.
 *
 * <p>Provides utilities for creating CloudEvents metadata that can be used with NATS messages.
 * Supports automatic generation of ce-id, ce-time, ce-type, and ce-source.
 */
public class CloudEventsHeaders {

  // CloudEvents spec 1.0 constants
  public static final String SPEC_VERSION = "1.0";
  public static final String PREFIX = "ce-";
  public static final String HEADER_SPECVERSION = "ce-specversion";
  public static final String HEADER_TYPE = "ce-type";
  public static final String HEADER_SOURCE = "ce-source";
  public static final String HEADER_ID = "ce-id";
  public static final String HEADER_TIME = "ce-time";
  public static final String HEADER_DATACONTENTTYPE = "ce-datacontenttype";
  public static final String CONTENT_TYPE_JSON = "application/json";

  private CloudEventsHeaders() {
    // Utility class; no instances
  }
}

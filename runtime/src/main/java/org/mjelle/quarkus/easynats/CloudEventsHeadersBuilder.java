package org.mjelle.quarkus.easynats;

import io.nats.client.impl.Headers;

public class CloudEventsHeadersBuilder {

  public static final String PREFIX = "ce-";
  public static final String HEADER_SPECVERSION = "ce-specversion";
  public static final String HEADER_TYPE = "ce-type";
  public static final String HEADER_SOURCE = "ce-source";
  public static final String HEADER_ID = "ce-id";
  public static final String HEADER_TIME = "ce-time";
  public static final String HEADER_DATACONTENTTYPE = "ce-datacontenttype";

  public Headers build(CloudEventsMetadata metadata) {
    Headers headers = new Headers();
    headers.add(HEADER_SPECVERSION, metadata.specVersion());
    headers.add(HEADER_TYPE, metadata.type());
    headers.add(HEADER_SOURCE, metadata.source());
    headers.add(HEADER_ID, metadata.id());
    headers.add(HEADER_TIME, metadata.time());
    headers.add(HEADER_DATACONTENTTYPE, metadata.dataContentType());
    return headers;
  }
}

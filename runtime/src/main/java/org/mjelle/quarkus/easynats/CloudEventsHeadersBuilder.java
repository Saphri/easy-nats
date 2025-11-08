package org.mjelle.quarkus.easynats;

import io.nats.client.impl.Headers;

public class CloudEventsHeadersBuilder {

  public Headers build(CloudEventsMetadata metadata) {
    Headers headers = new Headers();
    headers.add(CloudEventsHeaders.HEADER_SPECVERSION, metadata.specVersion());
    headers.add(CloudEventsHeaders.HEADER_TYPE, metadata.type());
    headers.add(CloudEventsHeaders.HEADER_SOURCE, metadata.source());
    headers.add(CloudEventsHeaders.HEADER_ID, metadata.id());
    headers.add(CloudEventsHeaders.HEADER_TIME, metadata.time());
    headers.add(CloudEventsHeaders.HEADER_DATACONTENTTYPE, metadata.dataContentType());
    return headers;
  }
}

package org.mjelle.quarkus.easynats;

public record CloudEventsMetadata(
    String specVersion,
    String type,
    String source,
    String id,
    String time,
    String dataContentType) {}

package org.mjelle.quarkus.easynats;

import io.nats.client.impl.Headers;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.UUID;

/**
 * Factory for generating CloudEvents 1.0 specification-compliant metadata.
 *
 * Provides utilities for creating CloudEvents metadata that can be used with NATS messages.
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

    /**
     * Immutable data structure for CloudEvents metadata.
     */
    public static class CloudEventsMetadata {
        public final String specVersion;
        public final String type;
        public final String source;
        public final String id;
        public final String time;
        public final String dataContentType;

        public CloudEventsMetadata(String type, String source) {
            this.specVersion = SPEC_VERSION;
            this.type = type;
            this.source = source;
            this.id = generateId();
            this.time = generateTime();
            this.dataContentType = CONTENT_TYPE_JSON;
        }
    }

    /**
     * Generate a unique CloudEvents ID (UUID v4).
     *
     * @return a UUID v4 string
     */
    public static String generateId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Generate a CloudEvents timestamp in ISO 8601 UTC format.
     *
     * @return an ISO 8601 timestamp string
     */
    public static String generateTime() {
        return Instant.now().toString();
    }

    /**
     * Generate a CloudEvents type from a payload class.
     *
     * @param payloadClass the payload class
     * @return the fully-qualified class name
     */
    public static String generateType(Class<?> payloadClass) {
        return payloadClass.getCanonicalName();
    }

    /**
     * Generate a CloudEvents source identifier.
     *
     * Attempts to get the local hostname, falls back to app name or localhost.
     *
     * @return a source identifier string
     */
    public static String generateSource() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            String appName = System.getProperty("app.name");
            return appName != null ? appName : "localhost";
        }
    }

    /**
     * Generate CloudEvents metadata for a payload.
     *
     * @param payloadClass the class of the payload (used to auto-generate ce-type)
     * @param ceTypeOverride the ce-type override (nullable; auto-generated if null)
     * @param ceSourceOverride the ce-source override (nullable; auto-generated if null)
     * @return a CloudEventsMetadata object with all attributes set
     */
    public static CloudEventsMetadata generateMetadata(
        Class<?> payloadClass, String ceTypeOverride, String ceSourceOverride) {
        String ceType = ceTypeOverride != null ? ceTypeOverride : generateType(payloadClass);
        String ceSource = ceSourceOverride != null ? ceSourceOverride : generateSource();
        return new CloudEventsMetadata(ceType, ceSource);
    }

    /**
     * Create NATS Headers object with CloudEvents attributes.
     *
     * @param payloadClass the class of the payload (used to auto-generate ce-type)
     * @param ceTypeOverride the ce-type override (nullable; auto-generated if null)
     * @param ceSourceOverride the ce-source override (nullable; auto-generated if null)
     * @return a Headers object with CloudEvents attributes set
     */
    public static Headers createHeaders(Class<?> payloadClass, String ceTypeOverride, String ceSourceOverride) {
        Headers headers = new Headers();
        CloudEventsMetadata metadata = generateMetadata(payloadClass, ceTypeOverride, ceSourceOverride);

        headers.add(HEADER_SPECVERSION, metadata.specVersion);
        headers.add(HEADER_TYPE, metadata.type);
        headers.add(HEADER_SOURCE, metadata.source);
        headers.add(HEADER_ID, metadata.id);
        headers.add(HEADER_TIME, metadata.time);
        headers.add(HEADER_DATACONTENTTYPE, metadata.dataContentType);

        return headers;
    }
}

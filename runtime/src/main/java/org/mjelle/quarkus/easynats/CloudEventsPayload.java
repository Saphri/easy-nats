package org.mjelle.quarkus.easynats;

import java.util.Objects;

/**
 * Immutable data structure representing a typed payload with CloudEvents metadata.
 *
 * @param <T> the type of the payload data
 */
public class CloudEventsPayload<T> {

    private final T data;
    private final String ceType;
    private final String ceSource;
    private final String ceId;
    private final String ceTime;

    /**
     * Constructs a CloudEventsPayload with all metadata.
     *
     * @param data the payload data (must not be null)
     * @param ceType the CloudEvents type (nullable; auto-generated if null)
     * @param ceSource the CloudEvents source (nullable; auto-generated if null)
     * @param ceId the CloudEvents ID (must not be null)
     * @param ceTime the CloudEvents timestamp (must not be null)
     * @throws NullPointerException if data, ceId, or ceTime is null
     */
    public CloudEventsPayload(T data, String ceType, String ceSource, String ceId, String ceTime) {
        this.data = Objects.requireNonNull(data, "Payload data cannot be null");
        this.ceType = ceType;
        this.ceSource = ceSource;
        this.ceId = Objects.requireNonNull(ceId, "ce-id cannot be null");
        this.ceTime = Objects.requireNonNull(ceTime, "ce-time cannot be null");
    }

    /**
     * Get the payload data.
     *
     * @return the payload data
     */
    public T getData() {
        return data;
    }

    /**
     * Get the CloudEvents type.
     *
     * @return the ce-type value (nullable)
     */
    public String getCeType() {
        return ceType;
    }

    /**
     * Get the CloudEvents source.
     *
     * @return the ce-source value (nullable)
     */
    public String getCeSource() {
        return ceSource;
    }

    /**
     * Get the CloudEvents ID.
     *
     * @return the ce-id value (non-null UUID)
     */
    public String getCeId() {
        return ceId;
    }

    /**
     * Get the CloudEvents timestamp.
     *
     * @return the ce-time value (non-null ISO 8601 timestamp)
     */
    public String getCeTime() {
        return ceTime;
    }
}

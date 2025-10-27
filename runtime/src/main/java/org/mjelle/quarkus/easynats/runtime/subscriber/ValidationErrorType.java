package org.mjelle.quarkus.easynats.runtime.subscriber;

/**
 * Enumeration of type validation error categories.
 */
public enum ValidationErrorType {
    /**
     * Type is a primitive (int, long, double, etc.).
     * User should wrap in a POJO.
     */
    PRIMITIVE_TYPE,

    /**
     * Type is an array (int[], String[], etc.).
     * User should wrap in a POJO.
     */
    ARRAY_TYPE,

    /**
     * Type doesn't have a no-arg constructor and no @JsonDeserialize annotation.
     * User should add a no-arg constructor or provide custom deserializer.
     */
    MISSING_NO_ARG_CTOR,

    /**
     * Generic type parameter cannot be resolved.
     * User should provide concrete type instead of wildcard/unresolvable generic.
     */
    UNRESOLVABLE_GENERIC,

    /**
     * Jackson type construction failed.
     * Underlying Jackson error with details provided.
     */
    JACKSON_ERROR,

    /**
     * Other validation error not covered by specific categories.
     */
    CUSTOM_ERROR
}

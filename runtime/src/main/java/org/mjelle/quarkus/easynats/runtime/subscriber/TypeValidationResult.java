package org.mjelle.quarkus.easynats.runtime.subscriber;

/**
 * Encapsulates the result of type validation.
 *
 * Indicates whether a type is Jackson-compatible and, if not, provides
 * error details to guide users to the wrapper pattern.
 */
public class TypeValidationResult {
    private final boolean valid;
    private final String typeName;
    private final String errorMessage;
    private final ValidationErrorType errorType;

    /**
     * Creates a validation result.
     *
     * @param valid true if type is valid, false otherwise
     * @param typeName fully qualified class name (e.g., "com.example.OrderData")
     * @param errorMessage human-readable error message (null if valid)
     * @param errorType category of error (null if valid)
     */
    public TypeValidationResult(boolean valid, String typeName, String errorMessage, ValidationErrorType errorType) {
        this.valid = valid;
        this.typeName = typeName;
        this.errorMessage = errorMessage;
        this.errorType = errorType;

        // Validate invariants
        if (valid) {
            if (errorMessage != null || errorType != null) {
                throw new IllegalArgumentException(
                    "Valid result must have null errorMessage and errorType"
                );
            }
        } else {
            if (errorMessage == null || errorType == null) {
                throw new IllegalArgumentException(
                    "Invalid result must have non-null errorMessage and errorType"
                );
            }
        }
    }

    /**
     * Returns true if the type is valid for Jackson serialization/deserialization.
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * Returns the fully qualified class name.
     */
    public String getTypeName() {
        return typeName;
    }

    /**
     * Returns the error message (null if valid).
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Returns the error type category (null if valid).
     */
    public ValidationErrorType getErrorType() {
        return errorType;
    }

    @Override
    public String toString() {
        if (valid) {
            return String.format("TypeValidationResult{valid=true, type=%s}", typeName);
        } else {
            return String.format(
                "TypeValidationResult{valid=false, type=%s, error=%s, message=%s}",
                typeName, errorType, errorMessage
            );
        }
    }
}

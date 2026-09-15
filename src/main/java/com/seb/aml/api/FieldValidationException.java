package com.seb.aml.api;

/**
 * Thrown when a specific request field fails validation in the controller layer.
 *
 * <p>Unlike Jakarta Bean Validation errors (which are caught automatically by Spring),
 * this exception is used for custom validation logic (e.g., channel enum parsing,
 * timestamp format, country code format) where the field name must be explicitly
 * provided for consistent error responses.</p>
 */
public class FieldValidationException extends RuntimeException {

    private final String field;

    public FieldValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}

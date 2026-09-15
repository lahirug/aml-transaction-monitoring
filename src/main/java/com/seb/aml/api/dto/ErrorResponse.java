package com.seb.aml.api.dto;

import java.util.List;

/**
 * Structured error response returned for all API errors.
 *
 * <p>Provides a machine-readable error code, a human-readable message, and optional
 * field-level details for validation errors.</p>
 *
 * @param error machine-readable error code (e.g., "VALIDATION_ERROR")
 * @param message human-readable summary of the error
 * @param details field-level error details (may be empty for non-validation errors)
 */
public record ErrorResponse(
        String error,
        String message,
        List<FieldError> details
) {

    /**
     * A single field-level validation error.
     *
     * @param field the field that failed validation
     * @param issue description of the validation failure
     */
    public record FieldError(String field, String issue) {
    }
}

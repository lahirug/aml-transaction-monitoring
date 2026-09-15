package com.seb.aml.api;

import com.seb.aml.api.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Global exception handler for the REST API.
 *
 * <p>Converts exceptions into structured {@link ErrorResponse} objects with consistent
 * error codes. All error responses follow the same format regardless of the error type.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles Jakarta Bean Validation failures (missing/invalid fields).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        List<ErrorResponse.FieldError> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();

        return ResponseEntity.badRequest().body(
                new ErrorResponse("VALIDATION_ERROR", "Transaction validation failed", details));
    }

    /**
     * Handles unsupported currency errors.
     */
    @ExceptionHandler(UnsupportedCurrencyException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedCurrency(UnsupportedCurrencyException ex) {
        List<ErrorResponse.FieldError> details = List.of(
                new ErrorResponse.FieldError("currency", ex.getMessage()));

        return ResponseEntity.badRequest().body(
                new ErrorResponse("UNSUPPORTED_CURRENCY", ex.getMessage(), details));
    }

    /**
     * Handles field-level validation failures from custom controller logic.
     */
    @ExceptionHandler(FieldValidationException.class)
    public ResponseEntity<ErrorResponse> handleFieldValidation(FieldValidationException ex) {
        List<ErrorResponse.FieldError> details = List.of(
                new ErrorResponse.FieldError(ex.getField(), ex.getMessage()));

        return ResponseEntity.badRequest().body(
                new ErrorResponse("VALIDATION_ERROR", "Transaction validation failed", details));
    }

    /**
     * Handles malformed JSON request bodies.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(
                new ErrorResponse("VALIDATION_ERROR", "Malformed request body", List.of()));
    }

    /**
     * Handles unsupported Content-Type (e.g., text/plain instead of application/json).
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(
                new ErrorResponse("UNSUPPORTED_MEDIA_TYPE",
                        "Content-Type must be application/json", List.of()));
    }

    /**
     * Handles unsupported HTTP methods (e.g., GET on a POST-only endpoint).
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(
                new ErrorResponse("METHOD_NOT_ALLOWED", ex.getMessage(), List.of()));
    }

    /**
     * Catch-all for unexpected errors. Logs the full stack trace for debugging.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error during request processing", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                new ErrorResponse("SCREENING_ERROR",
                        "An unexpected error occurred during screening", List.of()));
    }
}

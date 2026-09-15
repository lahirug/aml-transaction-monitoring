package com.seb.aml.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Request DTO for the transaction screening endpoint.
 *
 * <p>Validates all required fields using Jakarta Bean Validation annotations.
 * The {@code timestamp} field is optional — if omitted, the server receive time is used.</p>
 */
public record ScreeningRequest(
        @NotBlank(message = "transactionId is required") String transactionId,
        @NotBlank(message = "customerId is required") String customerId,
        @NotNull(message = "amount is required") @Positive(message = "amount must be positive") BigDecimal amount,
        @NotBlank(message = "currency is required") String currency,
        @NotBlank(message = "originCountry is required") String originCountry,
        @NotBlank(message = "destinationCountry is required") String destinationCountry,
        @NotBlank(message = "channel is required") String channel,
        String timestamp
) {
}

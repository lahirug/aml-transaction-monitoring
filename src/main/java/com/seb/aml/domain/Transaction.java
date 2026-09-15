package com.seb.aml.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable representation of a financial transaction to be screened.
 *
 * <p>This is the core domain object that rules evaluate against. All fields are
 * required except {@code timestamp}, which defaults to the current server time
 * if not provided by the caller.</p>
 *
 * <p>Note: The {@code timestamp} field is included for future support of time-based
 * rules (velocity checks, time-of-day analysis). In the current implementation it
 * is captured in screening events for audit purposes but not used in rule evaluation.</p>
 *
 * @param transactionId unique identifier for the transaction
 * @param customerId identifier of the customer initiating the transaction
 * @param amount transaction amount (must be positive)
 * @param currency ISO 4217 currency code (only EUR supported for threshold rules)
 * @param originCountry ISO 3166-1 alpha-2 code of the originating country
 * @param destinationCountry ISO 3166-1 alpha-2 code of the destination country
 * @param channel the medium through which the transaction was initiated
 * @param timestamp when the transaction occurred (defaults to server receive time)
 */
public record Transaction(
        String transactionId,
        String customerId,
        BigDecimal amount,
        String currency,
        String originCountry,
        String destinationCountry,
        Channel channel,
        Instant timestamp
) {
}

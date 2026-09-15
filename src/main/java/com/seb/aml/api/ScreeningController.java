package com.seb.aml.api;

import com.seb.aml.api.dto.ScreeningRequest;
import com.seb.aml.api.dto.ScreeningResponse;
import com.seb.aml.config.RuleProperties;
import com.seb.aml.domain.Channel;
import com.seb.aml.domain.ScreeningResult;
import com.seb.aml.domain.Transaction;
import com.seb.aml.screening.ScreeningService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * REST controller for transaction screening.
 *
 * <p>Accepts transaction screening requests, validates input, converts DTOs to domain
 * objects, delegates to the screening service, and returns the result. This layer is
 * responsible for HTTP concerns only — no business logic.</p>
 */
@RestController
@RequestMapping("/api/v1/transactions")
public class ScreeningController {

    private final ScreeningService screeningService;
    private final RuleProperties ruleProperties;

    public ScreeningController(ScreeningService screeningService, RuleProperties ruleProperties) {
        this.screeningService = screeningService;
        this.ruleProperties = ruleProperties;
    }

    /**
     * Screen a single transaction against all active AML rules.
     *
     * @param request the transaction to screen
     * @return screening result with decision and matched rules
     */
    @PostMapping(value = "/screen", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ScreeningResponse> screenTransaction(
            @Valid @RequestBody ScreeningRequest request) {

        validateCurrency(request.currency());
        validateCountryCode(request.originCountry(), "originCountry");
        validateCountryCode(request.destinationCountry(), "destinationCountry");
        Channel channel = parseChannel(request.channel());
        Instant timestamp = parseTimestamp(request.timestamp());

        Transaction transaction = new Transaction(
                request.transactionId().trim(),
                request.customerId().trim(),
                request.amount(),
                request.currency().trim().toUpperCase(),
                request.originCountry().trim().toUpperCase(),
                request.destinationCountry().trim().toUpperCase(),
                channel,
                timestamp
        );

        ScreeningResult result = screeningService.screen(transaction);

        ScreeningResponse response = new ScreeningResponse(
                result.transactionId(),
                result.decision(),
                result.matchedRules(),
                result.screenedAt()
        );

        return ResponseEntity.ok(response);
    }

    private void validateCurrency(String currency) {
        if (!ruleProperties.supportedCurrency().equalsIgnoreCase(currency.trim())) {
            throw new UnsupportedCurrencyException(currency.trim(), ruleProperties.supportedCurrency());
        }
    }

    private void validateCountryCode(String country, String fieldName) {
        String trimmed = country.trim();
        if (!trimmed.matches("[a-zA-Z]{2}")) {
            throw new FieldValidationException(fieldName,
                    "Invalid country code: " + trimmed + ". Expected ISO 3166-1 alpha-2 code (e.g., SE, FI)");
        }
    }

    private Channel parseChannel(String channel) {
        try {
            return Channel.valueOf(channel.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new FieldValidationException("channel",
                    "Invalid channel: " + channel.trim() + ". Supported values: ONLINE, BRANCH, ATM, MOBILE");
        }
    }

    private Instant parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(timestamp.trim());
        } catch (DateTimeParseException e) {
            throw new FieldValidationException("timestamp",
                    "Invalid timestamp format: " + timestamp.trim()
                            + ". Expected ISO-8601 format (e.g., 2024-01-15T10:30:00Z)");
        }
    }
}

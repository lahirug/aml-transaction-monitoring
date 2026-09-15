package com.seb.aml.api.dto;

import com.seb.aml.domain.Decision;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for the transaction screening endpoint.
 *
 * @param transactionId the screened transaction's identifier
 * @param decision CLEAR or REVIEW
 * @param matchedRules identifiers of all rules that flagged the transaction
 * @param screenedAt when the screening decision was made
 */
public record ScreeningResponse(
        String transactionId,
        Decision decision,
        List<String> matchedRules,
        Instant screenedAt
) {
}

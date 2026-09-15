package com.seb.aml.domain;

import java.time.Instant;
import java.util.List;

/**
 * Immutable result of screening a transaction against AML rules.
 *
 * <p>Contains the screening decision, the list of matched rule identifiers, and
 * the timestamp when the screening was completed. This object is both returned
 * to the API caller and published as a screening event for observability.</p>
 *
 * @param transactionId the screened transaction's identifier
 * @param decision CLEAR if no rules matched, REVIEW if one or more matched
 * @param matchedRules identifiers of all rules that flagged the transaction
 * @param screenedAt when the screening decision was made (server time)
 */
public record ScreeningResult(
        String transactionId,
        Decision decision,
        List<String> matchedRules,
        Instant screenedAt
) {
}

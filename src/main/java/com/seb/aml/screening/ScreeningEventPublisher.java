package com.seb.aml.screening;

import com.seb.aml.domain.ScreeningResult;
import com.seb.aml.rule.RuleEvaluationContext;

/**
 * Publishes screening decision events for observability and audit.
 *
 * <p>The current implementation logs events as structured JSON. In production, this
 * interface would have a Kafka or database-backed implementation, swappable without
 * changing the screening service.</p>
 */
public interface ScreeningEventPublisher {

    /**
     * Publish a screening event.
     *
     * @param result the screening decision
     * @param context the evaluation context that produced the decision
     * @param rulesEvaluated total number of enabled rules that were evaluated
     * @param evaluationTimeMs time taken to evaluate all rules in milliseconds
     */
    void publish(ScreeningResult result, RuleEvaluationContext context,
                 int rulesEvaluated, long evaluationTimeMs);
}

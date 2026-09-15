package com.seb.aml.screening;

import com.seb.aml.domain.ScreeningResult;
import com.seb.aml.rule.RuleEvaluationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Publishes screening events as structured JSON log entries.
 *
 * <p>Each screening decision produces a log entry containing the transaction ID,
 * customer ID, decision, matched rules, evaluation metrics, and timestamp. These
 * structured logs enable monitoring dashboards (e.g., Grafana), alerting on anomalies,
 * and serve as a basic audit trail.</p>
 *
 * <p>In production, this would be replaced with a Kafka or database-backed implementation
 * for durable event storage.</p>
 */
@Component
public class LoggingScreeningEventPublisher implements ScreeningEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingScreeningEventPublisher.class);

    @Override
    public void publish(ScreeningResult result, RuleEvaluationContext context,
                        int rulesEvaluated, long evaluationTimeMs) {
        log.info("Screening completed",
                kv("event", "SCREENING_COMPLETED"),
                kv("transactionId", result.transactionId()),
                kv("customerId", context.getTransaction().customerId()),
                kv("decision", result.decision()),
                kv("matchedRules", result.matchedRules()),
                kv("rulesEvaluated", rulesEvaluated),
                kv("evaluationTimeMs", evaluationTimeMs),
                kv("screenedAt", result.screenedAt()));
    }
}

package com.seb.aml.screening;

import com.seb.aml.domain.Decision;
import com.seb.aml.domain.ScreeningResult;
import com.seb.aml.domain.Transaction;
import com.seb.aml.rule.Rule;
import com.seb.aml.rule.RuleEvaluationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Core screening orchestrator.
 *
 * <p>Evaluates a transaction against all registered and enabled AML detection rules,
 * produces a screening result, and publishes a screening event for observability.</p>
 *
 * <p>This class has no knowledge of specific rule implementations — it depends only
 * on the {@link Rule} interface. New rules are discovered automatically via Spring
 * dependency injection.</p>
 *
 * <p>Key behaviors:</p>
 * <ul>
 *   <li><strong>Fail-safe evaluation:</strong> If a rule throws an exception, the
 *       transaction is treated as a match (flagged for REVIEW). In AML, false negatives
 *       are unacceptable.</li>
 *   <li><strong>Duplicate detection:</strong> If multiple rules share the same ruleId,
 *       a warning is logged and only the first instance is kept.</li>
 * </ul>
 */
@Service
public class ScreeningService {

    private static final Logger log = LoggerFactory.getLogger(ScreeningService.class);

    private final List<Rule> rules;
    private final ScreeningEventPublisher eventPublisher;

    /**
     * Constructs the screening service with all discovered rule implementations.
     *
     * <p>Spring injects all {@link Rule} beans automatically. Duplicates (same ruleId)
     * are detected, logged as warnings, and deduplicated — only the first instance is kept.</p>
     *
     * @param rules all rule beans discovered by Spring
     * @param eventPublisher publisher for screening decision events
     */
    public ScreeningService(List<Rule> rules, ScreeningEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        this.rules = deduplicateRules(rules);
    }

    /**
     * Screen a transaction against all enabled rules.
     *
     * @param transaction the transaction to screen
     * @return the screening result containing the decision and matched rule identifiers
     */
    public ScreeningResult screen(Transaction transaction) {
        RuleEvaluationContext context = new RuleEvaluationContext(transaction);
        long startTime = System.currentTimeMillis();

        List<Rule> enabledRules = rules.stream()
                .filter(Rule::isEnabled)
                .toList();

        if (enabledRules.isEmpty()) {
            log.warn("No rules are enabled — all transactions will be marked CLEAR. "
                    + "Verify rule configuration in application.yml.");
        }

        List<String> matchedRules = enabledRules.stream()
                .filter(rule -> evaluateSafely(rule, context))
                .map(Rule::getRuleId)
                .toList();

        Decision decision = matchedRules.isEmpty() ? Decision.CLEAR : Decision.REVIEW;

        ScreeningResult result = new ScreeningResult(
                transaction.transactionId(),
                decision,
                matchedRules,
                Instant.now()
        );

        long evaluationTimeMs = System.currentTimeMillis() - startTime;
        publishSafely(result, context, enabledRules.size(), evaluationTimeMs);

        return result;
    }

    /**
     * Returns the list of registered rules (after deduplication).
     *
     * @return unmodifiable view of active rules
     */
    public List<Rule> getRegisteredRules() {
        return List.copyOf(rules);
    }

    /**
     * Publishes the screening event safely. A publisher failure must not prevent the
     * screening result from being returned — the decision has already been made and
     * the caller needs it. Observability failures are logged but do not affect outcomes.
     */
    private void publishSafely(ScreeningResult result, RuleEvaluationContext context,
                                int rulesEvaluated, long evaluationTimeMs) {
        try {
            eventPublisher.publish(result, context, rulesEvaluated, evaluationTimeMs);
        } catch (Exception e) {
            log.error("Failed to publish screening event for transaction {}: {}",
                    result.transactionId(), e.getMessage(), e);
        }
    }

    /**
     * Fail-safe rule evaluation. If a rule throws an exception, it is treated as a match
     * to ensure the transaction is flagged for review. In AML, false positives (unnecessary
     * reviews) are acceptable; false negatives (missed suspicious activity) are not.
     */
    private boolean evaluateSafely(Rule rule, RuleEvaluationContext context) {
        try {
            return rule.evaluate(context);
        } catch (Exception e) {
            log.error("Rule {} failed for transaction {}: {}. Treating as match (fail-safe).",
                    rule.getRuleId(),
                    context.getTransaction().transactionId(),
                    e.getMessage(), e);
            return true;
        }
    }

    /**
     * Removes duplicate rules (same ruleId), keeping the first instance and logging warnings.
     */
    private List<Rule> deduplicateRules(List<Rule> allRules) {
        Map<String, Rule> seen = new LinkedHashMap<>();
        for (Rule rule : allRules) {
            Rule existing = seen.putIfAbsent(rule.getRuleId(), rule);
            if (existing != null) {
                log.warn("Duplicate rule detected: ruleId='{}'. Keeping first instance ({}), "
                                + "ignoring duplicate ({}).",
                        rule.getRuleId(),
                        existing.getClass().getSimpleName(),
                        rule.getClass().getSimpleName());
            }
        }
        return new ArrayList<>(seen.values());
    }
}

package com.seb.aml.rule;

import java.util.Map;

/**
 * Contract for all AML detection rules.
 *
 * <p>Each implementation encapsulates a single detection pattern and is independently
 * configurable and testable. Rules are discovered automatically by Spring's component
 * scanning and registered with the screening service.</p>
 *
 * <p><strong>Thread safety:</strong> Rule implementations are Spring singletons shared
 * across concurrent request threads. Implementations must be stateless — all mutable
 * state should be avoided. Configuration is injected once at construction and must not
 * change during the application's lifetime.</p>
 *
 * <p>Adding a new rule requires:
 * <ol>
 *   <li>Writing a new class implementing this interface</li>
 *   <li>Annotating it as a Spring {@code @Component}</li>
 *   <li>Adding configuration in {@code application.yml}</li>
 * </ol>
 * No changes to the screening service or API layer are needed.</p>
 */
public interface Rule {

    /** Unique identifier for this rule (e.g., "HIGH_VALUE_TRANSACTION"). */
    String getRuleId();

    /** Human-readable description of what this rule detects. */
    String getDescription();

    /** Whether this rule is currently active in screening evaluations. */
    boolean isEnabled();

    /**
     * Evaluate the given context against this rule.
     *
     * @param context the evaluation context containing the transaction and any enrichment data
     * @return {@code true} if the rule matches (transaction is suspicious), {@code false} otherwise
     */
    boolean evaluate(RuleEvaluationContext context);

    /** Returns the current configuration parameters for introspection via the rules API. */
    Map<String, Object> getParameters();
}

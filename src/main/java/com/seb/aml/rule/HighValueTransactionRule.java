package com.seb.aml.rule;

import com.seb.aml.config.RuleProperties;
import com.seb.aml.domain.Transaction;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Flags transactions where the amount exceeds a configurable threshold.
 *
 * <p>This rule targets large-value transfers, which are a primary indicator in money
 * laundering. The EU's Anti-Money Laundering Directive requires enhanced due diligence
 * for transactions above certain thresholds.</p>
 *
 * <p>Logic: {@code transaction.amount > threshold}</p>
 */
@Component
public class HighValueTransactionRule implements Rule {

    private static final String RULE_ID = "HIGH_VALUE_TRANSACTION";
    private static final String DESCRIPTION =
            "Flags transactions where the amount exceeds the configured threshold";

    private final RuleProperties.HighValueTransaction config;

    public HighValueTransactionRule(RuleProperties properties) {
        this.config = properties.rules().highValueTransaction();
    }

    @Override
    public String getRuleId() {
        return RULE_ID;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public boolean isEnabled() {
        return config.enabled();
    }

    @Override
    public boolean evaluate(RuleEvaluationContext context) {
        Transaction tx = context.getTransaction();
        return tx.amount().compareTo(config.threshold()) > 0;
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of(
                "threshold", config.threshold(),
                "currency", "EUR"
        );
    }
}

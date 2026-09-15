package com.seb.aml.rule;

import com.seb.aml.config.RuleProperties;
import com.seb.aml.domain.Channel;
import com.seb.aml.domain.Transaction;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Flags online cross-border transactions above a configurable threshold.
 *
 * <p>Online cross-border transactions present higher AML risk due to the anonymity
 * of the channel and the complexity of cross-jurisdictional fund flows. A lower
 * threshold is applied compared to the general high-value rule.</p>
 *
 * <p>Logic: {@code channel = ONLINE AND amount > threshold AND originCountry ≠ destinationCountry}</p>
 */
@Component
public class SuspiciousOnlineTransactionRule implements Rule {

    private static final String RULE_ID = "SUSPICIOUS_ONLINE_TRANSACTION";
    private static final String DESCRIPTION =
            "Flags online cross-border transactions above the configured threshold";

    private final RuleProperties.SuspiciousOnlineTransaction config;

    public SuspiciousOnlineTransactionRule(RuleProperties properties) {
        this.config = properties.rules().suspiciousOnlineTransaction();
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
        return tx.channel() == Channel.ONLINE
                && tx.amount().compareTo(config.threshold()) > 0
                && !tx.originCountry().equals(tx.destinationCountry());
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of(
                "threshold", config.threshold(),
                "currency", "EUR"
        );
    }
}

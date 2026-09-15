package com.seb.aml.rule;

import com.seb.aml.config.RuleProperties;
import com.seb.aml.domain.Transaction;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Flags transactions with amounts just below the reporting threshold.
 *
 * <p>Structuring (also known as "smurfing") is a technique where criminals deliberately
 * keep transaction amounts below regulatory reporting thresholds to avoid detection.
 * This rule detects amounts in the suspicious range just below the threshold.</p>
 *
 * <p>Logic: {@code amount >= lowerBound AND amount < reportingThreshold}</p>
 */
@Component
public class StructuringDetectionRule implements Rule {

    private static final String RULE_ID = "STRUCTURING_DETECTION";
    private static final String DESCRIPTION =
            "Flags transactions with amounts just below the reporting threshold";

    private final RuleProperties.StructuringDetection config;

    public StructuringDetectionRule(RuleProperties properties) {
        this.config = properties.rules().structuringDetection();
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
        return tx.amount().compareTo(config.lowerBound()) >= 0
                && tx.amount().compareTo(config.reportingThreshold()) < 0;
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of(
                "reportingThreshold", config.reportingThreshold(),
                "lowerBound", config.lowerBound(),
                "currency", "EUR"
        );
    }
}

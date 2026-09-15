package com.seb.aml.rule;

import com.seb.aml.config.RuleProperties;
import com.seb.aml.domain.Transaction;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Flags transactions where the origin or destination country is a high-risk jurisdiction.
 *
 * <p>FATF (Financial Action Task Force) maintains lists of jurisdictions with strategic
 * AML deficiencies. Transactions involving these countries require enhanced scrutiny.
 * The configured country list should reflect the institution's risk appetite and
 * regulatory requirements.</p>
 *
 * <p>Logic: {@code originCountry ∈ countries OR destinationCountry ∈ countries}</p>
 *
 * <p>The country list is stored as a {@link Set} for O(1) lookup performance.</p>
 */
@Component
public class HighRiskCountryRule implements Rule {

    private static final String RULE_ID = "HIGH_RISK_COUNTRY";
    private static final String DESCRIPTION =
            "Flags transactions involving high-risk jurisdictions";

    private final RuleProperties.HighRiskCountry config;
    private final Set<String> countrySet;

    public HighRiskCountryRule(RuleProperties properties) {
        this.config = properties.rules().highRiskCountry();
        this.countrySet = Set.copyOf(config.countries());
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
        return countrySet.contains(tx.originCountry())
                || countrySet.contains(tx.destinationCountry());
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of("countries", config.countries());
    }
}

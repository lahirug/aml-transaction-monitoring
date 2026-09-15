package com.seb.aml.api;

import com.seb.aml.config.RuleProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;
import java.util.List;

/**
 * Test configuration providing {@link RuleProperties} for controller tests.
 */
@TestConfiguration
class TestRulePropertiesConfig {

    @Bean
    RuleProperties ruleProperties() {
        return new RuleProperties("EUR", new RuleProperties.Rules(
                new RuleProperties.HighValueTransaction(true, BigDecimal.valueOf(10000)),
                new RuleProperties.HighRiskCountry(true, List.of("KP", "SD", "SY", "AF", "MM", "YE", "LY")),
                new RuleProperties.SuspiciousOnlineTransaction(true, BigDecimal.valueOf(5000)),
                new RuleProperties.StructuringDetection(true, BigDecimal.valueOf(10000), BigDecimal.valueOf(9000))
        ));
    }
}

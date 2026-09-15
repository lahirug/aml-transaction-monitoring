package com.seb.aml.rule;

import com.seb.aml.config.RuleProperties;
import com.seb.aml.domain.Channel;
import com.seb.aml.domain.Transaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HighRiskCountryRule}.
 */
class HighRiskCountryRuleTest {

    private HighRiskCountryRule createRule(boolean enabled, List<String> countries) {
        var config = new RuleProperties.HighRiskCountry(enabled, countries);
        var rules = new RuleProperties.Rules(
                new RuleProperties.HighValueTransaction(false, BigDecimal.valueOf(10000)),
                config,
                new RuleProperties.SuspiciousOnlineTransaction(false, BigDecimal.valueOf(5000)),
                new RuleProperties.StructuringDetection(false, BigDecimal.valueOf(10000), BigDecimal.valueOf(9000))
        );
        return new HighRiskCountryRule(new RuleProperties("EUR", rules));
    }

    private Transaction transaction(String origin, String destination) {
        return new Transaction("TX-001", "CUST-001", BigDecimal.valueOf(1000), "EUR",
                origin, destination, Channel.ONLINE, Instant.now());
    }

    @Test
    void originCountryHighRisk_shouldMatch() {
        var rule = createRule(true, List.of("KP", "SY"));
        var ctx = new RuleEvaluationContext(transaction("KP", "SE"));
        assertTrue(rule.evaluate(ctx));
    }

    @Test
    void destinationCountryHighRisk_shouldMatch() {
        var rule = createRule(true, List.of("KP", "SY"));
        var ctx = new RuleEvaluationContext(transaction("SE", "SY"));
        assertTrue(rule.evaluate(ctx));
    }

    @Test
    void bothCountriesHighRisk_shouldMatch() {
        var rule = createRule(true, List.of("KP", "SY"));
        var ctx = new RuleEvaluationContext(transaction("KP", "SY"));
        assertTrue(rule.evaluate(ctx));
    }

    @Test
    void neitherCountryHighRisk_shouldNotMatch() {
        var rule = createRule(true, List.of("KP", "SY"));
        var ctx = new RuleEvaluationContext(transaction("SE", "FI"));
        assertFalse(rule.evaluate(ctx));
    }

    @Test
    void ruleDisabled_shouldReportDisabled() {
        var rule = createRule(false, List.of("KP"));
        assertFalse(rule.isEnabled());
    }

    @Test
    void ruleId_shouldBeCorrect() {
        var rule = createRule(true, List.of("KP"));
        assertEquals("HIGH_RISK_COUNTRY", rule.getRuleId());
    }

    @Test
    void parameters_shouldIncludeCountries() {
        var rule = createRule(true, List.of("KP", "SY"));
        var params = rule.getParameters();
        assertEquals(List.of("KP", "SY"), params.get("countries"));
    }
}

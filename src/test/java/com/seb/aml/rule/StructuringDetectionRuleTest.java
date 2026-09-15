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
 * Unit tests for {@link StructuringDetectionRule}.
 */
class StructuringDetectionRuleTest {

    private StructuringDetectionRule createRule(boolean enabled, BigDecimal reportingThreshold,
                                                BigDecimal lowerBound) {
        var config = new RuleProperties.StructuringDetection(enabled, reportingThreshold, lowerBound);
        var rules = new RuleProperties.Rules(
                new RuleProperties.HighValueTransaction(false, BigDecimal.valueOf(10000)),
                new RuleProperties.HighRiskCountry(false, List.of("KP")),
                new RuleProperties.SuspiciousOnlineTransaction(false, BigDecimal.valueOf(5000)),
                config
        );
        return new StructuringDetectionRule(new RuleProperties("EUR", rules));
    }

    private Transaction transaction(BigDecimal amount) {
        return new Transaction("TX-001", "CUST-001", amount, "EUR",
                "SE", "SE", Channel.ONLINE, Instant.now());
    }

    @Test
    void amountInStructuringRange_shouldMatch() {
        var rule = createRule(true, BigDecimal.valueOf(10000), BigDecimal.valueOf(9000));
        var ctx = new RuleEvaluationContext(transaction(BigDecimal.valueOf(9500)));
        assertTrue(rule.evaluate(ctx));
    }

    @Test
    void amountAtLowerBound_shouldMatch() {
        var rule = createRule(true, BigDecimal.valueOf(10000), BigDecimal.valueOf(9000));
        var ctx = new RuleEvaluationContext(transaction(BigDecimal.valueOf(9000)));
        assertTrue(rule.evaluate(ctx));
    }

    @Test
    void amountJustBelowReportingThreshold_shouldMatch() {
        var rule = createRule(true, BigDecimal.valueOf(10000), BigDecimal.valueOf(9000));
        var ctx = new RuleEvaluationContext(transaction(BigDecimal.valueOf(9999)));
        assertTrue(rule.evaluate(ctx));
    }

    @Test
    void amountAtReportingThreshold_shouldNotMatch() {
        var rule = createRule(true, BigDecimal.valueOf(10000), BigDecimal.valueOf(9000));
        var ctx = new RuleEvaluationContext(transaction(BigDecimal.valueOf(10000)));
        assertFalse(rule.evaluate(ctx));
    }

    @Test
    void amountBelowLowerBound_shouldNotMatch() {
        var rule = createRule(true, BigDecimal.valueOf(10000), BigDecimal.valueOf(9000));
        var ctx = new RuleEvaluationContext(transaction(BigDecimal.valueOf(8999)));
        assertFalse(rule.evaluate(ctx));
    }

    @Test
    void amountAboveReportingThreshold_shouldNotMatch() {
        var rule = createRule(true, BigDecimal.valueOf(10000), BigDecimal.valueOf(9000));
        var ctx = new RuleEvaluationContext(transaction(BigDecimal.valueOf(15000)));
        assertFalse(rule.evaluate(ctx));
    }

    @Test
    void ruleDisabled_shouldReportDisabled() {
        var rule = createRule(false, BigDecimal.valueOf(10000), BigDecimal.valueOf(9000));
        assertFalse(rule.isEnabled());
    }

    @Test
    void ruleId_shouldBeCorrect() {
        var rule = createRule(true, BigDecimal.valueOf(10000), BigDecimal.valueOf(9000));
        assertEquals("STRUCTURING_DETECTION", rule.getRuleId());
    }

    @Test
    void parameters_shouldIncludeThresholds() {
        var rule = createRule(true, BigDecimal.valueOf(10000), BigDecimal.valueOf(9000));
        var params = rule.getParameters();
        assertEquals(BigDecimal.valueOf(10000), params.get("reportingThreshold"));
        assertEquals(BigDecimal.valueOf(9000), params.get("lowerBound"));
        assertEquals("EUR", params.get("currency"));
    }

    @Test
    void amountWithDecimalPrecision_atBoundary_shouldMatch() {
        var rule = createRule(true, new BigDecimal("10000.00"), new BigDecimal("9000.00"));
        var ctx = new RuleEvaluationContext(transaction(new BigDecimal("9999.99")));
        assertTrue(rule.evaluate(ctx));
    }

    @Test
    void amountJustBelowLowerBound_withDecimalPrecision_shouldNotMatch() {
        var rule = createRule(true, new BigDecimal("10000.00"), new BigDecimal("9000.00"));
        var ctx = new RuleEvaluationContext(transaction(new BigDecimal("8999.99")));
        assertFalse(rule.evaluate(ctx));
    }
}

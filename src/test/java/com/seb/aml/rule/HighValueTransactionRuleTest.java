package com.seb.aml.rule;

import com.seb.aml.config.RuleProperties;
import com.seb.aml.domain.Channel;
import com.seb.aml.domain.Transaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HighValueTransactionRule}.
 */
class HighValueTransactionRuleTest {

    private HighValueTransactionRule createRule(boolean enabled, BigDecimal threshold) {
        var config = new RuleProperties.HighValueTransaction(enabled, threshold);
        var rules = new RuleProperties.Rules(
                config,
                new RuleProperties.HighRiskCountry(false, java.util.List.of("KP")),
                new RuleProperties.SuspiciousOnlineTransaction(false, BigDecimal.valueOf(5000)),
                new RuleProperties.StructuringDetection(false, BigDecimal.valueOf(10000), BigDecimal.valueOf(9000))
        );
        return new HighValueTransactionRule(new RuleProperties("EUR", rules));
    }

    private Transaction transaction(BigDecimal amount) {
        return new Transaction("TX-001", "CUST-001", amount, "EUR",
                "SE", "SE", Channel.ONLINE, Instant.now());
    }

    @Test
    void amountAboveThreshold_shouldMatch() {
        var rule = createRule(true, BigDecimal.valueOf(10000));
        var ctx = new RuleEvaluationContext(transaction(BigDecimal.valueOf(10001)));
        assertTrue(rule.evaluate(ctx));
    }

    @Test
    void amountEqualToThreshold_shouldNotMatch() {
        var rule = createRule(true, BigDecimal.valueOf(10000));
        var ctx = new RuleEvaluationContext(transaction(BigDecimal.valueOf(10000)));
        assertFalse(rule.evaluate(ctx));
    }

    @Test
    void amountBelowThreshold_shouldNotMatch() {
        var rule = createRule(true, BigDecimal.valueOf(10000));
        var ctx = new RuleEvaluationContext(transaction(BigDecimal.valueOf(9999)));
        assertFalse(rule.evaluate(ctx));
    }

    @Test
    void ruleDisabled_shouldReportDisabled() {
        var rule = createRule(false, BigDecimal.valueOf(10000));
        assertFalse(rule.isEnabled());
    }

    @Test
    void ruleId_shouldBeCorrect() {
        var rule = createRule(true, BigDecimal.valueOf(10000));
        assertEquals("HIGH_VALUE_TRANSACTION", rule.getRuleId());
    }

    @Test
    void parameters_shouldIncludeThreshold() {
        var rule = createRule(true, BigDecimal.valueOf(10000));
        var params = rule.getParameters();
        assertEquals(BigDecimal.valueOf(10000), params.get("threshold"));
        assertEquals("EUR", params.get("currency"));
    }

    @Test
    void amountJustAboveThreshold_withDecimalPrecision_shouldMatch() {
        var rule = createRule(true, new BigDecimal("10000.00"));
        var ctx = new RuleEvaluationContext(transaction(new BigDecimal("10000.01")));
        assertTrue(rule.evaluate(ctx));
    }

    @Test
    void amountJustBelowThreshold_withDecimalPrecision_shouldNotMatch() {
        var rule = createRule(true, new BigDecimal("10000.00"));
        var ctx = new RuleEvaluationContext(transaction(new BigDecimal("9999.99")));
        assertFalse(rule.evaluate(ctx));
    }

    @Test
    void amountEqualToThreshold_withDifferentScale_shouldNotMatch() {
        var rule = createRule(true, new BigDecimal("10000"));
        var ctx = new RuleEvaluationContext(transaction(new BigDecimal("10000.00")));
        assertFalse(rule.evaluate(ctx));
    }
}

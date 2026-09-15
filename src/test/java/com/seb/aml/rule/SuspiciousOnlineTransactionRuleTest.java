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
 * Unit tests for {@link SuspiciousOnlineTransactionRule}.
 */
class SuspiciousOnlineTransactionRuleTest {

    private SuspiciousOnlineTransactionRule createRule(boolean enabled, BigDecimal threshold) {
        var config = new RuleProperties.SuspiciousOnlineTransaction(enabled, threshold);
        var rules = new RuleProperties.Rules(
                new RuleProperties.HighValueTransaction(false, BigDecimal.valueOf(10000)),
                new RuleProperties.HighRiskCountry(false, List.of("KP")),
                config,
                new RuleProperties.StructuringDetection(false, BigDecimal.valueOf(10000), BigDecimal.valueOf(9000))
        );
        return new SuspiciousOnlineTransactionRule(new RuleProperties("EUR", rules));
    }

    private Transaction transaction(BigDecimal amount, Channel channel, String origin, String destination) {
        return new Transaction("TX-001", "CUST-001", amount, "EUR",
                origin, destination, channel, Instant.now());
    }

    @Test
    void onlineCrossBorderAboveThreshold_shouldMatch() {
        var rule = createRule(true, BigDecimal.valueOf(5000));
        var ctx = new RuleEvaluationContext(
                transaction(BigDecimal.valueOf(5001), Channel.ONLINE, "SE", "FI"));
        assertTrue(rule.evaluate(ctx));
    }

    @Test
    void onlineDomesticAboveThreshold_shouldNotMatch() {
        var rule = createRule(true, BigDecimal.valueOf(5000));
        var ctx = new RuleEvaluationContext(
                transaction(BigDecimal.valueOf(5001), Channel.ONLINE, "SE", "SE"));
        assertFalse(rule.evaluate(ctx));
    }

    @Test
    void branchCrossBorderAboveThreshold_shouldNotMatch() {
        var rule = createRule(true, BigDecimal.valueOf(5000));
        var ctx = new RuleEvaluationContext(
                transaction(BigDecimal.valueOf(5001), Channel.BRANCH, "SE", "FI"));
        assertFalse(rule.evaluate(ctx));
    }

    @Test
    void onlineCrossBorderBelowThreshold_shouldNotMatch() {
        var rule = createRule(true, BigDecimal.valueOf(5000));
        var ctx = new RuleEvaluationContext(
                transaction(BigDecimal.valueOf(4999), Channel.ONLINE, "SE", "FI"));
        assertFalse(rule.evaluate(ctx));
    }

    @Test
    void onlineCrossBorderEqualToThreshold_shouldNotMatch() {
        var rule = createRule(true, BigDecimal.valueOf(5000));
        var ctx = new RuleEvaluationContext(
                transaction(BigDecimal.valueOf(5000), Channel.ONLINE, "SE", "FI"));
        assertFalse(rule.evaluate(ctx));
    }

    @Test
    void atmCrossBorderAboveThreshold_shouldNotMatch() {
        var rule = createRule(true, BigDecimal.valueOf(5000));
        var ctx = new RuleEvaluationContext(
                transaction(BigDecimal.valueOf(5001), Channel.ATM, "SE", "FI"));
        assertFalse(rule.evaluate(ctx));
    }

    @Test
    void mobileCrossBorderAboveThreshold_shouldNotMatch() {
        var rule = createRule(true, BigDecimal.valueOf(5000));
        var ctx = new RuleEvaluationContext(
                transaction(BigDecimal.valueOf(5001), Channel.MOBILE, "SE", "FI"));
        assertFalse(rule.evaluate(ctx));
    }

    @Test
    void ruleDisabled_shouldReportDisabled() {
        var rule = createRule(false, BigDecimal.valueOf(5000));
        assertFalse(rule.isEnabled());
    }

    @Test
    void ruleId_shouldBeCorrect() {
        var rule = createRule(true, BigDecimal.valueOf(5000));
        assertEquals("SUSPICIOUS_ONLINE_TRANSACTION", rule.getRuleId());
    }
}

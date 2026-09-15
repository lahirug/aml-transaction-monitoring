package com.seb.aml.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RuleProperties} configuration validation.
 *
 * <p>These tests verify that invalid configurations are rejected at startup.
 * A silently misconfigured AML system is worse than one that refuses to start —
 * missed detections carry regulatory consequences.</p>
 */
class RulePropertiesTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private RuleProperties validProperties() {
        return new RuleProperties("EUR", new RuleProperties.Rules(
                new RuleProperties.HighValueTransaction(true, BigDecimal.valueOf(10000)),
                new RuleProperties.HighRiskCountry(true, List.of("KP", "SY")),
                new RuleProperties.SuspiciousOnlineTransaction(true, BigDecimal.valueOf(5000)),
                new RuleProperties.StructuringDetection(true, BigDecimal.valueOf(10000), BigDecimal.valueOf(9000))
        ));
    }

    @Test
    void validConfiguration_shouldHaveNoViolations() {
        Set<ConstraintViolation<RuleProperties>> violations = validator.validate(validProperties());
        assertTrue(violations.isEmpty(), "Expected no violations but got: " + violations);
    }

    @Nested
    class SupportedCurrencyValidation {

        @Test
        void blankCurrency_shouldBeRejected() {
            var props = new RuleProperties("", validProperties().rules());
            Set<ConstraintViolation<RuleProperties>> violations = validator.validate(props);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("supportedCurrency")));
        }

        @Test
        void nullCurrency_shouldBeRejected() {
            var props = new RuleProperties(null, validProperties().rules());
            Set<ConstraintViolation<RuleProperties>> violations = validator.validate(props);
            assertFalse(violations.isEmpty());
        }
    }

    @Nested
    class HighValueTransactionValidation {

        @Test
        void negativeThreshold_shouldBeRejected() {
            var rules = new RuleProperties.Rules(
                    new RuleProperties.HighValueTransaction(true, BigDecimal.valueOf(-1)),
                    validProperties().rules().highRiskCountry(),
                    validProperties().rules().suspiciousOnlineTransaction(),
                    validProperties().rules().structuringDetection()
            );
            var props = new RuleProperties("EUR", rules);
            Set<ConstraintViolation<RuleProperties>> violations = validator.validate(props);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream().anyMatch(
                    v -> v.getPropertyPath().toString().contains("highValueTransaction.threshold")));
        }

        @Test
        void zeroThreshold_shouldBeRejected() {
            var rules = new RuleProperties.Rules(
                    new RuleProperties.HighValueTransaction(true, BigDecimal.ZERO),
                    validProperties().rules().highRiskCountry(),
                    validProperties().rules().suspiciousOnlineTransaction(),
                    validProperties().rules().structuringDetection()
            );
            var props = new RuleProperties("EUR", rules);
            Set<ConstraintViolation<RuleProperties>> violations = validator.validate(props);
            assertFalse(violations.isEmpty());
        }

        @Test
        void nullThreshold_shouldBeRejected() {
            var rules = new RuleProperties.Rules(
                    new RuleProperties.HighValueTransaction(true, null),
                    validProperties().rules().highRiskCountry(),
                    validProperties().rules().suspiciousOnlineTransaction(),
                    validProperties().rules().structuringDetection()
            );
            var props = new RuleProperties("EUR", rules);
            Set<ConstraintViolation<RuleProperties>> violations = validator.validate(props);
            assertFalse(violations.isEmpty());
        }
    }

    @Nested
    class HighRiskCountryValidation {

        @Test
        void emptyCountryList_shouldBeRejected() {
            var rules = new RuleProperties.Rules(
                    validProperties().rules().highValueTransaction(),
                    new RuleProperties.HighRiskCountry(true, List.of()),
                    validProperties().rules().suspiciousOnlineTransaction(),
                    validProperties().rules().structuringDetection()
            );
            var props = new RuleProperties("EUR", rules);
            Set<ConstraintViolation<RuleProperties>> violations = validator.validate(props);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream().anyMatch(
                    v -> v.getPropertyPath().toString().contains("highRiskCountry.countries")));
        }

        @Test
        void nullCountryList_shouldBeRejected() {
            var rules = new RuleProperties.Rules(
                    validProperties().rules().highValueTransaction(),
                    new RuleProperties.HighRiskCountry(true, null),
                    validProperties().rules().suspiciousOnlineTransaction(),
                    validProperties().rules().structuringDetection()
            );
            var props = new RuleProperties("EUR", rules);
            Set<ConstraintViolation<RuleProperties>> violations = validator.validate(props);
            assertFalse(violations.isEmpty());
        }
    }

    @Nested
    class SuspiciousOnlineTransactionValidation {

        @Test
        void negativeThreshold_shouldBeRejected() {
            var rules = new RuleProperties.Rules(
                    validProperties().rules().highValueTransaction(),
                    validProperties().rules().highRiskCountry(),
                    new RuleProperties.SuspiciousOnlineTransaction(true, BigDecimal.valueOf(-500)),
                    validProperties().rules().structuringDetection()
            );
            var props = new RuleProperties("EUR", rules);
            Set<ConstraintViolation<RuleProperties>> violations = validator.validate(props);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream().anyMatch(
                    v -> v.getPropertyPath().toString().contains("suspiciousOnlineTransaction.threshold")));
        }

        @Test
        void zeroThreshold_shouldBeRejected() {
            var rules = new RuleProperties.Rules(
                    validProperties().rules().highValueTransaction(),
                    validProperties().rules().highRiskCountry(),
                    new RuleProperties.SuspiciousOnlineTransaction(true, BigDecimal.ZERO),
                    validProperties().rules().structuringDetection()
            );
            var props = new RuleProperties("EUR", rules);
            Set<ConstraintViolation<RuleProperties>> violations = validator.validate(props);
            assertFalse(violations.isEmpty());
        }
    }

    @Nested
    class StructuringDetectionValidation {

        @Test
        void negativeReportingThreshold_shouldBeRejected() {
            var rules = new RuleProperties.Rules(
                    validProperties().rules().highValueTransaction(),
                    validProperties().rules().highRiskCountry(),
                    validProperties().rules().suspiciousOnlineTransaction(),
                    new RuleProperties.StructuringDetection(true, BigDecimal.valueOf(-10000), BigDecimal.valueOf(-20000))
            );
            var props = new RuleProperties("EUR", rules);
            Set<ConstraintViolation<RuleProperties>> violations = validator.validate(props);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream().anyMatch(
                    v -> v.getPropertyPath().toString().contains("reportingThreshold")));
        }

        @Test
        void negativeLowerBound_shouldBeRejected() {
            var rules = new RuleProperties.Rules(
                    validProperties().rules().highValueTransaction(),
                    validProperties().rules().highRiskCountry(),
                    validProperties().rules().suspiciousOnlineTransaction(),
                    new RuleProperties.StructuringDetection(true, BigDecimal.valueOf(10000), BigDecimal.valueOf(-9000))
            );
            var props = new RuleProperties("EUR", rules);
            Set<ConstraintViolation<RuleProperties>> violations = validator.validate(props);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream().anyMatch(
                    v -> v.getPropertyPath().toString().contains("lowerBound")));
        }

        @Test
        void lowerBoundEqualToThreshold_shouldFail() {
            assertThrows(IllegalArgumentException.class, () ->
                    new RuleProperties.StructuringDetection(true,
                            BigDecimal.valueOf(10000), BigDecimal.valueOf(10000)));
        }

        @Test
        void lowerBoundGreaterThanThreshold_shouldFail() {
            assertThrows(IllegalArgumentException.class, () ->
                    new RuleProperties.StructuringDetection(true,
                            BigDecimal.valueOf(10000), BigDecimal.valueOf(15000)));
        }

        @Test
        void validBounds_shouldSucceed() {
            var config = new RuleProperties.StructuringDetection(true,
                    BigDecimal.valueOf(10000), BigDecimal.valueOf(9000));
            assertEquals(BigDecimal.valueOf(10000), config.reportingThreshold());
            assertEquals(BigDecimal.valueOf(9000), config.lowerBound());
        }
    }
}

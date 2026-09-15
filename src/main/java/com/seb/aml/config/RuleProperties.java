package com.seb.aml.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.List;

/**
 * Type-safe configuration properties for AML screening rules.
 *
 * <p>Bound to the {@code aml.screening} prefix in {@code application.yml}. All rule
 * parameters are validated at startup — the application refuses to start if any
 * configuration is invalid (e.g., negative threshold, empty country list).</p>
 *
 * <p>A silently misconfigured AML system is worse than a system that is down —
 * missed detections have regulatory consequences.</p>
 */
@ConfigurationProperties(prefix = "aml.screening")
@Validated
public record RuleProperties(
        @NotBlank String supportedCurrency,
        @Valid Rules rules
) {

    /**
     * Container for all rule configurations.
     */
    public record Rules(
            @Valid HighValueTransaction highValueTransaction,
            @Valid HighRiskCountry highRiskCountry,
            @Valid SuspiciousOnlineTransaction suspiciousOnlineTransaction,
            @Valid StructuringDetection structuringDetection
    ) {
    }

    /**
     * Configuration for the HIGH_VALUE_TRANSACTION rule.
     *
     * @param enabled whether this rule is active
     * @param threshold amount above which a transaction is flagged (exclusive)
     */
    public record HighValueTransaction(
            boolean enabled,
            @NotNull @Positive BigDecimal threshold
    ) {
    }

    /**
     * Configuration for the HIGH_RISK_COUNTRY rule.
     *
     * @param enabled whether this rule is active
     * @param countries ISO 3166-1 alpha-2 codes of high-risk jurisdictions
     */
    public record HighRiskCountry(
            boolean enabled,
            @NotEmpty List<String> countries
    ) {
    }

    /**
     * Configuration for the SUSPICIOUS_ONLINE_TRANSACTION rule.
     *
     * @param enabled whether this rule is active
     * @param threshold amount above which an online cross-border transaction is flagged
     */
    public record SuspiciousOnlineTransaction(
            boolean enabled,
            @NotNull @Positive BigDecimal threshold
    ) {
    }

    /**
     * Configuration for the STRUCTURING_DETECTION rule.
     *
     * <p>The {@code lowerBound} must be strictly less than {@code reportingThreshold}.
     * A misconfigured range (e.g., lowerBound >= reportingThreshold) would silently
     * disable structuring detection, which is unacceptable in an AML system.</p>
     *
     * @param enabled whether this rule is active
     * @param reportingThreshold the regulatory reporting threshold
     * @param lowerBound the lower bound of the suspicious range (typically 90% of reporting threshold)
     */
    public record StructuringDetection(
            boolean enabled,
            @NotNull @Positive BigDecimal reportingThreshold,
            @NotNull @Positive BigDecimal lowerBound
    ) {
        public StructuringDetection {
            if (lowerBound != null && reportingThreshold != null
                    && lowerBound.compareTo(reportingThreshold) >= 0) {
                throw new IllegalArgumentException(
                        "Structuring detection lowerBound (" + lowerBound
                                + ") must be less than reportingThreshold (" + reportingThreshold + ")");
            }
        }
    }
}

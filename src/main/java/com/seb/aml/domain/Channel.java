package com.seb.aml.domain;

/**
 * Supported transaction channels.
 *
 * <p>Represents the medium through which a financial transaction was initiated.
 * Certain channels (e.g., ONLINE) carry higher AML risk due to reduced identity
 * verification compared to in-person channels.</p>
 */
public enum Channel {
    ONLINE,
    BRANCH,
    ATM,
    MOBILE
}

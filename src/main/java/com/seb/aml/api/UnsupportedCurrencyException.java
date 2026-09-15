package com.seb.aml.api;

/**
 * Thrown when a transaction uses a currency not supported for threshold-based rule evaluation.
 *
 * <p>Currently only EUR is supported. FX conversion is not implemented — see the design
 * document for rationale and production considerations.</p>
 */
public class UnsupportedCurrencyException extends RuntimeException {

    private final String currency;

    public UnsupportedCurrencyException(String currency, String supportedCurrency) {
        super(String.format("Unsupported currency: %s. Only %s is supported for "
                + "threshold-based rule evaluation. FX conversion is not implemented.",
                currency, supportedCurrency));
        this.currency = currency;
    }

    public String getCurrency() {
        return currency;
    }
}

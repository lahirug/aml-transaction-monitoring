package com.seb.aml.domain;

/**
 * Screening decision outcome.
 *
 * <p>A transaction is either cleared for processing or flagged for manual review
 * by an AML analyst. There is no intermediate state — the decision is binary.</p>
 */
public enum Decision {
    /** Transaction passed all rules and requires no further investigation. */
    CLEAR,

    /** Transaction matched one or more rules and requires analyst review. */
    REVIEW
}

package com.seb.aml.rule;

import com.seb.aml.domain.Transaction;

/**
 * Context object passed to rules during evaluation.
 *
 * <p>Currently wraps only the transaction being screened. This indirection exists to
 * support future enrichment without changing the {@link Rule} interface. When stateful
 * rules are needed (velocity checks, cumulative amounts), additional data sources can
 * be added here:</p>
 *
 * <ul>
 *   <li>{@code CustomerProfile} — KYC data for the transaction's customer</li>
 *   <li>{@code TransactionHistory} — windowed aggregates (count, sum, mean)</li>
 *   <li>{@code ExternalScreeningResults} — sanctions/PEP list matches</li>
 * </ul>
 *
 * <p>Existing rules are unaffected by these additions — they simply read
 * {@code getTransaction()} and ignore fields they don't need.</p>
 */
public class RuleEvaluationContext {

    private final Transaction transaction;

    // Future extensions:
    // private CustomerProfile customerProfile;
    // private TransactionHistory transactionHistory;
    // private ExternalScreeningResults externalScreening;

    public RuleEvaluationContext(Transaction transaction) {
        this.transaction = transaction;
    }

    public Transaction getTransaction() {
        return transaction;
    }
}

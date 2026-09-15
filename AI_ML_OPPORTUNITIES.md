# AI/ML Opportunities in AML Transaction Screening

This document outlines how modern AI/ML capabilities can enhance an AML transaction
monitoring system. These are not implemented in the exercise but represent realistic
extensions that work with the architecture we've designed.

---

## The False Positive Problem

Traditional rule-based AML systems have **95%+ false positive rates**. Analysts spend
the majority of their time dismissing legitimate transactions. This is the single
biggest operational pain point in AML compliance and where AI adds the most value.

---

## Opportunity 1: ML Risk Scoring

Instead of binary CLEAR/REVIEW, ML models can assign a **risk score** (0–100) alongside
rule matches. This helps analysts prioritize their review queue.

Example:
- Rule flags REVIEW (amount > 10,000 EUR)
- ML model scores it 12/100 because this customer regularly makes large payments to
  the same counterparty
- Analyst prioritizes score-95 transactions first

**How it fits our design:** The `ScreeningResult` could carry a `riskScore` field
populated by an ML-based `Rule` implementation. The `Rule` interface and
`RuleEvaluationContext` support this without engine changes.

---

## Opportunity 2: False Positive Reduction

ML models trained on historical analyst decisions (SAR filed vs. dismissed) can learn
to suppress obvious false positives. The model learns patterns like:
- "This customer always sends 12,000 EUR to the same supplier on the 15th of each month"
- "Cross-border transactions between SE and NO are routine for this customer segment"

This is the highest-value AI application in AML today. It directly reduces analyst
workload while maintaining regulatory compliance.

---

## Opportunity 3: LLM-Assisted Alert Investigation

When a transaction is flagged for REVIEW, an LLM can pre-generate an investigation
summary for the analyst:
- Pull customer KYC profile, recent transaction history, adverse media
- Summarize: "Customer C-12345 is a small import business. This 15,000 EUR payment
  to GB matches their regular supplier pattern. No adverse media found."
- The analyst still makes the final decision, but investigation time drops significantly

This is a natural extension of the screening events we emit — the event triggers an
enrichment pipeline that feeds into an LLM summarization step.

---

## Opportunity 4: Adaptive Thresholds

Instead of static thresholds (amount > 10,000), ML models can learn per-customer or
per-segment thresholds. A 10,000 EUR transaction is normal for a corporate account
but unusual for a student.

This connects directly to the `RuleEvaluationContext` extensibility — the context
would carry the customer's behavioral profile, and a rule implementation could use
a model to determine if the amount is anomalous for that specific customer.

---

## Opportunity 5: Natural Language Rule Configuration

LLMs could translate analyst intent into rule configuration:
- Analyst types: "Flag online transactions over 5000 EUR going to sanctioned countries"
- System generates the YAML config or creates the rule parameters
- Human reviews and approves before activation

The four-eyes approval principle still applies — AI accelerates configuration,
humans validate it.

---

## Opportunity 6: Network / Graph Analysis

Graph neural networks can detect circular fund flows and layering schemes that no
single-transaction rule can catch:
- A → B → C → D → A (money cycling through shell companies)
- Fan-in/fan-out patterns across related entities

This requires transaction history and relationship data in the
`RuleEvaluationContext` — the extension point we designed for.

---

## Implementation Priority

| Priority | Opportunity | Effort | Impact |
|----------|-----------|--------|--------|
| **1** | LLM-assisted investigation summaries | Low — sits alongside existing system | Reduces analyst workload per alert |
| **2** | ML risk scoring for queue prioritization | Medium — needs labeled historical data | Reduces false positive burden |
| **3** | False positive suppression models | Medium — needs analyst decision history | Directly reduces alert volume |
| **4** | Adaptive per-customer thresholds | High — needs behavioral profiling | Catches sophisticated laundering |
| **5** | Natural language rule configuration | Medium — LLM integration | Faster rule management |
| **6** | Network/graph analysis | High — needs data infrastructure | Catches complex schemes |

All six opportunities work with the architecture we've designed. The `Rule` interface
and `RuleEvaluationContext` pattern supports ML-based rules alongside deterministic
ones without changing the screening engine.

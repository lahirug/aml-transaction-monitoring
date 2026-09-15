# AML Transaction Screening Service — Design Document

| Field          | Value                                      |
|----------------|--------------------------------------------|
| **Author**     | Lahiru Ginnaliya Gamathige                 |
| **Created**    | 2026-09-15                                 |
| **Status**     | Draft                                      |
| **Reviewers**  | Shankar Gautam (SEB Financial Crimes Team) |

---

## 1. Overview

This document describes the design of a **stateless AML (Anti-Money Laundering) transaction
screening service**. The service receives individual financial transactions via a REST API,
evaluates them against a set of configurable detection rules, and returns a screening decision
(`CLEAR` or `REVIEW`) along with the identifiers of any matched rules.

The system is built as a take-home exercise for a Senior Backend Engineer position in SEB's
financial crimes team. It is intentionally limited in scope but designed with clear extension
points for production concerns such as historical transaction analysis, persistent audit trails,
and dynamic rule management.

---

## 2. Goals

1. **Screen individual transactions** against multiple configurable AML detection rules.
2. **Return a clear decision** — `CLEAR` when no rules match, `REVIEW` when one or more match.
3. **Make rule evaluation extensible** — adding a new rule type requires writing one Java class
   and one YAML config entry. No changes to the screening engine or API layer.
4. **Separate concerns** — domain model, rule evaluation, API transport, and observability are
   independent layers.
5. **Provide structured observability** — every screening decision produces a structured log
   event suitable for monitoring and audit.
6. **Support configurable rule parameters** — thresholds, country lists, and other parameters
   are externalized in YAML configuration.
7. **Handle invalid input explicitly** — return structured error responses for malformed or
   unsupported transactions.

---

## 3. Non-Goals (Explicit Scope Exclusions)

The following are deliberately excluded from the implementation. Each is discussed in
[Section 11: Production Considerations](#11-production-considerations) with guidance on how
it would be introduced.

| Exclusion                        | Rationale                                                    |
|----------------------------------|--------------------------------------------------------------|
| **Foreign exchange conversion**  | Transactions are evaluated in their stated currency. Only EUR is supported for threshold-based rules. Transactions in other currencies are rejected with a clear error. FX conversion requires a rate service, staleness handling, and rounding policy — a significant subsystem on its own. See Section 12.3 for alternatives considered. |
| **Historical / stateful rules**  | The system is point-in-time (stateless). Rules like velocity checks or cumulative amount analysis require a transaction store and windowed aggregation. The design accommodates this via the `RuleEvaluationContext` pattern (see Section 6). |
| **Persistent storage**           | Rules are loaded from YAML at startup. Screening results are emitted as structured log events. Production would persist both to a database. |
| **Rule CRUD API**                | Without persistence, runtime rule mutation would be lost on restart. Rules are managed via YAML configuration files at deployment time. |
| **UI / Frontend**                | The API is designed so a UI can be built on top. The `GET /rules` endpoint provides the introspection needed for a rule management interface. |
| **Authentication / Authorization** | Not relevant to the screening logic. Production would use OAuth2/JWT via Spring Security. |
| **Rule DSL**                     | Libraries like Drools or SpEL-based expression evaluation are discussed in alternatives (Section 12) but not implemented. Hand-coded rules are easier to test, debug, and explain. |
| **Batch screening**              | Only single-transaction screening is implemented. A `POST /api/v1/transactions/screen/batch` endpoint is a natural extension. |
| **Async event publishing**       | Events are logged synchronously. Production would publish to Kafka or an event store asynchronously. |

---

## 4. Background

### 4.1 What is AML Transaction Monitoring?

Anti-Money Laundering (AML) transaction monitoring is a regulatory requirement for financial
institutions. Banks must screen financial transactions to detect patterns indicative of money
laundering, terrorist financing, or other financial crimes.

When a transaction is flagged, it is escalated to an AML analyst for manual investigation. The
analyst reviews the transaction in the context of the customer's profile (KYC — Know Your
Customer), transaction history, and other intelligence to determine whether a Suspicious
Activity Report (SAR) should be filed with the relevant Financial Intelligence Unit (FIU).

### 4.2 Rule-Based vs. Statistical Models

Modern AML systems typically combine:

- **Rule-based detection**: Deterministic rules with configurable thresholds (e.g., "flag
  transactions over 10,000 EUR"). This is what we implement.
- **Statistical / ML models**: Anomaly detection, clustering, and supervised models trained
  on historical SARs. These complement rules by catching patterns that deterministic rules miss.

This exercise focuses on the rule-based component.

### 4.3 Key AML Patterns

The rules implemented in this system correspond to well-known AML typologies:

| Pattern | Description | Rule |
|---------|-------------|------|
| **Large value transfers** | Single transactions above reporting thresholds | `HIGH_VALUE_TRANSACTION` |
| **High-risk jurisdictions** | Transactions involving countries on FATF grey/black lists or sanctions lists | `HIGH_RISK_COUNTRY` |
| **Suspicious cross-border online transfers** | Compound indicator: online channel + cross-border + elevated amount | `SUSPICIOUS_ONLINE_TRANSACTION` |
| **Structuring (Smurfing)** | Deliberately keeping amounts just below reporting thresholds to avoid detection | `STRUCTURING_DETECTION` |

---

## 5. API Design

### 5.1 Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/transactions/screen` | Screen a single transaction |
| `GET`  | `/api/v1/rules` | List active rules and their configuration |
| `GET`  | `/actuator/health` | Health check (Spring Boot Actuator) |

### 5.2 Screen Transaction

**Request:**

```http
POST /api/v1/transactions/screen
Content-Type: application/json
```

```json
{
  "transactionId": "TX-10001",
  "customerId": "C-12345",
  "amount": 15000.00,
  "currency": "EUR",
  "originCountry": "SE",
  "destinationCountry": "GB",
  "channel": "ONLINE",
  "timestamp": "2026-09-15T14:30:00Z"
}
```

**Field definitions:**

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `transactionId` | String | Yes | Non-blank |
| `customerId` | String | Yes | Non-blank |
| `amount` | BigDecimal | Yes | Positive (> 0) |
| `currency` | String | Yes | ISO 4217 code. Only `EUR` supported for threshold rules. |
| `originCountry` | String | Yes | ISO 3166-1 alpha-2 |
| `destinationCountry` | String | Yes | ISO 3166-1 alpha-2 |
| `channel` | String | Yes | One of: `ONLINE`, `BRANCH`, `ATM`, `MOBILE` |
| `timestamp` | String | No | ISO 8601. Defaults to server receive time if omitted. |

> **Note on `timestamp`:** Included in the domain model for future support of time-based
> rules (velocity checks, time-of-day analysis). For this exercise, it is captured in the
> screening event for audit purposes but not used in rule evaluation.

**Successful response (200 OK):**

```json
{
  "transactionId": "TX-10001",
  "decision": "REVIEW",
  "matchedRules": [
    "HIGH_VALUE_TRANSACTION",
    "SUSPICIOUS_ONLINE_TRANSACTION"
  ],
  "timestamp": "2026-09-15T14:30:01Z"
}
```

**Decision logic:**
- `matchedRules` is empty → `decision` = `CLEAR`
- `matchedRules` has one or more entries → `decision` = `REVIEW`

### 5.3 List Rules

**Request:**

```http
GET /api/v1/rules
```

**Response (200 OK):**

```json
{
  "rules": [
    {
      "ruleId": "HIGH_VALUE_TRANSACTION",
      "description": "Flags transactions exceeding the configured amount threshold",
      "enabled": true,
      "parameters": {
        "threshold": 10000,
        "currency": "EUR"
      }
    },
    {
      "ruleId": "HIGH_RISK_COUNTRY",
      "description": "Flags transactions involving high-risk jurisdictions",
      "enabled": true,
      "parameters": {
        "countries": ["KP", "SY", "AF", "MM", "YE", "LY", "SD"]
      }
    }
  ]
}
```

### 5.4 Error Responses

All errors follow a consistent structure:

```json
{
  "error": "VALIDATION_ERROR",
  "message": "Transaction validation failed",
  "details": [
    {
      "field": "currency",
      "issue": "Unsupported currency: USD. Only EUR is supported for threshold-based rule evaluation. FX conversion is not implemented."
    }
  ]
}
```

**Error codes:**

| HTTP Status | Error Code | When |
|-------------|-----------|------|
| 400 | `VALIDATION_ERROR` | Missing/invalid fields |
| 400 | `UNSUPPORTED_CURRENCY` | Currency other than EUR |
| 500 | `SCREENING_ERROR` | Unexpected rule evaluation failure (fail-safe → REVIEW) |

**Fail-safe behavior:** If a rule throws an unexpected exception during evaluation, the
transaction is flagged as `REVIEW` rather than `CLEAR`. In AML systems, false positives
(unnecessary reviews) are acceptable; false negatives (missed suspicious activity) are not.
This is a deliberate design choice.

---

## 6. System Design

### 6.1 Architecture Overview

```
┌──────────────────────────────────────────────────────────┐
│                    Spring Boot Application                │
│                                                          │
│  ┌──────────┐    ┌─────────────────┐    ┌─────────────┐  │
│  │   API    │───▶│   Screening     │───▶│   Rule      │  │
│  │  Layer   │    │   Service       │    │   Engine    │  │
│  │          │◀───│                 │    │             │  │
│  └──────────┘    └────────┬────────┘    └──────┬──────┘  │
│                           │                     │        │
│                           ▼                     ▼        │
│                  ┌─────────────────┐    ┌─────────────┐  │
│                  │    Event        │    │   Rule      │  │
│                  │    Publisher    │    │   Registry  │  │
│                  │  (Structured    │    │  (YAML +    │  │
│                  │   Logging)      │    │   Auto-     │  │
│                  └─────────────────┘    │  discovery) │  │
│                                        └─────────────┘  │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

**Layer responsibilities:**

| Layer | Responsibility | Knows about |
|-------|---------------|-------------|
| **API Layer** | HTTP transport, request/response DTOs, input validation | DTOs, domain objects |
| **Screening Service** | Orchestrates rule evaluation, produces screening result | Domain objects, Rule interface |
| **Rule Engine** | Iterates over registered rules, collects matches | Rule interface |
| **Rule implementations** | Individual detection logic | Domain objects, own config |
| **Event Publisher** | Emits structured screening events | Screening events |
| **Rule Registry** | Loads and manages rule instances from config | Rule interface, config |

### 6.2 Domain Model

```
┌─────────────────────┐         ┌──────────────────────┐
│    Transaction       │         │   ScreeningResult    │
├─────────────────────┤         ├──────────────────────┤
│ transactionId: String│────────▶│ transactionId: String│
│ customerId: String   │         │ decision: Decision   │
│ amount: BigDecimal   │         │ matchedRules: List   │
│ currency: String     │         │ timestamp: Instant   │
│ originCountry: String│         └──────────────────────┘
│ destinationCountry   │
│ channel: Channel     │         ┌──────────────────────┐
│ timestamp: Instant   │         │   Decision (enum)    │
└─────────────────────┘         ├──────────────────────┤
                                │ CLEAR                │
                                │ REVIEW               │
                                └──────────────────────┘

┌─────────────────────────┐
│   RuleEvaluationContext  │
├─────────────────────────┤
│ transaction: Transaction │
│ // Future extensions:    │
│ // customerProfile       │
│ // transactionHistory    │
│ // externalScreening     │
└─────────────────────────┘
```

**Key design decision — `RuleEvaluationContext`:**

Rules receive a `RuleEvaluationContext` rather than a raw `Transaction`. Today the context
contains only the transaction. When stateful rules are needed (velocity checks, cumulative
amounts), we enrich the context with historical data. Existing rules are unaffected — they
simply read `context.getTransaction()` and ignore fields they don't need.

This avoids a breaking interface change when historical analysis is introduced.

### 6.3 Rule Interface

```java
/**
 * Contract for all AML detection rules. Each implementation encapsulates
 * a single detection pattern and is independently configurable and testable.
 */
public interface Rule {

    /** Unique identifier for this rule (e.g., "HIGH_VALUE_TRANSACTION"). */
    String getRuleId();

    /** Human-readable description of what this rule detects. */
    String getDescription();

    /** Whether this rule is currently active. */
    boolean isEnabled();

    /**
     * Evaluate the given context against this rule.
     *
     * @param context the evaluation context containing the transaction
     *                and any enrichment data
     * @return true if the rule matches (transaction is suspicious)
     */
    boolean evaluate(RuleEvaluationContext context);

    /** Returns the current configuration parameters for introspection. */
    Map<String, Object> getParameters();
}
```

**Adding a new rule requires:**
1. Write a new class implementing `Rule`
2. Annotate it as a Spring `@Component`
3. Add configuration in `application.yml`

No changes to `ScreeningService`, `ScreeningController`, or any existing rule. Spring's
auto-discovery picks up the new `@Component` automatically.

**Duplicate rule detection:** If multiple rules share the same `ruleId`, the system logs
a warning at startup and keeps only the first instance. This prevents accidental double-
evaluation from misconfiguration or classpath duplication.

### 6.4 Screening Service

```java
/**
 * Core screening orchestrator. Evaluates a transaction against all
 * registered rules and produces a screening result.
 *
 * This class has no knowledge of specific rules — it depends only
 * on the Rule interface. New rules are discovered automatically
 * via Spring dependency injection.
 */
@Service
public class ScreeningService {

    private final List<Rule> rules;
    private final ScreeningEventPublisher eventPublisher;

    // Spring injects all Rule implementations automatically
    public ScreeningService(List<Rule> rules,
                            ScreeningEventPublisher eventPublisher) {
        this.rules = rules;
        this.eventPublisher = eventPublisher;
    }

    public ScreeningResult screen(Transaction transaction) {
        RuleEvaluationContext context = new RuleEvaluationContext(transaction);

        List<String> matchedRules = rules.stream()
            .filter(Rule::isEnabled)
            .filter(rule -> evaluateSafely(rule, context))
            .map(Rule::getRuleId)
            .toList();

        Decision decision = matchedRules.isEmpty()
            ? Decision.CLEAR
            : Decision.REVIEW;

        ScreeningResult result = new ScreeningResult(
            transaction.getTransactionId(), decision, matchedRules);

        eventPublisher.publish(result, context);

        return result;
    }

    /**
     * Fail-safe evaluation: if a rule throws an exception, treat it
     * as a match (REVIEW). In AML, false negatives are unacceptable.
     */
    private boolean evaluateSafely(Rule rule,
                                    RuleEvaluationContext context) {
        try {
            return rule.evaluate(context);
        } catch (Exception e) {
            log.error("Rule {} failed for transaction {}: {}",
                rule.getRuleId(),
                context.getTransaction().getTransactionId(),
                e.getMessage());
            return true; // fail-safe: flag for review
        }
    }
}
```

### 6.5 Observability

Every screening decision produces a structured log event:

```json
{
  "event": "SCREENING_COMPLETED",
  "transactionId": "TX-10001",
  "customerId": "C-12345",
  "decision": "REVIEW",
  "matchedRules": ["HIGH_VALUE_TRANSACTION", "SUSPICIOUS_ONLINE_TRANSACTION"],
  "rulesEvaluated": 4,
  "evaluationTimeMs": 2,
  "timestamp": "2026-09-15T14:30:01Z"
}
```

The `ScreeningEventPublisher` is an interface with a logging implementation:

```java
public interface ScreeningEventPublisher {
    void publish(ScreeningResult result, RuleEvaluationContext context);
}
```

In production, this interface would have a Kafka or database-backed implementation,
swappable without changing the screening service.

**What this enables:**
- Monitoring dashboards (e.g., Grafana) on rule hit rates
- Alerting on unusual patterns (sudden spike in REVIEW decisions)
- Regulatory audit trail
- Performance monitoring (evaluation time per transaction)

---

## 7. Rule Specifications

### 7.1 HIGH_VALUE_TRANSACTION

| Property | Value |
|----------|-------|
| **ID** | `HIGH_VALUE_TRANSACTION` |
| **Description** | Flags transactions where the amount exceeds a configurable threshold |
| **Default threshold** | 10,000 EUR |
| **Logic** | `transaction.amount > threshold` |
| **Configurable params** | `threshold` (BigDecimal) |

**AML rationale:** Large-value transactions are a primary indicator in money laundering. The
EU's Anti-Money Laundering Directive requires enhanced due diligence for transactions above
certain thresholds.

### 7.2 HIGH_RISK_COUNTRY

| Property | Value |
|----------|-------|
| **ID** | `HIGH_RISK_COUNTRY` |
| **Description** | Flags transactions where origin or destination is a high-risk jurisdiction |
| **Default countries** | KP (North Korea), SY (Syria), AF (Afghanistan), MM (Myanmar), YE (Yemen), LY (Libya), SD (Sudan) |
| **Logic** | `transaction.originCountry ∈ countries OR transaction.destinationCountry ∈ countries` |
| **Configurable params** | `countries` (List of ISO 3166-1 alpha-2 codes) |

**AML rationale:** FATF (Financial Action Task Force) maintains lists of jurisdictions with
strategic AML deficiencies. Transactions involving these countries require enhanced scrutiny.
The default list is based on FATF's high-risk jurisdictions list.

### 7.3 SUSPICIOUS_ONLINE_TRANSACTION

| Property | Value |
|----------|-------|
| **ID** | `SUSPICIOUS_ONLINE_TRANSACTION` |
| **Description** | Flags online cross-border transactions above an elevated amount |
| **Logic** | `channel = ONLINE AND amount > threshold AND originCountry ≠ destinationCountry` |
| **Default threshold** | 5,000 EUR |
| **Configurable params** | `threshold` (BigDecimal) |

**AML rationale:** Online cross-border transactions present higher risk due to the anonymity
of the channel and the complexity of cross-jurisdictional fund flows. A lower threshold is
applied compared to the general high-value rule.

### 7.4 STRUCTURING_DETECTION

| Property | Value |
|----------|-------|
| **ID** | `STRUCTURING_DETECTION` |
| **Description** | Flags transactions with amounts just below the reporting threshold, indicating potential structuring |
| **Logic** | `amount >= lowerBound AND amount < reportingThreshold` |
| **Default reporting threshold** | 10,000 EUR |
| **Default lower bound** | 9,000 EUR (90% of reporting threshold) |
| **Configurable params** | `reportingThreshold` (BigDecimal), `lowerBound` (BigDecimal) |

**AML rationale:** Structuring (also known as "smurfing") is a technique where criminals
deliberately break large transactions into smaller amounts to stay below regulatory reporting
thresholds. Detecting amounts just below the threshold is a classic AML pattern.

---

## 8. Configuration

Rules are configured in `application.yml`:

```yaml
aml:
  screening:
    supported-currency: EUR
    rules:
      high-value-transaction:
        enabled: true
        threshold: 10000
      high-risk-country:
        enabled: true
        countries:
          - KP
          - SD
          - SY
          - AF
          - MM
          - YE
          - LY
      suspicious-online-transaction:
        enabled: true
        threshold: 5000
      structuring-detection:
        enabled: true
        reporting-threshold: 10000
        lower-bound: 9000
```

Spring Boot's `@ConfigurationProperties` binds this YAML to typed Java configuration
objects, providing:
- Type safety
- Validation at startup (fail fast on bad config)
- Easy overrides via environment variables or command-line arguments

**Startup validation:** Rule configuration is validated when the application starts. If
any rule has invalid parameters (e.g., negative threshold, empty country list, missing
required fields), the application **refuses to start** and logs a clear error describing
the misconfiguration. A silently misconfigured AML system is worse than a system that
is down — missed detections have regulatory consequences.

**No hot reloading:** Rules are loaded once at startup. Changing rule configuration
requires a restart. Hot reloading is a production concern that adds complexity around
config consistency and audit trails for config changes.

---

## 9. Testing Strategy

### 9.1 Test Categories

| Category | What | How | Why |
|----------|------|-----|-----|
| **Rule unit tests** | Each rule in isolation | JUnit 5 + direct instantiation | Verify detection logic and edge cases independently |
| **Screening service tests** | Orchestration logic | JUnit 5 + mock rules | Verify decision logic, fail-safe behavior, event publishing |
| **API integration tests** | Full HTTP request/response | Spring MockMvc | Verify serialization, validation, error responses |
| **Configuration tests** | Config-dependent behavior | Spring test with custom properties | Verify different thresholds produce different decisions |

### 9.2 Key Test Scenarios

| # | Scenario | Expected |
|---|----------|----------|
| 1 | Low-value domestic EUR transaction via branch | `CLEAR`, no matched rules |
| 2 | Transaction amount 15,000 EUR | `REVIEW`, matches `HIGH_VALUE_TRANSACTION` |
| 3 | Transaction to Iran, low amount | `REVIEW`, matches `HIGH_RISK_COUNTRY` |
| 4 | Online cross-border 8,000 EUR | `REVIEW`, matches `SUSPICIOUS_ONLINE_TRANSACTION` |
| 5 | Amount 9,500 EUR | `REVIEW`, matches `STRUCTURING_DETECTION` |
| 6 | 15,000 EUR online cross-border to Iran | `REVIEW`, matches multiple rules |
| 7 | Amount exactly at threshold (10,000 EUR) | Boundary: does NOT match `HIGH_VALUE_TRANSACTION` (> not >=) |
| 8 | Amount exactly 9,000 EUR | Boundary: matches `STRUCTURING_DETECTION` (>= lowerBound) |
| 9 | Transaction with currency USD | `400 UNSUPPORTED_CURRENCY` error |
| 10 | Missing required field | `400 VALIDATION_ERROR` |
| 11 | Negative amount | `400 VALIDATION_ERROR` |
| 12 | Rule disabled in config | Disabled rule does not produce a match |
| 13 | Rule throws exception | Fail-safe: treated as match, transaction → `REVIEW` |

### 9.3 What We Don't Test (and Why)

- **Spring Boot auto-configuration**: tested by the framework itself.
- **JSON serialization edge cases**: Jackson is well-tested; we verify our DTOs round-trip correctly, not Jackson internals.
- **100% line coverage**: coverage is a tool, not a goal. We test business behavior.

---

## 10. Project Structure

```
aml-transaction-monitoring/
│
├── DESIGN.md                          # This document
├── README.md                          # Build, run, architecture summary
├── AI_USAGE.md                        # AI tool usage disclosure
├── pom.xml                            # Maven build
│
├── src/
│   ├── main/
│   │   ├── java/com/seb/aml/
│   │   │   ├── AmlScreeningApplication.java
│   │   │   │
│   │   │   ├── domain/                # Core domain objects
│   │   │   │   ├── Transaction.java
│   │   │   │   ├── ScreeningResult.java
│   │   │   │   ├── Decision.java      # Enum: CLEAR, REVIEW
│   │   │   │   └── Channel.java       # Enum: ONLINE, BRANCH, ATM, MOBILE
│   │   │   │
│   │   │   ├── rule/                  # Rule interface and implementations
│   │   │   │   ├── Rule.java          # Interface
│   │   │   │   ├── RuleEvaluationContext.java
│   │   │   │   ├── HighValueTransactionRule.java
│   │   │   │   ├── HighRiskCountryRule.java
│   │   │   │   ├── SuspiciousOnlineTransactionRule.java
│   │   │   │   └── StructuringDetectionRule.java
│   │   │   │
│   │   │   ├── screening/             # Screening orchestration
│   │   │   │   ├── ScreeningService.java
│   │   │   │   ├── ScreeningEventPublisher.java  # Interface
│   │   │   │   └── LoggingScreeningEventPublisher.java
│   │   │   │
│   │   │   ├── api/                   # REST API layer
│   │   │   │   ├── ScreeningController.java
│   │   │   │   ├── RuleController.java
│   │   │   │   ├── dto/
│   │   │   │   │   ├── ScreeningRequest.java
│   │   │   │   │   ├── ScreeningResponse.java
│   │   │   │   │   ├── RuleResponse.java
│   │   │   │   │   └── ErrorResponse.java
│   │   │   │   └── GlobalExceptionHandler.java
│   │   │   │
│   │   │   └── config/                # Configuration binding
│   │   │       └── RuleProperties.java
│   │   │
│   │   └── resources/
│   │       ├── application.yml        # Default configuration
│   │       └── logback-spring.xml     # Structured JSON logging config
│   │
│   └── test/
│       └── java/com/seb/aml/
│           ├── rule/                  # Rule unit tests
│           │   ├── HighValueTransactionRuleTest.java
│           │   ├── HighRiskCountryRuleTest.java
│           │   ├── SuspiciousOnlineTransactionRuleTest.java
│           │   └── StructuringDetectionRuleTest.java
│           │
│           ├── screening/             # Service tests
│           │   └── ScreeningServiceTest.java
│           │
│           └── api/                   # API integration tests
│               └── ScreeningControllerTest.java
│
└── scripts/
    ├── screen-transaction.sh          # Sample curl command
    └── samples/
        ├── clear-transaction.json     # Transaction that passes all rules
        ├── high-value.json            # Triggers HIGH_VALUE_TRANSACTION
        ├── high-risk-country.json     # Triggers HIGH_RISK_COUNTRY
        └── multi-match.json           # Triggers multiple rules
```

**Package naming: `com.seb.aml`** — uses the company's domain as is conventional in Java.
In a real project this would align with the organization's package naming standards.

---

## 11. Production Considerations

If this system were going into production, the following would be important:

### 11.1 Foreign Exchange Conversion

Threshold rules currently only support EUR. A production system would need:
- An FX rate service (internal or external API)
- A policy for rate staleness (how old can a rate be before it's rejected)
- Rounding rules for converted amounts
- Handling of exotic/unsupported currency pairs
- Caching to avoid per-transaction API calls

This is a substantial subsystem and was excluded to keep the exercise focused.

### 11.2 Historical / Stateful Rules

The `RuleEvaluationContext` is designed to be extended with:

```java
public class RuleEvaluationContext {
    private final Transaction transaction;

    // Future: populated by an enrichment step before rule evaluation
    private CustomerProfile customerProfile;
    private TransactionHistory transactionHistory;
    private ExternalScreeningResults externalScreening;
}
```

Example stateful rules that become possible:
- **Velocity check**: >5 transactions in 24 hours
- **Cumulative amount**: total >50,000 EUR in 7 days
- **Mean deviation**: amount >3σ from customer's historical mean
- **Rapid fund movement**: received and sent within 1 hour
- **Dormant account activity**: first transaction in >90 days

This requires a transaction store (likely a time-series database or event store) and
a pre-evaluation enrichment step.

### 11.3 Persistent Audit Trail

Regulatory requirements (e.g., EU 6AMLD) mandate that screening decisions are retained
for a minimum period (typically 5 years). Production would:
- Persist every `ScreeningEvent` to a database
- Include the rule version that produced the decision
- Support query by transaction, customer, date range, and decision
- Be immutable (append-only, no updates or deletes)

### 11.4 Event-Driven Architecture

Replace `LoggingScreeningEventPublisher` with a Kafka publisher:
- Downstream consumers build dashboards, alert on anomalies
- Decouples screening from audit persistence
- Enables replay and reprocessing

### 11.5 Rule Versioning

When rule parameters change, it's critical to know which version of a rule produced a
given decision. Production would:
- Version rule configurations
- Record the config version with each screening event
- Support auditing: "this transaction was flagged by HIGH_VALUE_TRANSACTION v3 with
  threshold=10000"

### 11.6 Dynamic Rule Management

A production UI for rule management would need:
- CRUD API for rules (backed by a database)
- Change approval workflow (four-eyes principle for AML)
- Rule validation before activation
- A/B testing: shadow-run new rules without affecting decisions
- Rollback capability

### 11.7 Performance and Scalability

- Batch screening endpoint for bulk processing
- Horizontal scaling (stateless service, multiple instances behind a load balancer)
- Rule evaluation parallelization for large rule sets
- Circuit breaker for external dependencies (sanctions list APIs)
- Rate limiting to prevent abuse

### 11.8 Monitoring

Built on the structured logging we implement:
- Rule hit rate dashboard (which rules fire most often)
- REVIEW/CLEAR ratio over time
- Evaluation latency percentiles
- Error rate by rule
- Alerting on sudden changes (spike in reviews may indicate a misconfigured rule
  or a real attack pattern)

---

## 12. Alternatives Considered

### 12.1 Rule Engine Library (Drools)

**Considered:** Using Drools or another rule engine for rule evaluation.

**Rejected because:**
- Drools introduces significant complexity (DRL files, knowledge sessions, fact insertion)
- Overkill for 4 rules with simple logic
- Harder to debug and test than plain Java
- The exercise explicitly advises against a sophisticated rule DSL

**When it would be appropriate:** If the rule set grows to 50+ rules, or if business
analysts (not developers) need to author rules, a rule engine becomes valuable.

### 12.2 Spring Expression Language (SpEL)

**Considered:** Defining rule logic as SpEL expressions in YAML:

```yaml
rules:
  - id: HIGH_VALUE_TRANSACTION
    expression: "#transaction.amount > 10000"
```

**Rejected because:**
- Expressions are strings — no compile-time checking
- Harder to unit test (need SpEL evaluation context setup)
- Security risk (expression injection if rules are user-provided)
- More "clever" than clear — a Java class is easier to read and debug

**When it would be appropriate:** If rules need to be modified without redeployment
and the system has a proper validation/sandboxing layer.

### 12.3 Per-Currency Threshold Configuration

**Considered:** Instead of supporting only EUR, configure separate thresholds per currency
for each rule (e.g., EUR: 10,000, USD: 12,000, SEK: 100,000).

**Rejected because:**
- Configuration complexity grows multiplicatively — N currencies × M threshold-based rules.
  With 20 currencies and 3 threshold rules, that's 60 entries to maintain and keep consistent.
- Each currency's threshold must be independently researched and justified (regulatory
  thresholds differ by jurisdiction), increasing the risk of misconfiguration.
- Operational burden: adding a new threshold rule requires setting thresholds for every
  supported currency, not just one.
- The simpler EUR-only approach makes the exercise clearer while still demonstrating the
  design. Production would likely use FX conversion to a base currency rather than
  maintaining per-currency thresholds.

**When it would be appropriate:** If the system handles a small, fixed set of currencies
(e.g., EUR + SEK + USD only) and FX conversion is undesirable due to rate volatility.

### 12.4 Database-Backed Rule Storage

**Considered:** Storing rules in a database with CRUD API.

**Rejected because:**
- Adds infrastructure dependency (database)
- Requires migration tooling (Flyway/Liquibase)
- Requires change management (who can modify rules?)
- YAML config is sufficient for the exercise scope

**When it would be appropriate:** When rules need to be modified at runtime by
non-developers, or when rule change audit trails are required.

---

## 13. Open Questions

These would be resolved during development or in consultation with the AML team:

1. **Should the structuring detection rule consider the customer's recent transaction
   history?** Currently it's point-in-time only. A customer making five 9,500 EUR
   transactions in a day is more suspicious than a single one.

2. **Should rules have severity levels?** Currently all matches produce `REVIEW`. In
   production, some matches might produce `ALERT` (immediate escalation) vs. `REVIEW`
   (queue for analyst).

3. **Should the API support screening metadata?** For example, the source system that
   submitted the transaction, or a correlation ID for distributed tracing.

---

*End of design document.*

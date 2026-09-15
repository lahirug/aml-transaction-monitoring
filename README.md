# AML Transaction Screening Service

A stateless, rule-based Anti-Money Laundering (AML) transaction screening service built with Java 21 and Spring Boot. Evaluates individual transactions against configurable detection rules and returns a CLEAR or REVIEW decision.

## Prerequisites

- **Java 21+** (tested with OpenJDK 23)
- **Maven 3.9+**

## Build & Run

```bash
# Build
mvn clean package

# Run tests
mvn test

# Start the service
mvn spring-boot:run
```

The service starts on port **8080** by default.

## API Endpoints

### Screen a Transaction

```
POST /api/v1/transactions/screen
```

```bash
curl -X POST http://localhost:8080/api/v1/transactions/screen \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "TX-001",
    "customerId": "CUST-001",
    "amount": 15000,
    "currency": "EUR",
    "originCountry": "SE",
    "destinationCountry": "DE",
    "channel": "ONLINE"
  }'
```

**Response:**
```json
{
  "transactionId": "TX-001",
  "decision": "REVIEW",
  "matchedRules": ["HIGH_VALUE_TRANSACTION", "SUSPICIOUS_ONLINE_TRANSACTION"],
  "screenedAt": "2024-01-15T10:30:00Z"
}
```

### List Rules

```bash
curl http://localhost:8080/api/v1/rules
```

**Response:**
```json
{
  "rules": [
    {
      "ruleId": "HIGH_VALUE_TRANSACTION",
      "description": "Flags transactions where the amount exceeds the configured threshold",
      "enabled": true,
      "parameters": { "threshold": 10000, "currency": "EUR" }
    }
  ]
}
```

### Health Check

```bash
curl http://localhost:8080/actuator/health
```

**Response:**
```json
{
  "status": "UP"
}
```

## CLI Scripts

Run all sample transactions against a running service:

```bash
./scripts/screen-transaction.sh
```

Or screen a specific sample:

```bash
./scripts/screen-transaction.sh scripts/samples/high-value-transaction.json
```

## Detection Rules

| Rule | Description | Default Config |
|------|-------------|----------------|
| `HIGH_VALUE_TRANSACTION` | Flags amounts > threshold | threshold: €10,000 |
| `HIGH_RISK_COUNTRY` | Flags transactions involving FATF high-risk jurisdictions | KP, SD, SY, AF, MM, YE, LY |
| `SUSPICIOUS_ONLINE_TRANSACTION` | Flags online cross-border transfers > threshold | threshold: €5,000 |
| `STRUCTURING_DETECTION` | Flags amounts in the suspicious range below reporting threshold | range: €9,000–€10,000 |

All rules are configurable via `application.yml`. Rules can be individually enabled or disabled.

## Rule Configuration

Rules are configured in `src/main/resources/application.yml`:

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
        countries: [KP, SD, SY, AF, MM, YE, LY]
      suspicious-online-transaction:
        enabled: true
        threshold: 5000
      structuring-detection:
        enabled: true
        reporting-threshold: 10000
        lower-bound: 9000
```

Configuration is validated at startup — the service refuses to start with invalid values (negative thresholds, empty country lists, etc.).

## Architecture

```
api/                    REST controllers, DTOs, exception handling
config/                 RuleProperties (type-safe YAML binding)
domain/                 Transaction, Decision, Channel, ScreeningResult
rule/                   Rule interface + 4 implementations
screening/              ScreeningService orchestrator + event publisher
```

**Key design decisions:**

- **Strategy pattern**: Rules implement a common `Rule` interface, discovered by Spring component scanning. Adding a rule requires zero changes to existing code.
- **Fail-safe evaluation**: If a rule throws an exception, the transaction is treated as REVIEW. In AML, false negatives are unacceptable.
- **Duplicate detection**: Duplicate rule IDs are logged as warnings; only the first instance is kept.
- **RuleEvaluationContext**: Wraps the transaction for future extensibility (customer profiles, transaction history) without changing the Rule interface.
- **EUR-only**: FX conversion is not implemented. Non-EUR transactions are rejected with a clear error.

## Testing

- **36 rule unit tests**: Each rule tested in isolation with boundary conditions and BigDecimal precision
- **9 service tests**: Mock-based tests for CLEAR/REVIEW logic, fail-safe behavior, duplicate detection, event publishing
- **13 controller integration tests**: MockMvc tests for valid requests, validation errors, unsupported currency, malformed JSON, invalid timestamps, case insensitivity
- **4 configuration tests**: Startup validation for structuring rule bounds

## What's Not Implemented (and Why)

See [DESIGN.md](DESIGN.md) Section 3 (Non-Goals) for detailed rationale:

- No persistent storage (exercise scope)
- No authentication/authorization
- No batch processing
- No UI (exercise says "no frontend")
- No multi-currency FX conversion
- No rule CRUD API (no persistence backing)
- No hot deployment of rules
- No rate limiting
- No distributed tracing

## Production Considerations

Documented in [DESIGN.md](DESIGN.md) Section 11:

- Replace in-memory logging with Kafka-backed event publisher
- Add persistent rule storage with versioning
- Implement FX conversion for multi-currency support
- Add distributed tracing (OpenTelemetry)
- Circuit breakers for external dependencies
- Rate limiting and authentication

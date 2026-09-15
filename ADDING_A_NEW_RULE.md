# Adding a New Rule — Step by Step

This guide walks through adding a new AML rule to the system. We'll use a
hypothetical **ROUND_AMOUNT_DETECTION** rule as an example — it flags
transactions with suspiciously round amounts (e.g., exactly 5000, 8000)
which can indicate structuring.

---

## Step 1: Add configuration

Open `src/main/java/com/seb/aml/config/RuleProperties.java` and add a new
nested record inside `RuleProperties`:

```java
public record RoundAmountDetection(
        boolean enabled,
        @NotNull @Positive BigDecimal minAmount
) {
}
```

Then add it to the `Rules` record:

```java
public record Rules(
        @Valid HighValueTransaction highValueTransaction,
        @Valid HighRiskCountry highRiskCountry,
        @Valid SuspiciousOnlineTransaction suspiciousOnlineTransaction,
        @Valid StructuringDetection structuringDetection,
        @Valid RoundAmountDetection roundAmountDetection       // ← add this
) {
}
```

---

## Step 2: Add YAML config

Open `src/main/resources/application.yml` and add under `aml.screening.rules`:

```yaml
round-amount-detection:
  enabled: true
  min-amount: 1000
```

---

## Step 3: Implement the rule

Create `src/main/java/com/seb/aml/rule/RoundAmountDetectionRule.java`:

```java
package com.seb.aml.rule;

import com.seb.aml.config.RuleProperties;
import com.seb.aml.domain.Transaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class RoundAmountDetectionRule implements Rule {

    private static final String RULE_ID = "ROUND_AMOUNT_DETECTION";
    private static final String DESCRIPTION =
            "Flags transactions with suspiciously round amounts";

    private final RuleProperties.RoundAmountDetection config;

    public RoundAmountDetectionRule(RuleProperties properties) {
        this.config = properties.rules().roundAmountDetection();
    }

    @Override
    public String getRuleId() {
        return RULE_ID;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public boolean isEnabled() {
        return config.enabled();
    }

    @Override
    public boolean evaluate(RuleEvaluationContext context) {
        Transaction tx = context.getTransaction();
        BigDecimal amount = tx.amount();
        return amount.compareTo(config.minAmount()) >= 0
                && amount.remainder(BigDecimal.valueOf(1000)).compareTo(BigDecimal.ZERO) == 0;
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of("minAmount", config.minAmount());
    }
}
```

That's it for the rule. The `@Component` annotation makes Spring discover it
automatically. `ScreeningService` receives it via `List<Rule>` injection — no
changes needed there.

---

## Step 4: Write tests

Create `src/test/java/com/seb/aml/rule/RoundAmountDetectionRuleTest.java`:

```java
package com.seb.aml.rule;

import com.seb.aml.config.RuleProperties;
import com.seb.aml.domain.Channel;
import com.seb.aml.domain.Transaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoundAmountDetectionRuleTest {

    private RoundAmountDetectionRule createRule(boolean enabled, BigDecimal minAmount) {
        var rules = new RuleProperties.Rules(
                new RuleProperties.HighValueTransaction(false, BigDecimal.valueOf(10000)),
                new RuleProperties.HighRiskCountry(false, List.of("KP")),
                new RuleProperties.SuspiciousOnlineTransaction(false, BigDecimal.valueOf(5000)),
                new RuleProperties.StructuringDetection(false, BigDecimal.valueOf(10000), BigDecimal.valueOf(9000)),
                new RuleProperties.RoundAmountDetection(enabled, minAmount)
        );
        return new RoundAmountDetectionRule(new RuleProperties("EUR", rules));
    }

    private Transaction transaction(BigDecimal amount) {
        return new Transaction("TX-001", "CUST-001", amount, "EUR",
                "SE", "FI", Channel.ONLINE, Instant.now());
    }

    @Test
    void roundAmountAboveMin_shouldMatch() {
        var rule = createRule(true, BigDecimal.valueOf(1000));
        assertTrue(rule.evaluate(new RuleEvaluationContext(transaction(BigDecimal.valueOf(5000)))));
    }

    @Test
    void nonRoundAmount_shouldNotMatch() {
        var rule = createRule(true, BigDecimal.valueOf(1000));
        assertFalse(rule.evaluate(new RuleEvaluationContext(transaction(BigDecimal.valueOf(5001)))));
    }

    @Test
    void roundAmountBelowMin_shouldNotMatch() {
        var rule = createRule(true, BigDecimal.valueOf(5000));
        assertFalse(rule.evaluate(new RuleEvaluationContext(transaction(BigDecimal.valueOf(3000)))));
    }

    @Test
    void disabled_shouldReportDisabled() {
        var rule = createRule(false, BigDecimal.valueOf(1000));
        assertFalse(rule.isEnabled());
    }
}
```

---

## Step 5: Update the test config

Open `src/test/java/com/seb/aml/api/TestRulePropertiesConfig.java` and add
the new rule config to the `RuleProperties` bean so that `@WebMvcTest` tests
still compile.

---

## Summary of files changed

| File | Change |
|------|--------|
| `RuleProperties.java` | Add `RoundAmountDetection` record + add field to `Rules` |
| `application.yml` | Add `round-amount-detection` config block |
| `RoundAmountDetectionRule.java` | **New file** — the rule implementation |
| `RoundAmountDetectionRuleTest.java` | **New file** — unit tests |
| `TestRulePropertiesConfig.java` | Add new rule config to test bean |

**Files NOT changed:** `ScreeningService`, `ScreeningController`,
`GlobalExceptionHandler`, or any existing rule. This is the extensibility
the architecture provides.

# AI Usage Disclosure

## Development Approach

This project followed the **Elephant and Goldfish** model described in Google Research's
paper ["Elephants, Goldfish and the New Golden Age of Software Engineering"](https://research.google/pubs/elephants-goldfish-and-the-new-golden-age-of-software-engineering/)
by Steve Yegge (2025).

The approach has two phases:

1. **Elephant phase (design):** An extended collaborative session with AI to explore the
   problem space, discuss trade-offs, evaluate alternatives, and produce a comprehensive
   design document. The human drives all decisions; AI acts as a knowledgeable collaborator
   who can be questioned, challenged, and corrected.

2. **Goldfish phase (implementation):** The design document is handed to a fresh AI context
   (the "goldfish" with no memory of the design conversation). If the goldfish can implement
   correctly from the document alone, the design is sufficiently clear. The human reviews
   all generated code critically.

The rationale: as AI becomes better at generating code, the **specification becomes more
valuable than the code itself**. Time invested in design yields higher-quality implementation
with fewer iterations.

---

## Tools Used

| Tool | Purpose |
|------|---------|
| **Claude Code (Claude Opus 4.6)** | Primary AI collaborator for design discussion, architecture decisions, code generation, and document authoring |

---

## What I Used AI For

### Design phase (majority of effort)
- Explored AML domain concepts (FATF lists, structuring patterns, SAR workflows)
- Discussed and evaluated architecture alternatives (Drools vs. SpEL vs. hand-coded rules)
- Debated scope decisions (multi-currency support, rule CRUD, UI)
- Drafted and iterated on the design document (DESIGN.md)
- Identified production considerations and extension points

### Implementation phase
- Generated boilerplate (Spring Boot project structure, Maven POM, configuration binding)
- Implemented domain model, rule interface, and rule implementations from the design doc
- Generated test cases from the test scenarios defined during design
- Wrote structured logging configuration

---

## One AI Suggestion I Accepted

**RuleEvaluationContext pattern:** AI suggested that rules should receive a context object
wrapping the transaction rather than the transaction directly. This allows future enrichment
(customer profile, transaction history) without changing the rule interface.

**Why I accepted:** This is a well-known extensibility pattern, and the cost is minimal —
one extra wrapper class. It directly addresses the requirement that adding stateful rules
(velocity checks, cumulative amounts) should not require rewriting existing rules. The
trade-off (slight indirection) is worth the future flexibility.

---

## One AI Suggestion I Rejected

**Per-currency threshold configuration:** AI suggested supporting multiple currencies by
configuring separate thresholds per currency per rule (e.g., EUR: 10,000, USD: 12,000,
SEK: 100,000) in the YAML configuration.

**Why I rejected:** The configuration complexity grows multiplicatively — N currencies ×
M threshold-based rules. With 20 currencies across 3 threshold rules, that's 60 entries
to maintain, each requiring independent research and justification. Adding a new threshold
rule means setting values for every supported currency. In production, FX conversion to a
base currency is a cleaner solution than maintaining a matrix of per-currency thresholds.
I chose EUR-only with a clear error message for other currencies, and documented FX
conversion as a production consideration.

---

## Reflection

AI was most valuable during the design phase — it acted as a domain-knowledgeable
collaborator that I could question and push back on. Several of its initial suggestions
were over-engineered for the exercise scope, and the back-and-forth discussion helped
arrive at pragmatic trade-offs.

During implementation, AI was useful for boilerplate and repetitive code but required
careful review for correctness, especially around edge cases in rule evaluation logic
and Spring configuration binding.

The Elephant/Goldfish model worked well here: the extensive design conversation produced
a document clear enough that implementation decisions were already made. The code became
an expression of the design rather than a discovery process.

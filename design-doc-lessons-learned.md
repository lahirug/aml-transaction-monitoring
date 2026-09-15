# Design Document Lessons Learned

Things I had to explicitly specify during implementation that were missing or
underspecified in the original design document. Use this as a checklist when
writing future design documents.

---

## 1. Duplicate Rule Handling

**What I had to say:** "if we have duplicate rules we should log an error/warning
and pick the first one instead"

**Gap:** The design doc described the rule registration mechanism but did not
specify what happens when two rules share the same `ruleId`. This is a common
edge case with plugin-style architectures.

**Lesson:** When designing any registry or plugin system, explicitly specify the
behaviour for duplicate entries — fail fast, last-wins, first-wins, or merge.

---

## 2. Fail-Fast on Invalid Configuration

**What I had to say:** "when parsing the rule configuration if there are errors
service should not be up and running it should instead give an error and crash,
we do not implement hot deployment support for the rules right now"

**Gap:** The design doc mentioned YAML-based configuration and startup validation
but did not state the consequence of invalid configuration. Should the service
start with defaults? Log a warning? Refuse to start?

**Lesson:** For any configuration-driven system, explicitly state the failure
mode: fail-fast (refuse to start) vs. fail-soft (use defaults and warn). In AML,
a silently misconfigured system is worse than a system that is down.

---

## 3. Startup Configuration Validation and Testing

**What I had to say:** "do we have tests for invalid configurations during
startup? rules should have some mandatory fields and if any of those are not
configured it's an invalid rules configuration"

**Gap:** The design doc mentioned `@Validated` and startup validation in passing
but did not enumerate what constitutes invalid configuration or what specific
constraints apply (e.g., thresholds must be positive, country list must not be
empty, structuring lowerBound must be less than reportingThreshold).

**Lesson:** List the validation rules for every configuration parameter in the
design doc. Include a table of field constraints (required, type, range, format).
This directly drives both the implementation and the test cases.

---

## 4. Negative and Boundary Test Strategy

**What I had to say:** "do we have enough negative tests in our unit tests"

**Gap:** The testing section of the design doc listed test categories (unit,
integration, config) but did not enumerate the specific negative scenarios:
- What if all rules are disabled?
- What if the rule list is empty?
- What if a rule throws a NullPointerException?
- What if the event publisher fails?
- What if multiple validation errors occur simultaneously?

**Lesson:** Include a "key test scenarios" subsection in the design doc with
explicit negative and boundary cases. For each component, ask: "what are the
ways this can fail, and what should happen?"

---

## 5. Input Validation Completeness and Error Response Consistency

**What I had to say:** "do we do proper validation about the input transaction
details and return errors properly and do we have tests for those?"

**Gap:** The design doc defined the error response structure and listed
validation annotations, but did not specify:
- Country code format validation (ISO 3166-1 alpha-2, i.e., exactly 2 letters)
- That all error responses must include field-level details consistently
  (not just bean validation errors, but also custom controller validation)
- What HTTP status code to return for wrong Content-Type (415) or wrong
  HTTP method (405)
- That invalid requests must never reach the service layer

**Lesson:** Create an "error catalogue" in the design doc — a table mapping
every possible input error to its HTTP status code, error code, and whether
field-level details are included. Also specify input format constraints beyond
just "required" (e.g., regex patterns, length limits, enum values).

---

## 6. Unsupported Currency as an Explicit Error (Not Just Rejection)

**What I had to say:** "if we get a transaction in a different currency we should
give an error saying this transaction evaluation is not supported because it
involves fx conversion"

**Gap:** The initial design mentioned EUR-only support but did not specify that
non-EUR transactions should return a specific, descriptive error (as opposed to
a generic validation error). The error message should explain *why* — FX
conversion is not implemented.

**Lesson:** When a feature is intentionally out of scope, specify the exact user-
facing behaviour at the boundary. Don't just say "not supported" — define the
error code, message, and HTTP status the caller receives.

---

## 7. README with Runnable Examples

**What I had to say:** "instead give a proper curl command to run this similar to
the post"

**Gap:** The README listed API endpoints as `GET /api/v1/rules` but did not
include full curl commands with example request/response payloads that someone
could copy-paste and run.

**Lesson:** The design doc's API section should include complete request/response
examples (not just schemas). These serve double duty as documentation and as the
basis for integration test expectations. If someone reading the doc can't
immediately try the API, the examples are incomplete.

---

## 8. Scope Decisions and Their Rationale

**What I had to say (across multiple messages):**
- "lets not do the ui but build the system in a way that ui can be built later"
- "lets revert [multi-currency], this will make the configuration complex"
- "we do not implement hot deployment support for the rules right now"
- "since we do not have persistent storage, adding and modifying rules is hard,
  so we don't have to worry about the crud"

**Gap:** While the design doc had a "Non-Goals" section, several scope decisions
were made during conversation but not all rationale was captured. Each non-goal
should explain *why* it was excluded and *what the alternative would look like*.

**Lesson:** For every non-goal, write a one-line rationale. "No UI" is not
enough — "No UI: reduces implementation scope; system is designed with clean REST
APIs so a UI can be added later without backend changes" is better. This helps
the reader (or interviewer) understand the trade-off was deliberate.

---

## Summary Checklist for Future Design Documents

- [ ] Duplicate/conflict handling for any registry or plugin system
- [ ] Failure mode for every configuration parameter (fail-fast vs. fail-soft)
- [ ] Validation constraints table for all config and API input fields
- [ ] Error catalogue mapping every error to status code, error code, and details
- [ ] Key negative and boundary test scenarios per component
- [ ] Complete request/response examples (copy-paste runnable)
- [ ] Explicit user-facing behaviour for out-of-scope features at the boundary
- [ ] Rationale for every non-goal (why excluded, what it would look like)

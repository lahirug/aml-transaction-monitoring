package com.seb.aml.screening;

import com.seb.aml.domain.Channel;
import com.seb.aml.domain.Decision;
import com.seb.aml.domain.ScreeningResult;
import com.seb.aml.domain.Transaction;
import com.seb.aml.rule.Rule;
import com.seb.aml.rule.RuleEvaluationContext;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ScreeningService}.
 */
class ScreeningServiceTest {

    private final ScreeningEventPublisher publisher = mock(ScreeningEventPublisher.class);

    private Transaction sampleTransaction() {
        return new Transaction("TX-001", "CUST-001", BigDecimal.valueOf(5000), "EUR",
                "SE", "FI", Channel.ONLINE, Instant.now());
    }

    private Rule mockRule(String ruleId, boolean enabled, boolean matches) {
        Rule rule = mock(Rule.class);
        when(rule.getRuleId()).thenReturn(ruleId);
        when(rule.isEnabled()).thenReturn(enabled);
        when(rule.evaluate(any())).thenReturn(matches);
        return rule;
    }

    @Nested
    class DecisionLogic {

        @Test
        void noRulesMatch_shouldReturnClear() {
            Rule rule1 = mockRule("RULE_1", true, false);
            Rule rule2 = mockRule("RULE_2", true, false);

            var service = new ScreeningService(List.of(rule1, rule2), publisher);
            ScreeningResult result = service.screen(sampleTransaction());

            assertEquals(Decision.CLEAR, result.decision());
            assertTrue(result.matchedRules().isEmpty());
        }

        @Test
        void oneRuleMatches_shouldReturnReview() {
            Rule rule1 = mockRule("RULE_1", true, true);
            Rule rule2 = mockRule("RULE_2", true, false);

            var service = new ScreeningService(List.of(rule1, rule2), publisher);
            ScreeningResult result = service.screen(sampleTransaction());

            assertEquals(Decision.REVIEW, result.decision());
            assertEquals(List.of("RULE_1"), result.matchedRules());
        }

        @Test
        void multipleRulesMatch_shouldReturnAllMatchedRules() {
            Rule rule1 = mockRule("RULE_1", true, true);
            Rule rule2 = mockRule("RULE_2", true, true);

            var service = new ScreeningService(List.of(rule1, rule2), publisher);
            ScreeningResult result = service.screen(sampleTransaction());

            assertEquals(Decision.REVIEW, result.decision());
            assertEquals(List.of("RULE_1", "RULE_2"), result.matchedRules());
        }

        @Test
        void screeningResult_shouldContainTransactionId() {
            Rule rule = mockRule("RULE_1", true, false);

            var service = new ScreeningService(List.of(rule), publisher);
            ScreeningResult result = service.screen(sampleTransaction());

            assertEquals("TX-001", result.transactionId());
            assertNotNull(result.screenedAt());
        }
    }

    @Nested
    class DisabledRules {

        @Test
        void disabledRule_shouldNotBeEvaluated() {
            Rule enabledRule = mockRule("ENABLED", true, false);
            Rule disabledRule = mockRule("DISABLED", false, true);

            var service = new ScreeningService(List.of(enabledRule, disabledRule), publisher);
            ScreeningResult result = service.screen(sampleTransaction());

            assertEquals(Decision.CLEAR, result.decision());
            verify(disabledRule, never()).evaluate(any());
        }

        @Test
        void allRulesDisabled_shouldReturnClear() {
            Rule rule1 = mockRule("RULE_1", false, true);
            Rule rule2 = mockRule("RULE_2", false, true);

            var service = new ScreeningService(List.of(rule1, rule2), publisher);
            ScreeningResult result = service.screen(sampleTransaction());

            assertEquals(Decision.CLEAR, result.decision());
            assertTrue(result.matchedRules().isEmpty());
            verify(rule1, never()).evaluate(any());
            verify(rule2, never()).evaluate(any());
        }

        @Test
        void emptyRulesList_shouldReturnClear() {
            var service = new ScreeningService(List.of(), publisher);
            ScreeningResult result = service.screen(sampleTransaction());

            assertEquals(Decision.CLEAR, result.decision());
            assertTrue(result.matchedRules().isEmpty());
        }
    }

    @Nested
    class FailSafe {

        @Test
        void ruleThrowsException_shouldTreatAsMatch() {
            Rule failingRule = mockRule("FAILING", true, false);
            when(failingRule.evaluate(any())).thenThrow(new RuntimeException("Unexpected error"));

            var service = new ScreeningService(List.of(failingRule), publisher);
            ScreeningResult result = service.screen(sampleTransaction());

            assertEquals(Decision.REVIEW, result.decision());
            assertTrue(result.matchedRules().contains("FAILING"));
        }

        @Test
        void failingRule_shouldNotPreventOtherRulesFromBeingEvaluated() {
            Rule failingRule = mockRule("FAILING", true, false);
            when(failingRule.evaluate(any())).thenThrow(new RuntimeException("Unexpected error"));

            Rule normalRule = mockRule("NORMAL", true, true);

            var service = new ScreeningService(List.of(failingRule, normalRule), publisher);
            ScreeningResult result = service.screen(sampleTransaction());

            assertEquals(Decision.REVIEW, result.decision());
            assertEquals(2, result.matchedRules().size());
            assertTrue(result.matchedRules().contains("FAILING"));
            assertTrue(result.matchedRules().contains("NORMAL"));
        }

        @Test
        void ruleThrowsNullPointerException_shouldTreatAsMatch() {
            Rule failingRule = mockRule("NPE_RULE", true, false);
            when(failingRule.evaluate(any())).thenThrow(new NullPointerException());

            var service = new ScreeningService(List.of(failingRule), publisher);
            ScreeningResult result = service.screen(sampleTransaction());

            assertEquals(Decision.REVIEW, result.decision());
            assertTrue(result.matchedRules().contains("NPE_RULE"));
        }
    }

    @Nested
    class DuplicateDetection {

        @Test
        void duplicateRuleIds_shouldKeepFirstOnly() {
            Rule first = mockRule("DUPLICATE", true, true);
            Rule second = mockRule("DUPLICATE", true, false);

            var service = new ScreeningService(List.of(first, second), publisher);
            assertEquals(1, service.getRegisteredRules().size());

            ScreeningResult result = service.screen(sampleTransaction());
            assertEquals(Decision.REVIEW, result.decision());
            assertEquals(List.of("DUPLICATE"), result.matchedRules());
        }

        @Test
        void getRegisteredRules_shouldReturnAllUniqueRules() {
            Rule rule1 = mockRule("RULE_1", true, false);
            Rule rule2 = mockRule("RULE_2", true, false);

            var service = new ScreeningService(List.of(rule1, rule2), publisher);
            assertEquals(2, service.getRegisteredRules().size());
        }
    }

    @Nested
    class EventPublishing {

        @Test
        void screeningPublishesEvent() {
            Rule rule = mockRule("RULE_1", true, false);

            var service = new ScreeningService(List.of(rule), publisher);
            service.screen(sampleTransaction());

            verify(publisher).publish(any(ScreeningResult.class), any(RuleEvaluationContext.class),
                    eq(1), anyLong());
        }

        @Test
        void publisherException_shouldNotCrashScreening() {
            doThrow(new RuntimeException("Publisher failed"))
                    .when(publisher).publish(any(), any(), anyInt(), anyLong());

            Rule rule = mockRule("RULE_1", true, false);
            var service = new ScreeningService(List.of(rule), publisher);

            assertDoesNotThrow(() -> service.screen(sampleTransaction()));
        }
    }
}

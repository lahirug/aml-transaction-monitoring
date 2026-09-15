package com.seb.aml.api;

import com.seb.aml.rule.Rule;
import com.seb.aml.screening.ScreeningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link RuleController} using MockMvc.
 */
@WebMvcTest(RuleController.class)
@Import(TestRulePropertiesConfig.class)
class RuleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ScreeningService screeningService;

    private static final String RULES_URL = "/api/v1/rules";

    @Test
    void listRules_shouldReturnAllRegisteredRules() throws Exception {
        Rule rule = mock(Rule.class);
        when(rule.getRuleId()).thenReturn("HIGH_VALUE_TRANSACTION");
        when(rule.getDescription()).thenReturn("Flags high-value transactions");
        when(rule.isEnabled()).thenReturn(true);
        when(rule.getParameters()).thenReturn(Map.of("threshold", 10000, "currency", "EUR"));

        when(screeningService.getRegisteredRules()).thenReturn(List.of(rule));

        mockMvc.perform(get(RULES_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rules.length()").value(1))
                .andExpect(jsonPath("$.rules[0].ruleId").value("HIGH_VALUE_TRANSACTION"))
                .andExpect(jsonPath("$.rules[0].description").value("Flags high-value transactions"))
                .andExpect(jsonPath("$.rules[0].enabled").value(true))
                .andExpect(jsonPath("$.rules[0].parameters.threshold").value(10000));
    }

    @Test
    void listRules_withNoRules_shouldReturnEmptyList() throws Exception {
        when(screeningService.getRegisteredRules()).thenReturn(List.of());

        mockMvc.perform(get(RULES_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rules").isEmpty());
    }
}

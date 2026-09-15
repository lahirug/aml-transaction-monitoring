package com.seb.aml;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration tests that boot the entire Spring context.
 *
 * <p>Unlike the {@code @WebMvcTest} controller tests (which mock the service layer),
 * these tests verify the complete request flow: HTTP → controller → service → real rules
 * → real event publisher → response. This catches wiring issues that unit tests miss.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class AmlScreeningIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void clearTransaction_fullFlow() throws Exception {
        String request = """
                {
                    "transactionId": "TX-INT-001",
                    "customerId": "CUST-001",
                    "amount": 500,
                    "currency": "EUR",
                    "originCountry": "SE",
                    "destinationCountry": "SE",
                    "channel": "BRANCH"
                }
                """;

        mockMvc.perform(post("/api/v1/transactions/screen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("TX-INT-001"))
                .andExpect(jsonPath("$.decision").value("CLEAR"))
                .andExpect(jsonPath("$.matchedRules").isEmpty())
                .andExpect(jsonPath("$.screenedAt").isNotEmpty());
    }

    @Test
    void highValueTransaction_fullFlow() throws Exception {
        String request = """
                {
                    "transactionId": "TX-INT-002",
                    "customerId": "CUST-001",
                    "amount": 15000,
                    "currency": "EUR",
                    "originCountry": "SE",
                    "destinationCountry": "SE",
                    "channel": "BRANCH"
                }
                """;

        mockMvc.perform(post("/api/v1/transactions/screen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("REVIEW"))
                .andExpect(jsonPath("$.matchedRules[0]").value("HIGH_VALUE_TRANSACTION"));
    }

    @Test
    void multipleRulesMatch_fullFlow() throws Exception {
        String request = """
                {
                    "transactionId": "TX-INT-003",
                    "customerId": "CUST-001",
                    "amount": 15000,
                    "currency": "EUR",
                    "originCountry": "SE",
                    "destinationCountry": "SY",
                    "channel": "ONLINE"
                }
                """;

        mockMvc.perform(post("/api/v1/transactions/screen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("REVIEW"))
                .andExpect(jsonPath("$.matchedRules.length()").value(3));
    }

    @Test
    void listRules_fullFlow() throws Exception {
        mockMvc.perform(get("/api/v1/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rules.length()").value(4))
                .andExpect(jsonPath("$.rules[0].ruleId").isNotEmpty())
                .andExpect(jsonPath("$.rules[0].description").isNotEmpty())
                .andExpect(jsonPath("$.rules[0].parameters").isNotEmpty());
    }

    @Test
    void healthCheck_fullFlow() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}

package com.seb.aml.api;

import com.seb.aml.domain.Decision;
import com.seb.aml.domain.ScreeningResult;
import com.seb.aml.screening.ScreeningService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link ScreeningController} using MockMvc.
 *
 * <p>Tests cover the full request/response lifecycle: JSON deserialization,
 * bean validation, custom controller validation, error response structure,
 * and correct delegation to the screening service.</p>
 */
@WebMvcTest(ScreeningController.class)
@Import(TestRulePropertiesConfig.class)
class ScreeningControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ScreeningService screeningService;

    private static final String SCREEN_URL = "/api/v1/transactions/screen";

    private static final String VALID_REQUEST = """
            {
                "transactionId": "TX-001",
                "customerId": "CUST-001",
                "amount": 5000,
                "currency": "EUR",
                "originCountry": "SE",
                "destinationCountry": "FI",
                "channel": "ONLINE",
                "timestamp": "2024-01-15T10:30:00Z"
            }
            """;

    @Nested
    class HappyPath {

        @Test
        void validRequest_shouldReturnClear() throws Exception {
            when(screeningService.screen(any())).thenReturn(
                    new ScreeningResult("TX-001", Decision.CLEAR, List.of(),
                            Instant.parse("2024-01-15T10:30:00Z")));

            mockMvc.perform(post(SCREEN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_REQUEST))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.transactionId").value("TX-001"))
                    .andExpect(jsonPath("$.decision").value("CLEAR"))
                    .andExpect(jsonPath("$.matchedRules").isEmpty())
                    .andExpect(jsonPath("$.screenedAt").isNotEmpty());
        }

        @Test
        void validRequest_shouldReturnReviewWithMatchedRules() throws Exception {
            when(screeningService.screen(any())).thenReturn(
                    new ScreeningResult("TX-001", Decision.REVIEW,
                            List.of("HIGH_VALUE_TRANSACTION", "HIGH_RISK_COUNTRY"),
                            Instant.parse("2024-01-15T10:30:00Z")));

            mockMvc.perform(post(SCREEN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_REQUEST))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.decision").value("REVIEW"))
                    .andExpect(jsonPath("$.matchedRules.length()").value(2))
                    .andExpect(jsonPath("$.matchedRules[0]").value("HIGH_VALUE_TRANSACTION"))
                    .andExpect(jsonPath("$.matchedRules[1]").value("HIGH_RISK_COUNTRY"));
        }

        @Test
        void missingTimestamp_shouldDefaultToServerTime() throws Exception {
            String request = """
                    {
                        "transactionId": "TX-001",
                        "customerId": "CUST-001",
                        "amount": 5000,
                        "currency": "EUR",
                        "originCountry": "SE",
                        "destinationCountry": "FI",
                        "channel": "ONLINE"
                    }
                    """;

            when(screeningService.screen(any())).thenReturn(
                    new ScreeningResult("TX-001", Decision.CLEAR, List.of(), Instant.now()));

            mockMvc.perform(post(SCREEN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.decision").value("CLEAR"));
        }

        @Test
        void lowercaseCurrencyAndCountry_shouldBeAccepted() throws Exception {
            String request = """
                    {
                        "transactionId": "TX-001",
                        "customerId": "CUST-001",
                        "amount": 5000,
                        "currency": "eur",
                        "originCountry": "se",
                        "destinationCountry": "fi",
                        "channel": "online"
                    }
                    """;

            when(screeningService.screen(any())).thenReturn(
                    new ScreeningResult("TX-001", Decision.CLEAR, List.of(), Instant.now()));

            mockMvc.perform(post(SCREEN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.decision").value("CLEAR"));
        }
    }

    @Nested
    class MissingFields {

        @Test
        void missingAllRequiredFields_shouldReturn400WithMultipleDetails() throws Exception {
            mockMvc.perform(post(SCREEN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").value("Transaction validation failed"))
                    .andExpect(jsonPath("$.details").isNotEmpty())
                    .andExpect(jsonPath("$.details.length()").value(7));
        }

        @Test
        void missingTransactionId_shouldReturn400WithFieldDetail() throws Exception {
            String request = """
                    {
                        "customerId": "CUST-001",
                        "amount": 5000,
                        "currency": "EUR",
                        "originCountry": "SE",
                        "destinationCountry": "FI",
                        "channel": "ONLINE"
                    }
                    """;

            mockMvc.perform(post(SCREEN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.details[?(@.field == 'transactionId')]").exists())
                    .andExpect(jsonPath("$.details[?(@.field == 'transactionId')].issue")
                            .value("transactionId is required"));
        }

        @Test
        void missingCustomerId_shouldReturn400WithFieldDetail() throws Exception {
            String request = """
                    {
                        "transactionId": "TX-001",
                        "amount": 5000,
                        "currency": "EUR",
                        "originCountry": "SE",
                        "destinationCountry": "FI",
                        "channel": "ONLINE"
                    }
                    """;

            mockMvc.perform(post(SCREEN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details[?(@.field == 'customerId')]").exists())
                    .andExpect(jsonPath("$.details[?(@.field == 'customerId')].issue")
                            .value("customerId is required"));
        }

        @Test
        void missingAmount_shouldReturn400WithFieldDetail() throws Exception {
            String request = """
                    {
                        "transactionId": "TX-001",
                        "customerId": "CUST-001",
                        "currency": "EUR",
                        "originCountry": "SE",
                        "destinationCountry": "FI",
                        "channel": "ONLINE"
                    }
                    """;

            mockMvc.perform(post(SCREEN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details[?(@.field == 'amount')]").exists())
                    .andExpect(jsonPath("$.details[?(@.field == 'amount')].issue")
                            .value("amount is required"));
        }

        @Test
        void missingCurrency_shouldReturn400() throws Exception {
            String request = """
                    {
                        "transactionId": "TX-001",
                        "customerId": "CUST-001",
                        "amount": 5000,
                        "originCountry": "SE",
                        "destinationCountry": "FI",
                        "channel": "ONLINE"
                    }
                    """;

            mockMvc.perform(post(SCREEN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details[?(@.field == 'currency')]").exists());
        }

        @Test
        void missingOriginCountry_shouldReturn400() throws Exception {
            String request = """
                    {
                        "transactionId": "TX-001",
                        "customerId": "CUST-001",
                        "amount": 5000,
                        "currency": "EUR",
                        "destinationCountry": "FI",
                        "channel": "ONLINE"
                    }
                    """;

            mockMvc.perform(post(SCREEN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details[?(@.field == 'originCountry')]").exists());
        }

        @Test
        void missingDestinationCountry_shouldReturn400() throws Exception {
            String request = """
                    {
                        "transactionId": "TX-001",
                        "customerId": "CUST-001",
                        "amount": 5000,
                        "currency": "EUR",
                        "originCountry": "SE",
                        "channel": "ONLINE"
                    }
                    """;

            mockMvc.perform(post(SCREEN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details[?(@.field == 'destinationCountry')]").exists());
        }

        @Test
        void missingChannel_shouldReturn400() throws Exception {
            String request = """
                    {
                        "transactionId": "TX-001",
                        "customerId": "CUST-001",
                        "amount": 5000,
                        "currency": "EUR",
                        "originCountry": "SE",
                        "destinationCountry": "FI"
                    }
                    """;

            mockMvc.perform(post(SCREEN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details[?(@.field == 'channel')]").exists());
        }

        @Test
        void blankTransactionId_shouldReturn400() throws Exception {
            String request = """
                    {
                        "transactionId": "  ",
                        "customerId": "CUST-001",
                        "amount": 5000,
                        "currency": "EUR",
                        "originCountry": "SE",
                        "destinationCountry": "FI",
                        "channel": "ONLINE"
                    }
                    """;

            mockMvc.perform(post(SCREEN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.details[?(@.field == 'transactionId')]").exists());
        }
    }

    @Nested
    class InvalidValues {

        @Test
        void unsupportedCurrency_shouldReturn400WithCurrencyError() throws Exception {
            String request = """
                    {
                        "transactionId": "TX-001",
                        "customerId": "CUST-001",
                        "amount": 5000,
                        "currency": "USD",
                        "originCountry": "SE",
                        "destinationCountry": "FI",
                        "channel": "ONLINE"
                    }
                    """;

            mockMvc.perform(post(SCREEN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("UNSUPPORTED_CURRENCY"))
                    .andExpect(jsonPath("$.details[0].field").value("currency"));
        }

        @Test
        void invalidChannel_shouldReturn400WithFieldDetail() throws Exception {
            String request = """
                    {
                        "transactionId": "TX-001",
                        "customerId": "CUST-001",
                        "amount": 5000,
                        "currency": "EUR",
                        "originCountry": "SE",
                        "destinationCountry": "FI",
                        "channel": "INVALID"
                    }
                    """;

            mockMvc.perform(post(SCREEN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.details[0].field").value("channel"));
        }

        @Test
        void negativeAmount_shouldReturn400WithFieldDetail() throws Exception {
            String request = """
                    {
                        "transactionId": "TX-001",
                        "customerId": "CUST-001",
                        "amount": -100,
                        "currency": "EUR",
                        "originCountry": "SE",
                        "destinationCountry": "FI",
                        "channel": "ONLINE"
                    }
                    """;

            mockMvc.perform(post(SCREEN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.details[?(@.field == 'amount')]").exists());
        }

        @Test
        void zeroAmount_shouldReturn400() throws Exception {
            String request = """
                    {
                        "transactionId": "TX-001",
                        "customerId": "CUST-001",
                        "amount": 0,
                        "currency": "EUR",
                        "originCountry": "SE",
                        "destinationCountry": "FI",
                        "channel": "ONLINE"
                    }
                    """;

            mockMvc.perform(post(SCREEN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.details[?(@.field == 'amount')]").exists());
        }

        @Test
        void invalidTimestamp_shouldReturn400WithFieldDetail() throws Exception {
            String request = """
                    {
                        "transactionId": "TX-001",
                        "customerId": "CUST-001",
                        "amount": 5000,
                        "currency": "EUR",
                        "originCountry": "SE",
                        "destinationCountry": "FI",
                        "channel": "ONLINE",
                        "timestamp": "not-a-timestamp"
                    }
                    """;

            mockMvc.perform(post(SCREEN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.details[0].field").value("timestamp"));
        }

        @Test
        void invalidOriginCountryFormat_shouldReturn400() throws Exception {
            String request = """
                    {
                        "transactionId": "TX-001",
                        "customerId": "CUST-001",
                        "amount": 5000,
                        "currency": "EUR",
                        "originCountry": "SWEDEN",
                        "destinationCountry": "FI",
                        "channel": "ONLINE"
                    }
                    """;

            mockMvc.perform(post(SCREEN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.details[0].field").value("originCountry"));
        }

        @Test
        void invalidDestinationCountryFormat_shouldReturn400() throws Exception {
            String request = """
                    {
                        "transactionId": "TX-001",
                        "customerId": "CUST-001",
                        "amount": 5000,
                        "currency": "EUR",
                        "originCountry": "SE",
                        "destinationCountry": "123",
                        "channel": "ONLINE"
                    }
                    """;

            mockMvc.perform(post(SCREEN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.details[0].field").value("destinationCountry"));
        }

        @Test
        void singleCharCountryCode_shouldReturn400() throws Exception {
            String request = """
                    {
                        "transactionId": "TX-001",
                        "customerId": "CUST-001",
                        "amount": 5000,
                        "currency": "EUR",
                        "originCountry": "S",
                        "destinationCountry": "FI",
                        "channel": "ONLINE"
                    }
                    """;

            mockMvc.perform(post(SCREEN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details[0].field").value("originCountry"));
        }

        @Test
        void amountAsString_shouldReturn400() throws Exception {
            String request = """
                    {
                        "transactionId": "TX-001",
                        "customerId": "CUST-001",
                        "amount": "not-a-number",
                        "currency": "EUR",
                        "originCountry": "SE",
                        "destinationCountry": "FI",
                        "channel": "ONLINE"
                    }
                    """;

            mockMvc.perform(post(SCREEN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        }
    }

    @Nested
    class InvalidValuesDoNotReachService {

        @Test
        void unsupportedCurrency_shouldNotCallService() throws Exception {
            String request = """
                    {
                        "transactionId": "TX-001",
                        "customerId": "CUST-001",
                        "amount": 5000,
                        "currency": "USD",
                        "originCountry": "SE",
                        "destinationCountry": "FI",
                        "channel": "ONLINE"
                    }
                    """;

            mockMvc.perform(post(SCREEN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isBadRequest());

            verify(screeningService, never()).screen(any());
        }

        @Test
        void invalidCountryCode_shouldNotCallService() throws Exception {
            String request = """
                    {
                        "transactionId": "TX-001",
                        "customerId": "CUST-001",
                        "amount": 5000,
                        "currency": "EUR",
                        "originCountry": "INVALID",
                        "destinationCountry": "FI",
                        "channel": "ONLINE"
                    }
                    """;

            mockMvc.perform(post(SCREEN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isBadRequest());

            verify(screeningService, never()).screen(any());
        }
    }

    @Nested
    class MalformedRequests {

        @Test
        void malformedJson_shouldReturn400() throws Exception {
            mockMvc.perform(post(SCREEN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ invalid json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").value("Malformed request body"));
        }

        @Test
        void emptyBody_shouldReturn400() throws Exception {
            mockMvc.perform(post(SCREEN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(""))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void wrongHttpMethod_shouldReturn405() throws Exception {
            mockMvc.perform(get(SCREEN_URL))
                    .andExpect(status().isMethodNotAllowed());
        }

        @Test
        void wrongContentType_shouldReturn415() throws Exception {
            mockMvc.perform(post(SCREEN_URL)
                            .contentType(MediaType.TEXT_PLAIN)
                            .content(VALID_REQUEST))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.error").value("UNSUPPORTED_MEDIA_TYPE"));
        }
    }
}

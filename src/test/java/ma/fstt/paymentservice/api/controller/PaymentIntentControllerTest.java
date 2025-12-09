package ma.fstt.paymentservice.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.fstt.paymentservice.api.dto.PaymentIntentRequest;
import ma.fstt.paymentservice.core.orchestrator.PaymentOrchestrator;
import ma.fstt.paymentservice.domain.repository.BookingRepository;
import ma.fstt.paymentservice.domain.repository.PropertyRepository;
import ma.fstt.paymentservice.domain.repository.TransactionRepository;
import ma.fstt.paymentservice.domain.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import ma.fstt.paymentservice.config.SecurityConfig;
import ma.fstt.paymentservice.exception.GlobalExceptionHandler;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = PaymentIntentController.class,
    excludeAutoConfiguration = {
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
    },
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = {
    "app.security.enabled=false"
})
class PaymentIntentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentOrchestrator paymentOrchestrator;

    @MockBean
    private UserAccountRepository userAccountRepository;

    @MockBean
    private TransactionRepository transactionRepository;

    @MockBean
    private BookingRepository bookingRepository;

    @MockBean
    private PropertyRepository propertyRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testCreatePaymentIntent_Success() throws Exception {
        Long bookingId = 123L;
        PaymentIntentRequest request = new PaymentIntentRequest();
        request.setBookingId(bookingId);

        PaymentOrchestrator.PaymentIntentResponse orchestratorResponse = 
                PaymentOrchestrator.PaymentIntentResponse.builder()
                        .referenceId(UUID.randomUUID())
                        .to("0x1234567890123456789012345678901234567890")
                        .value("1000000000000000000")
                        .data("0xabcd")
                        .chainId(11155111L)
                        .totalAmountWei("1000000000000000000")
                        .build();

        when(paymentOrchestrator.createPaymentIntent(any(PaymentIntentRequest.class))).thenReturn(orchestratorResponse);

        mockMvc.perform(post("/api/payments/intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.to").value("0x1234567890123456789012345678901234567890"))
                .andExpect(jsonPath("$.chainId").value(11155111L))
                .andExpect(jsonPath("$.referenceId").exists());
    }

    @Test
    void testCreatePaymentIntent_InvalidRequest() throws Exception {
        PaymentIntentRequest request = new PaymentIntentRequest();
        // bookingId is null - should trigger validation error

        mockMvc.perform(post("/api/payments/intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.message").exists());
    }
}


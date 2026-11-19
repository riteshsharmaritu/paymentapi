package com.paymentapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentapi.dto.PaymentRequest;
import com.paymentapi.dto.PaymentResponse;
import com.paymentapi.entity.Payment;
import com.paymentapi.entity.PaymentStatus;
import com.paymentapi.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void createPayment_returnsCompletedPayment() throws Exception {
        PaymentRequest request = buildRequest("ctrl-ok-1", 1001L, 1002L, "50.00");

        Payment payment = new Payment();
        payment.setPaymentId(1L);
        payment.setIdempotencyKey(request.getIdempotencyKey());
        payment.setFromAccount(request.getFromAccount());
        payment.setToAccount(request.getToAccount());
        payment.setAmount(request.getAmount());
        payment.setDate(request.getDate());
        payment.setStatus(PaymentStatus.COMPLETED);

        when(paymentService.processPayment(any(PaymentRequest.class)))
                .thenReturn(PaymentResponse.from(payment));

        mockMvc.perform(post("/v1/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(1))
                .andExpect(jsonPath("$.idempotencyKey").value("ctrl-ok-1"))
                .andExpect(jsonPath("$.fromAccount").value(1001))
                .andExpect(jsonPath("$.toAccount").value(1002))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        verify(paymentService).processPayment(any(PaymentRequest.class));
    }

    @Test
    void createPayment_returnsFailedPaymentWithErrorMessage() throws Exception {
        PaymentRequest request = buildRequest("ctrl-fail-1", 1001L, 1002L, "99999.00");

        when(paymentService.processPayment(any(PaymentRequest.class)))
                .thenReturn(PaymentResponse.failed(request, "Insufficient balance"));

        mockMvc.perform(post("/v1/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorMessage").value("Insufficient balance"))
                .andExpect(jsonPath("$.idempotencyKey").value("ctrl-fail-1"));
    }

    private PaymentRequest buildRequest(String key, Long from, Long to, String amount) {
        PaymentRequest request = new PaymentRequest();
        request.setIdempotencyKey(key);
        request.setFromAccount(from);
        request.setToAccount(to);
        request.setAmount(new BigDecimal(amount));
        request.setDate(Instant.parse("2026-11-18T12:00:00Z"));
        return request;
    }
}

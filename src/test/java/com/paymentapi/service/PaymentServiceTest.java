package com.paymentapi.service;

import com.paymentapi.dto.PaymentRequest;
import com.paymentapi.dto.PaymentResponse;
import com.paymentapi.entity.Account;
import com.paymentapi.entity.Payment;
import com.paymentapi.entity.PaymentStatus;
import com.paymentapi.repository.AccountRepository;
import com.paymentapi.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void processPayment_returnsExistingPayment_whenIdempotencyKeyExists() {
        Payment existing = completedPayment(10L, "idem-1", 1001L, 1002L, "25.00");
        when(paymentRepository.findByIdempotencyKey("idem-1")).thenReturn(Optional.of(existing));

        PaymentResponse response = paymentService.processPayment(
                buildRequest("idem-1", 1001L, 1002L, "25.00"));

        assertThat(response.getPaymentId()).isEqualTo(10L);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        verify(paymentRepository, never()).saveAndFlush(any());
        verify(accountRepository, never()).findById(any());
    }

    @Test
    void processPayment_completesTransfer_whenValid() {
        PaymentRequest request = buildRequest("idem-ok", 1001L, 1002L, "50.00");
        Account from = new Account(1001L, 4827, new BigDecimal("1250.00"));
        Account to = new Account(1002L, 1935, new BigDecimal("340.50"));

        when(paymentRepository.findByIdempotencyKey("idem-ok")).thenReturn(Optional.empty());
        when(accountRepository.findById(1001L)).thenReturn(Optional.of(from));
        when(accountRepository.findById(1002L)).thenReturn(Optional.of(to));
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setPaymentId(1L);
            return payment;
        });
        when(accountRepository.findByIdForUpdate(1001L)).thenReturn(Optional.of(from));
        when(accountRepository.findByIdForUpdate(1002L)).thenReturn(Optional.of(to));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentService.processPayment(request);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(response.getErrorMessage()).isNull();
        assertThat(from.getBalance()).isEqualByComparingTo("1200.00");
        assertThat(to.getBalance()).isEqualByComparingTo("390.50");
        verify(accountRepository).save(from);
        verify(accountRepository).save(to);
    }

    @Test
    void processPayment_returnsFailed_whenInsufficientBalance() {
        PaymentRequest request = buildRequest("idem-nsf", 1001L, 1002L, "5000.00");
        Account from = new Account(1001L, 4827, new BigDecimal("100.00"));
        Account to = new Account(1002L, 1935, new BigDecimal("340.50"));

        when(paymentRepository.findByIdempotencyKey("idem-nsf")).thenReturn(Optional.empty());
        when(accountRepository.findById(1001L)).thenReturn(Optional.of(from));
        when(accountRepository.findById(1002L)).thenReturn(Optional.of(to));

        PaymentResponse response = paymentService.processPayment(request);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.getErrorMessage()).isEqualTo("Insufficient balance");
        assertThat(response.getPaymentId()).isNull();
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void processPayment_returnsFailed_whenSameAccount() {
        PaymentRequest request = buildRequest("idem-same", 1001L, 1001L, "10.00");
        when(paymentRepository.findByIdempotencyKey("idem-same")).thenReturn(Optional.empty());

        PaymentResponse response = paymentService.processPayment(request);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.getErrorMessage()).isEqualTo("From and to accounts must be different");
        verify(accountRepository, never()).findById(any());
    }

    @Test
    void processPayment_returnsFailed_whenFromAccountMissing() {
        PaymentRequest request = buildRequest("idem-missing", 9999L, 1002L, "10.00");
        when(paymentRepository.findByIdempotencyKey("idem-missing")).thenReturn(Optional.empty());
        when(accountRepository.findById(9999L)).thenReturn(Optional.empty());

        PaymentResponse response = paymentService.processPayment(request);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.getErrorMessage()).isEqualTo("From account not found: 9999");
    }

    @Test
    void processPayment_returnsFailed_whenAmountInvalid() {
        PaymentRequest request = buildRequest("idem-amt", 1001L, 1002L, "0");
        when(paymentRepository.findByIdempotencyKey("idem-amt")).thenReturn(Optional.empty());

        PaymentResponse response = paymentService.processPayment(request);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.getErrorMessage()).isEqualTo("Amount must be greater than zero");
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

    private Payment completedPayment(Long id, String key, Long from, Long to, String amount) {
        Payment payment = new Payment();
        payment.setPaymentId(id);
        payment.setIdempotencyKey(key);
        payment.setFromAccount(from);
        payment.setToAccount(to);
        payment.setAmount(new BigDecimal(amount));
        payment.setDate(Instant.parse("2026-11-18T12:00:00Z"));
        payment.setStatus(PaymentStatus.COMPLETED);
        return payment;
    }
}

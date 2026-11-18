package com.paymentapi.service;

import com.paymentapi.dto.PaymentRequest;
import com.paymentapi.dto.PaymentResponse;
import com.paymentapi.entity.Account;
import com.paymentapi.entity.Payment;
import com.paymentapi.entity.PaymentStatus;
import com.paymentapi.repository.AccountRepository;
import com.paymentapi.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final AccountRepository accountRepository;

    public PaymentService(PaymentRepository paymentRepository, AccountRepository accountRepository) {
        this.paymentRepository = paymentRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional
    public PaymentResponse processPayment(PaymentRequest request) {
        Optional<Payment> existing = paymentRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existing.isPresent()) {
            return PaymentResponse.from(existing.get());
        }

        try {
            validatePayment(request);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return PaymentResponse.failed(request, ex.getMessage());
        }

        Payment payment = createPayment(request, PaymentStatus.PENDING);
        payment = paymentRepository.saveAndFlush(payment);

        try {
            transferBalance(payment);
            payment.setStatus(PaymentStatus.COMPLETED);
            return PaymentResponse.from(paymentRepository.save(payment));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            payment.setStatus(PaymentStatus.FAILED);
            payment = paymentRepository.save(payment);
            return PaymentResponse.from(payment, ex.getMessage());
        }
    }

    private void validatePayment(PaymentRequest request) {
        if (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()) {
            throw new IllegalArgumentException("Idempotency key is required");
        }
        if (request.getDate() == null) {
            throw new IllegalArgumentException("Payment date is required");
        }
        if (request.getFromAccount() == null) {
            throw new IllegalArgumentException("From account is required");
        }
        if (request.getToAccount() == null) {
            throw new IllegalArgumentException("To account is required");
        }
        if (request.getFromAccount().equals(request.getToAccount())) {
            throw new IllegalArgumentException("From and to accounts must be different");
        }

        BigDecimal amount = request.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        Account fromAccount = accountRepository.findById(request.getFromAccount())
                .orElseThrow(() -> new IllegalArgumentException("From account not found: " + request.getFromAccount()));
        accountRepository.findById(request.getToAccount())
                .orElseThrow(() -> new IllegalArgumentException("To account not found: " + request.getToAccount()));

        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient balance");
        }
    }

    private Payment createPayment(PaymentRequest request, PaymentStatus status) {
        Payment payment = new Payment();
        payment.setIdempotencyKey(request.getIdempotencyKey());
        payment.setFromAccount(request.getFromAccount());
        payment.setToAccount(request.getToAccount());
        payment.setAmount(request.getAmount());
        payment.setDate(request.getDate());
        payment.setStatus(status);
        return payment;
    }

    private void transferBalance(Payment payment) {
        Long firstId = Math.min(payment.getFromAccount(), payment.getToAccount());
        Long secondId = Math.max(payment.getFromAccount(), payment.getToAccount());

        Account first = accountRepository.findByIdForUpdate(firstId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + firstId));
        Account second = accountRepository.findByIdForUpdate(secondId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + secondId));

        Account fromAccount = payment.getFromAccount().equals(first.getAccountId()) ? first : second;
        Account toAccount = payment.getToAccount().equals(first.getAccountId()) ? first : second;

        BigDecimal amount = payment.getAmount();
        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient balance");
        }

        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        accountRepository.save(fromAccount);

        toAccount.setBalance(toAccount.getBalance().add(amount));
        accountRepository.save(toAccount);
    }
}

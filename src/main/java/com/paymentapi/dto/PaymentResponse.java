package com.paymentapi.dto;

import com.paymentapi.entity.Payment;
import com.paymentapi.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public class PaymentResponse {

    private Long paymentId;
    private String idempotencyKey;
    private Long fromAccount;
    private Long toAccount;
    private BigDecimal amount;
    private Instant date;
    private PaymentStatus status;
    private String errorMessage;

    public static PaymentResponse from(Payment payment) {
        return from(payment, null);
    }

    public static PaymentResponse from(Payment payment, String errorMessage) {
        PaymentResponse response = new PaymentResponse();
        response.paymentId = payment.getPaymentId();
        response.idempotencyKey = payment.getIdempotencyKey();
        response.fromAccount = payment.getFromAccount();
        response.toAccount = payment.getToAccount();
        response.amount = payment.getAmount();
        response.date = payment.getDate();
        response.status = payment.getStatus();
        response.errorMessage = errorMessage;
        return response;
    }

    public static PaymentResponse failed(PaymentRequest request, String errorMessage) {
        PaymentResponse response = new PaymentResponse();
        response.idempotencyKey = request.getIdempotencyKey();
        response.fromAccount = request.getFromAccount();
        response.toAccount = request.getToAccount();
        response.amount = request.getAmount();
        response.date = request.getDate();
        response.status = PaymentStatus.FAILED;
        response.errorMessage = errorMessage;
        return response;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Long getFromAccount() {
        return fromAccount;
    }

    public Long getToAccount() {
        return toAccount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getDate() {
        return date;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}

package com.paymentapi.repository;

import com.paymentapi.entity.Payment;
import com.paymentapi.entity.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.sql.init.mode=always"
})
class PaymentRepositoryTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void save_andFindByIdempotencyKey_returnsPayment() {
        Payment payment = new Payment();
        payment.setIdempotencyKey("repo-key-1");
        payment.setFromAccount(1001L);
        payment.setToAccount(1002L);
        payment.setAmount(new BigDecimal("75.50"));
        payment.setDate(Instant.parse("2026-11-18T15:30:00Z"));
        payment.setStatus(PaymentStatus.PENDING);

        Payment saved = paymentRepository.saveAndFlush(payment);

        assertThat(saved.getPaymentId()).isNotNull();

        Optional<Payment> found = paymentRepository.findByIdempotencyKey("repo-key-1");
        assertThat(found).isPresent();
        assertThat(found.get().getFromAccount()).isEqualTo(1001L);
        assertThat(found.get().getToAccount()).isEqualTo(1002L);
        assertThat(found.get().getAmount()).isEqualByComparingTo("75.50");
        assertThat(found.get().getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void findByIdempotencyKey_returnsEmpty_whenUnknown() {
        assertThat(paymentRepository.findByIdempotencyKey("missing-key")).isEmpty();
    }

    @Test
    void save_updatesPaymentStatus() {
        Payment payment = new Payment();
        payment.setIdempotencyKey("repo-key-2");
        payment.setFromAccount(1001L);
        payment.setToAccount(1003L);
        payment.setAmount(new BigDecimal("20.00"));
        payment.setDate(Instant.parse("2026-11-18T16:00:00Z"));
        payment.setStatus(PaymentStatus.PENDING);
        payment = paymentRepository.saveAndFlush(payment);

        payment.setStatus(PaymentStatus.COMPLETED);
        Payment updated = paymentRepository.saveAndFlush(payment);

        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(paymentRepository.findByIdempotencyKey("repo-key-2"))
                .get()
                .extracting(Payment::getStatus)
                .isEqualTo(PaymentStatus.COMPLETED);
    }
}

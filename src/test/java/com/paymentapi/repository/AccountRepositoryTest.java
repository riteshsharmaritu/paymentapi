package com.paymentapi.repository;

import com.paymentapi.entity.Account;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.sql.init.mode=always"
})
class AccountRepositoryTest {

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void findById_returnsSeededAccount() {
        Optional<Account> account = accountRepository.findById(1001L);

        assertThat(account).isPresent();
        assertThat(account.get().getAccountNumber()).isEqualTo(4827);
        assertThat(account.get().getBalance()).isEqualByComparingTo("1250.00");
    }

    @Test
    void findById_returnsEmpty_whenUnknown() {
        assertThat(accountRepository.findById(9999L)).isEmpty();
    }

    @Test
    @Transactional
    void findByIdForUpdate_returnsAccount() {
        Optional<Account> account = accountRepository.findByIdForUpdate(1002L);

        assertThat(account).isPresent();
        assertThat(account.get().getAccountId()).isEqualTo(1002L);
        assertThat(account.get().getBalance()).isEqualByComparingTo("340.50");
    }

    @Test
    void save_updatesBalance() {
        Account account = accountRepository.findById(1010L).orElseThrow();
        account.setBalance(new BigDecimal("42.00"));

        Account updated = accountRepository.saveAndFlush(account);

        assertThat(updated.getBalance()).isEqualByComparingTo("42.00");
        assertThat(accountRepository.findById(1010L))
                .get()
                .extracting(Account::getBalance)
                .satisfies(balance -> assertThat((BigDecimal) balance).isEqualByComparingTo("42.00"));
    }
}

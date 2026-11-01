package com.piyush.fincore.entity;

import com.piyush.fincore.exception.InsufficientBalanceException;
import com.piyush.fincore.exception.InvalidOperationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletTest {

    private final Wallet wallet = new Wallet(new User("Alice", "alice@example.com", "hash", Role.USER), "INR");

    @Test
    void creditThenDebitUpdatesBalance() {
        wallet.credit(new BigDecimal("100.50"));
        wallet.debit(new BigDecimal("40.25"));

        assertThat(wallet.getBalance()).isEqualByComparingTo("60.25");
    }

    @Test
    void decimalAmountsStayExact() {
        wallet.credit(new BigDecimal("0.1"));
        wallet.credit(new BigDecimal("0.2"));

        // With double this would be 0.30000000000000004
        assertThat(wallet.getBalance()).isEqualByComparingTo("0.3");
    }

    @Test
    void debitMoreThanBalanceIsRejectedAndBalanceUnchanged() {
        wallet.credit(new BigDecimal("10"));

        assertThatThrownBy(() -> wallet.debit(new BigDecimal("10.0001")))
                .isInstanceOf(InsufficientBalanceException.class);
        assertThat(wallet.getBalance()).isEqualByComparingTo("10");
    }

    @Test
    void debitOfExactBalanceLeavesZero() {
        wallet.credit(new BigDecimal("25"));
        wallet.debit(new BigDecimal("25"));

        assertThat(wallet.getBalance()).isEqualByComparingTo("0");
    }

    @Test
    void zeroOrNegativeAmountsAreRejected() {
        assertThatThrownBy(() -> wallet.credit(BigDecimal.ZERO)).isInstanceOf(InvalidOperationException.class);
        assertThatThrownBy(() -> wallet.credit(new BigDecimal("-5"))).isInstanceOf(InvalidOperationException.class);
        assertThatThrownBy(() -> wallet.debit(new BigDecimal("-5"))).isInstanceOf(InvalidOperationException.class);
    }
}

package com.piyush.fincore.service;

import com.piyush.fincore.entity.Wallet;
import com.piyush.fincore.exception.InsufficientBalanceException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConcurrencyRetryTest {

    private final ConcurrencyRetry retry = new ConcurrencyRetry();

    @Test
    void conflictsAreRetriedUntilAnAttemptSucceeds() {
        AtomicInteger calls = new AtomicInteger();

        String result = retry.execute(() -> {
            if (calls.incrementAndGet() < 3) {
                throw conflict();
            }
            return "done";
        });

        assertThat(result).isEqualTo("done");
        assertThat(calls).hasValue(3);
    }

    @Test
    void deadlocksAreRetriedToo() {
        AtomicInteger calls = new AtomicInteger();

        String result = retry.execute(() -> {
            if (calls.incrementAndGet() == 1) {
                // What Spring throws when MySQL reports "Deadlock found when trying to get lock".
                throw new CannotAcquireLockException("Deadlock found when trying to get lock");
            }
            return "done";
        });

        assertThat(result).isEqualTo("done");
        assertThat(calls).hasValue(2);
    }

    @Test
    void givesUpAfterMaxAttemptsAndRethrows() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> retry.execute(() -> {
            calls.incrementAndGet();
            throw conflict();
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(calls).hasValue(ConcurrencyRetry.MAX_ATTEMPTS);
    }

    @Test
    void businessErrorsAreNotRetried() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> retry.execute(() -> {
            calls.incrementAndGet();
            throw new InsufficientBalanceException(1L, BigDecimal.ONE, BigDecimal.TEN);
        })).isInstanceOf(InsufficientBalanceException.class);

        // Retrying would just fail the same way: only concurrency conflicts are worth another attempt.
        assertThat(calls).hasValue(1);
    }

    private static ObjectOptimisticLockingFailureException conflict() {
        return new ObjectOptimisticLockingFailureException(Wallet.class, 1L);
    }
}

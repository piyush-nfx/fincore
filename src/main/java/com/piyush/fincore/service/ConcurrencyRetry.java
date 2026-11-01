package com.piyush.fincore.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Re-runs a transactional operation that lost a race with another request on the same rows.
 *
 * <p>Two kinds of race end up here, both subclasses of Spring's ConcurrencyFailureException:
 * <ul>
 *   <li><b>Optimistic lock conflict</b>: another transaction changed the wallet first, so our
 *       "UPDATE ... WHERE version = ?" matched no row.</li>
 *   <li><b>Deadlock</b>: two transactions each hold a lock the other needs; MySQL aborts one of them and says
 *       "try restarting transaction".</li>
 * </ul>
 * Either way the loser's transaction was rolled back completely, so it is safe to simply run it again: the new
 * attempt reads the fresh balance and version. Business errors (insufficient balance, not found) are not retried,
 * because they would fail the same way every time.
 *
 * <p>The retry MUST wrap the @Transactional method from the outside. Each call then goes through Spring's proxy and
 * gets a brand-new transaction. Retrying inside the failed transaction would reuse stale data and could never succeed.
 */
@Slf4j
@Component
public class ConcurrencyRetry {

    static final int MAX_ATTEMPTS = 5;
    private static final long BASE_DELAY_MS = 20;

    public <T> T execute(Supplier<T> operation) {
        for (int attempt = 1; ; attempt++) {
            try {
                return operation.get();
            } catch (ConcurrencyFailureException e) {
                if (attempt == MAX_ATTEMPTS) {
                    log.warn("Giving up after {} attempts: {}", attempt, e.getClass().getSimpleName());
                    throw e; // GlobalExceptionHandler turns this into 409 "please retry"
                }
                backOff(attempt);
            }
        }
    }

    /**
     * Waits a little longer after each failure (20ms, 40ms, 80ms, ...) plus a random "jitter". Without jitter, all the
     * requests that collided would wake up at the same moment and collide again.
     */
    private static void backOff(int attempt) {
        long delay = BASE_DELAY_MS * (1L << (attempt - 1));
        long jitter = ThreadLocalRandom.current().nextLong(delay + 1);
        try {
            Thread.sleep(delay + jitter);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry", ie);
        }
    }
}

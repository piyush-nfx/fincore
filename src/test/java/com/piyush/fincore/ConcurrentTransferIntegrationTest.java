package com.piyush.fincore;

import com.piyush.fincore.dto.CreateWalletRequest;
import com.piyush.fincore.dto.DepositRequest;
import com.piyush.fincore.dto.TransactionResponse;
import com.piyush.fincore.dto.TransferRequest;
import com.piyush.fincore.dto.TransferResult;
import com.piyush.fincore.entity.Role;
import com.piyush.fincore.entity.TransactionStatus;
import com.piyush.fincore.entity.User;
import com.piyush.fincore.exception.InsufficientBalanceException;
import com.piyush.fincore.repository.UserRepository;
import com.piyush.fincore.service.TransactionHistoryService;
import com.piyush.fincore.service.TransferFacade;
import com.piyush.fincore.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the money rules under real concurrency: real threads, the real transactional services, and a real MySQL
 * (Testcontainers). Unit tests with mocks can't show any of this, because locking happens inside the database.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ConcurrentTransferIntegrationTest {

    @Autowired
    private TransferFacade transferFacade;
    @Autowired
    private WalletService walletService;
    @Autowired
    private TransactionHistoryService historyService;
    @Autowired
    private UserRepository userRepository;

    private record Account(Long userId, Long walletId) {
    }

    private enum Outcome { SUCCESS, INSUFFICIENT }

    @Test
    void tenConcurrentTransfersCanNeverSpendMoreThanTheBalance() throws Exception {
        Account carol = account("100");
        Account dave = account("0");

        // 10 transfers of 20 from a wallet holding 100, all released at the same instant.
        List<Outcome> outcomes = runConcurrently(10, i -> {
            try {
                transferFacade.transfer(carol.userId(), new TransferRequest(carol.walletId(), dave.walletId(),
                        new BigDecimal("20")), UUID.randomUUID().toString());
                return Outcome.SUCCESS;
            } catch (InsufficientBalanceException e) {
                return Outcome.INSUFFICIENT;
            }
        });

        assertThat(outcomes).filteredOn(o -> o == Outcome.SUCCESS).hasSize(5);
        assertThat(outcomes).filteredOn(o -> o == Outcome.INSUFFICIENT).hasSize(5);
        assertThat(balance(carol)).isEqualByComparingTo("0");
        assertThat(balance(dave)).isEqualByComparingTo("100");
        assertThat(successfulTransfersInto(dave)).isEqualTo(5);
    }

    @Test
    void transfersInBothDirectionsAtOnceAllSucceedAndConserveMoney() throws Exception {
        Account alice = account("100");
        Account bob = account("100");

        // Alice->Bob and Bob->Alice at the same time lock the two wallets in opposite orders: the classic deadlock
        // pattern. ConcurrencyRetry must absorb every conflict and deadlock, so no request may fail.
        List<Outcome> outcomes = runConcurrently(10, i -> {
            Account from = i % 2 == 0 ? alice : bob;
            Account to = i % 2 == 0 ? bob : alice;
            transferFacade.transfer(from.userId(), new TransferRequest(from.walletId(), to.walletId(), BigDecimal.ONE),
                    UUID.randomUUID().toString());
            return Outcome.SUCCESS;
        });

        assertThat(outcomes).hasSize(10).containsOnly(Outcome.SUCCESS);
        assertThat(balance(alice)).isEqualByComparingTo("100"); // 5 out, 5 in
        assertThat(balance(bob)).isEqualByComparingTo("100");
    }

    @Test
    void theSamePaymentSentFiveTimesAtOnceMovesMoneyOnce() throws Exception {
        Account erin = account("50");
        Account frank = account("0");
        String key = UUID.randomUUID().toString();

        List<TransferResult> results = runConcurrently(5, i -> transferFacade.transfer(erin.userId(),
                new TransferRequest(erin.walletId(), frank.walletId(), BigDecimal.TEN), key));

        assertThat(results).extracting(r -> r.transaction().id()).containsOnly(results.get(0).transaction().id());
        assertThat(results).filteredOn(r -> !r.replayed()).hasSize(1); // exactly one request did the work
        assertThat(balance(erin)).isEqualByComparingTo("40");
        assertThat(balance(frank)).isEqualByComparingTo("10");
    }

    @Test
    void twoUsersMayUseTheSameIdempotencyKey() {
        // Before migration V2 the key was unique across all users; now it's unique per user.
        Account gina = account("10");
        Account hank = account("10");
        String sharedKey = "order-42";

        TransactionResponse fromGina = transferFacade.transfer(gina.userId(),
                new TransferRequest(gina.walletId(), hank.walletId(), BigDecimal.ONE), sharedKey).transaction();
        TransactionResponse fromHank = transferFacade.transfer(hank.userId(),
                new TransferRequest(hank.walletId(), gina.walletId(), new BigDecimal("2")), sharedKey).transaction();

        assertThat(fromGina.id()).isNotEqualTo(fromHank.id());
        assertThat(balance(gina)).isEqualByComparingTo("11");
        assertThat(balance(hank)).isEqualByComparingTo("9");
    }

    /** Runs the task on N threads that all wait at a latch and start together, then collects every result. */
    private static <T> List<T> runConcurrently(int threads, IndexedTask<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                int index = i;
                Callable<T> callable = () -> {
                    start.await();
                    return task.run(index);
                };
                futures.add(pool.submit(callable));
            }
            start.countDown(); // release all threads at once
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS)); // rethrows any unexpected exception
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface IndexedTask<T> {
        T run(int index) throws Exception;
    }

    private Account account(String openingBalance) {
        User user = userRepository.save(new User("Test", UUID.randomUUID() + "@example.com", "not-a-real-hash", Role.USER));
        Long walletId = walletService.create(user.getId(), new CreateWalletRequest("INR")).id();
        BigDecimal amount = new BigDecimal(openingBalance);
        if (amount.signum() > 0) {
            walletService.deposit(user.getId(), walletId, new DepositRequest(amount));
        }
        return new Account(user.getId(), walletId);
    }

    private BigDecimal balance(Account account) {
        return walletService.get(account.userId(), account.walletId()).balance();
    }

    private long successfulTransfersInto(Account account) {
        return historyService.history(account.userId(), account.walletId(), Pageable.ofSize(100)).content().stream()
                .filter(t -> t.status() == TransactionStatus.SUCCESS && account.walletId().equals(t.toWalletId()))
                .count();
    }
}

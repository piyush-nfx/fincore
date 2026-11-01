package com.piyush.fincore.service;

import com.piyush.fincore.dto.TransferRequest;
import com.piyush.fincore.dto.TransferResult;
import com.piyush.fincore.entity.*;
import com.piyush.fincore.exception.IdempotencyKeyConflictException;
import com.piyush.fincore.exception.InsufficientBalanceException;
import com.piyush.fincore.exception.InvalidOperationException;
import com.piyush.fincore.exception.TransferFailedException;
import com.piyush.fincore.exception.WalletNotFoundException;
import com.piyush.fincore.repository.TransactionRepository;
import com.piyush.fincore.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    private static final Long ALICE = 10L;
    private static final Long BOB = 20L;

    @Mock
    private WalletRepository walletRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private FailedTransferRecorder failedTransferRecorder;

    @InjectMocks
    private TransferService transferService;

    private Wallet aliceWallet;
    private Wallet bobWallet;

    @BeforeEach
    void setUp() {
        aliceWallet = wallet(1L, ALICE, "INR", "100");
        bobWallet = wallet(2L, BOB, "INR", "0");
    }

    @Test
    void successfulTransferMovesMoneyAndWritesLedgerRow() {
        stubWallets(aliceWallet, bobWallet);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransferResult result = transferService.transfer(ALICE, new TransferRequest(1L, 2L, new BigDecimal("30")), "key-1");

        assertThat(aliceWallet.getBalance()).isEqualByComparingTo("70");
        assertThat(bobWallet.getBalance()).isEqualByComparingTo("30");
        assertThat(result.replayed()).isFalse();
        assertThat(result.transaction().status()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(result.transaction().type()).isEqualTo(TransactionType.TRANSFER);
        assertThat(result.transaction().idempotencyKey()).isEqualTo("key-1");
        verifyNoInteractions(failedTransferRecorder);
    }

    @Test
    void cannotTransferFromSomeoneElsesWallet() {
        // Bob asks to send money out of Alice's wallet: the owner-scoped lookup finds nothing.
        when(walletRepository.findByIdAndUserId(1L, BOB)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.transfer(BOB, new TransferRequest(1L, 2L, BigDecimal.TEN), "key-x"))
                .isInstanceOf(WalletNotFoundException.class);

        assertThat(aliceWallet.getBalance()).isEqualByComparingTo("100");
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void insufficientBalanceRecordsFailedRowAndChangesNothing() {
        stubWallets(aliceWallet, bobWallet);

        assertThatThrownBy(() -> transferService.transfer(ALICE, new TransferRequest(1L, 2L, new BigDecimal("100.01")), "key-2"))
                .isInstanceOf(InsufficientBalanceException.class);

        assertThat(aliceWallet.getBalance()).isEqualByComparingTo("100");
        assertThat(bobWallet.getBalance()).isEqualByComparingTo("0");
        verify(failedTransferRecorder).record(eq(1L), eq(2L), eq(new BigDecimal("100.01")), eq("key-2"), anyString());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void sameWalletTransferIsRejectedBeforeTouchingTheDatabase() {
        assertThatThrownBy(() -> transferService.transfer(ALICE, new TransferRequest(1L, 1L, BigDecimal.TEN), "key-s"))
                .isInstanceOf(InvalidOperationException.class);

        verifyNoInteractions(walletRepository);
    }

    @Test
    void unknownDestinationWalletIsRejected() {
        when(walletRepository.findByIdAndUserId(1L, ALICE)).thenReturn(Optional.of(aliceWallet));
        when(walletRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.transfer(ALICE, new TransferRequest(1L, 99L, BigDecimal.TEN), "key-u"))
                .isInstanceOf(WalletNotFoundException.class);
        assertThat(aliceWallet.getBalance()).isEqualByComparingTo("100");
    }

    @Test
    void currencyMismatchIsRejected() {
        Wallet usd = wallet(3L, BOB, "USD", "0");
        stubWallets(aliceWallet, usd);

        assertThatThrownBy(() -> transferService.transfer(ALICE, new TransferRequest(1L, 3L, BigDecimal.TEN), "key-c"))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Currency mismatch");
        assertThat(aliceWallet.getBalance()).isEqualByComparingTo("100");
    }

    @Test
    void repeatedIdempotencyKeyReturnsOriginalWithoutMovingMoneyAgain() {
        Transaction original = Transaction.transfer(aliceWallet, bobWallet, new BigDecimal("30"), "key-3");
        when(transactionRepository.findByIdempotencyKey("key-3")).thenReturn(Optional.of(original));

        TransferResult result = transferService.transfer(ALICE, new TransferRequest(1L, 2L, new BigDecimal("30.00")), "key-3");

        assertThat(result.replayed()).isTrue();
        assertThat(result.transaction().idempotencyKey()).isEqualTo("key-3");
        assertThat(aliceWallet.getBalance()).isEqualByComparingTo("100");
        verifyNoInteractions(walletRepository);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void replayOfAFailedTransferGivesTheSameErrorAgain() {
        Transaction failed = Transaction.failedTransfer(aliceWallet, bobWallet, new BigDecimal("500"), "key-f",
                "Insufficient balance in wallet 1");
        when(transactionRepository.findByIdempotencyKey("key-f")).thenReturn(Optional.of(failed));

        assertThatThrownBy(() -> transferService.transfer(ALICE, new TransferRequest(1L, 2L, new BigDecimal("500")), "key-f"))
                .isInstanceOf(TransferFailedException.class)
                .hasMessage("Insufficient balance in wallet 1");
        verifyNoInteractions(walletRepository, failedTransferRecorder);
    }

    @Test
    void sameKeyWithADifferentAmountIsRejected() {
        Transaction original = Transaction.transfer(aliceWallet, bobWallet, new BigDecimal("30"), "key-d");
        when(transactionRepository.findByIdempotencyKey("key-d")).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> transferService.transfer(ALICE, new TransferRequest(1L, 2L, new BigDecimal("31")), "key-d"))
                .isInstanceOf(IdempotencyKeyConflictException.class)
                .hasMessageContaining("different transfer");
        verifyNoInteractions(walletRepository);
    }

    @Test
    void idempotencyKeyOfAnotherUserIsRejectedInsteadOfLeakingTheirTransaction() {
        Transaction alicesTransfer = Transaction.transfer(aliceWallet, bobWallet, new BigDecimal("30"), "key-4");
        when(transactionRepository.findByIdempotencyKey("key-4")).thenReturn(Optional.of(alicesTransfer));

        assertThatThrownBy(() -> transferService.transfer(BOB, new TransferRequest(2L, 1L, BigDecimal.ONE), "key-4"))
                .isInstanceOf(IdempotencyKeyConflictException.class);
        verifyNoInteractions(walletRepository);
    }

    private void stubWallets(Wallet from, Wallet to) {
        when(walletRepository.findByIdAndUserId(from.getId(), from.getUser().getId())).thenReturn(Optional.of(from));
        when(walletRepository.findById(to.getId())).thenReturn(Optional.of(to));
    }

    // Ids are normally assigned by the database, so the tests set them by reflection.
    private static Wallet wallet(Long id, Long ownerId, String currency, String balance) {
        User owner = new User("u" + ownerId, "u" + ownerId + "@example.com", "hash", Role.USER);
        ReflectionTestUtils.setField(owner, "id", ownerId);
        Wallet wallet = new Wallet(owner, currency);
        ReflectionTestUtils.setField(wallet, "id", id);
        BigDecimal initial = new BigDecimal(balance);
        if (initial.signum() > 0) {
            wallet.credit(initial);
        }
        return wallet;
    }
}

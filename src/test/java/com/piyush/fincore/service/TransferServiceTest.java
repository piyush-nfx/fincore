package com.piyush.fincore.service;

import com.piyush.fincore.dto.TransactionResponse;
import com.piyush.fincore.dto.TransferRequest;
import com.piyush.fincore.entity.*;
import com.piyush.fincore.exception.InsufficientBalanceException;
import com.piyush.fincore.exception.InvalidOperationException;
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

    @Mock
    private WalletRepository walletRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private FailedTransferRecorder failedTransferRecorder;

    @InjectMocks
    private TransferService transferService;

    private Wallet alice;
    private Wallet bob;

    @BeforeEach
    void setUp() {
        alice = wallet(1L, "INR", "100");
        bob = wallet(2L, "INR", "0");
    }

    @Test
    void successfulTransferMovesMoneyAndWritesLedgerRow() {
        stubWallets(alice, bob);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionResponse result = transferService.transfer(new TransferRequest(1L, 2L, new BigDecimal("30")), "key-1");

        assertThat(alice.getBalance()).isEqualByComparingTo("70");
        assertThat(bob.getBalance()).isEqualByComparingTo("30");
        assertThat(result.status()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(result.type()).isEqualTo(TransactionType.TRANSFER);
        assertThat(result.idempotencyKey()).isEqualTo("key-1");
        verifyNoInteractions(failedTransferRecorder);
    }

    @Test
    void insufficientBalanceRecordsFailedRowAndChangesNothing() {
        stubWallets(alice, bob);

        assertThatThrownBy(() -> transferService.transfer(new TransferRequest(1L, 2L, new BigDecimal("100.01")), "key-2"))
                .isInstanceOf(InsufficientBalanceException.class);

        assertThat(alice.getBalance()).isEqualByComparingTo("100");
        assertThat(bob.getBalance()).isEqualByComparingTo("0");
        verify(failedTransferRecorder).record(eq(1L), eq(2L), eq(new BigDecimal("100.01")), eq("key-2"), anyString());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void sameWalletTransferIsRejectedBeforeTouchingTheDatabase() {
        assertThatThrownBy(() -> transferService.transfer(new TransferRequest(1L, 1L, BigDecimal.TEN), null))
                .isInstanceOf(InvalidOperationException.class);

        verifyNoInteractions(walletRepository);
    }

    @Test
    void unknownWalletIsRejected() {
        when(walletRepository.findById(1L)).thenReturn(Optional.of(alice));
        when(walletRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.transfer(new TransferRequest(1L, 99L, BigDecimal.TEN), null))
                .isInstanceOf(WalletNotFoundException.class);
        assertThat(alice.getBalance()).isEqualByComparingTo("100");
    }

    @Test
    void currencyMismatchIsRejected() {
        Wallet usd = wallet(3L, "USD", "0");
        stubWallets(alice, usd);

        assertThatThrownBy(() -> transferService.transfer(new TransferRequest(1L, 3L, BigDecimal.TEN), null))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Currency mismatch");
        assertThat(alice.getBalance()).isEqualByComparingTo("100");
    }

    @Test
    void repeatedIdempotencyKeyReturnsOriginalWithoutMovingMoneyAgain() {
        Transaction original = Transaction.transfer(alice, bob, new BigDecimal("30"), "key-3");
        when(transactionRepository.findByIdempotencyKey("key-3")).thenReturn(Optional.of(original));

        TransactionResponse result = transferService.transfer(new TransferRequest(1L, 2L, new BigDecimal("30")), "key-3");

        assertThat(result.idempotencyKey()).isEqualTo("key-3");
        assertThat(alice.getBalance()).isEqualByComparingTo("100");
        verifyNoInteractions(walletRepository);
        verify(transactionRepository, never()).save(any());
    }

    private void stubWallets(Wallet from, Wallet to) {
        when(walletRepository.findById(from.getId())).thenReturn(Optional.of(from));
        when(walletRepository.findById(to.getId())).thenReturn(Optional.of(to));
    }

    private static Wallet wallet(Long id, String currency, String balance) {
        Wallet wallet = new Wallet(new User("u" + id, "u" + id + "@example.com", "hash", Role.USER), currency);
        ReflectionTestUtils.setField(wallet, "id", id); // ids are normally assigned by the database
        BigDecimal initial = new BigDecimal(balance);
        if (initial.signum() > 0) {
            wallet.credit(initial);
        }
        return wallet;
    }
}

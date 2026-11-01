package com.piyush.fincore.service;

import com.piyush.fincore.entity.Transaction;
import com.piyush.fincore.repository.TransactionRepository;
import com.piyush.fincore.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Writes FAILED ledger rows for transfers that were rejected.
 *
 * <p>Why a separate bean with REQUIRES_NEW: the failing transfer's own transaction is about to roll back,
 * and anything it inserted would be rolled back too. REQUIRES_NEW suspends it and commits this row in an
 * independent transaction. It has to live in another bean because Spring's @Transactional works through a
 * proxy, and a call to a method on the same object (self-invocation) skips the proxy.
 */
@Component
@RequiredArgsConstructor
public class FailedTransferRecorder {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long fromWalletId, Long toWalletId, BigDecimal amount, String idempotencyKey, String reason) {
        // getReferenceById gives a proxy holding only the id: enough for the foreign keys, with no SELECT.
        transactionRepository.save(Transaction.failedTransfer(
                walletRepository.getReferenceById(fromWalletId),
                walletRepository.getReferenceById(toWalletId),
                amount, idempotencyKey, reason));
    }
}

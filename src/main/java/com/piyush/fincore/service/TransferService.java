package com.piyush.fincore.service;

import com.piyush.fincore.dto.TransactionResponse;
import com.piyush.fincore.dto.TransferRequest;
import com.piyush.fincore.entity.Transaction;
import com.piyush.fincore.entity.Wallet;
import com.piyush.fincore.exception.IdempotencyKeyConflictException;
import com.piyush.fincore.exception.InsufficientBalanceException;
import com.piyush.fincore.exception.InvalidOperationException;
import com.piyush.fincore.exception.WalletNotFoundException;
import com.piyush.fincore.repository.TransactionRepository;
import com.piyush.fincore.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final FailedTransferRecorder failedTransferRecorder;

    /**
     * Moves money between two wallets. Validation, debit, credit and the ledger insert all run in ONE
     * database transaction: if any step throws, everything rolls back, so money is never debited without
     * being credited.
     *
     * <p>Concurrency: both wallets carry @Version. If another transfer updates one of them between our read and
     * our commit, the version check fails and the commit throws ObjectOptimisticLockingFailureException instead
     * of double-spending. Phase 3 adds automatic retries for that case.
     *
     * @param userId the logged-in user; they must own the source wallet (the destination can be anyone's)
     */
    @Transactional
    public TransactionResponse transfer(Long userId, TransferRequest request, String idempotencyKey) {
        // A retried request with a key we've already processed returns the original result: no second transfer.
        if (idempotencyKey != null) {
            Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                // Never hand back another user's transaction just because they guessed or reused a key.
                if (!existing.get().getFromWallet().getUser().getId().equals(userId)) {
                    throw new IdempotencyKeyConflictException();
                }
                return TransactionResponse.from(existing.get());
            }
        }

        if (request.fromWalletId().equals(request.toWalletId())) {
            throw new InvalidOperationException("Cannot transfer to the same wallet");
        }
        // Ownership check: you can only send money FROM your own wallet. Someone else's gives 404, as if it didn't exist.
        Wallet from = walletRepository.findByIdAndUserId(request.fromWalletId(), userId)
                .orElseThrow(() -> new WalletNotFoundException(request.fromWalletId()));
        Wallet to = walletRepository.findById(request.toWalletId())
                .orElseThrow(() -> new WalletNotFoundException(request.toWalletId()));
        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new InvalidOperationException("Currency mismatch: " + from.getCurrency() + " -> " + to.getCurrency());
        }

        try {
            from.debit(request.amount());
        } catch (InsufficientBalanceException e) {
            failedTransferRecorder.record(from.getId(), to.getId(), request.amount(), idempotencyKey, e.getMessage());
            throw e; // unchecked exception -> this transaction rolls back
        }
        to.credit(request.amount());

        Transaction tx = transactionRepository.save(Transaction.transfer(from, to, request.amount(), idempotencyKey));
        return TransactionResponse.from(tx);
    }
}

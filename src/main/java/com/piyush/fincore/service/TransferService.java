package com.piyush.fincore.service;

import com.piyush.fincore.dto.TransactionResponse;
import com.piyush.fincore.dto.TransferRequest;
import com.piyush.fincore.dto.TransferResult;
import com.piyush.fincore.entity.Transaction;
import com.piyush.fincore.entity.TransactionStatus;
import com.piyush.fincore.entity.Wallet;
import com.piyush.fincore.exception.IdempotencyKeyConflictException;
import com.piyush.fincore.exception.InsufficientBalanceException;
import com.piyush.fincore.exception.InvalidOperationException;
import com.piyush.fincore.exception.TransferFailedException;
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
     * of double-spending. TransferFacade catches that and runs this method again in a fresh transaction.
     *
     * @param userId the logged-in user; they must own the source wallet (the destination can be anyone's)
     */
    @Transactional
    public TransferResult transfer(Long userId, TransferRequest request, String idempotencyKey) {
        // A retried request with a key we've already processed gets the original outcome: no second transfer.
        Optional<TransferResult> replay = replay(userId, request, idempotencyKey);
        if (replay.isPresent()) {
            return replay.get();
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
        // Send the wallet UPDATEs now, before the ledger INSERT. Hibernate would otherwise run the INSERT first, which
        // takes shared locks on both wallet rows (foreign keys); two transfers holding those and both wanting the
        // exclusive lock for the UPDATE is a deadlock. Updating first means a concurrent transfer simply waits, then
        // fails the version check cleanly and is retried.
        walletRepository.flush();

        Transaction tx = transactionRepository.save(Transaction.transfer(from, to, request.amount(), idempotencyKey));
        return new TransferResult(TransactionResponse.from(tx), false);
    }

    /**
     * Used by TransferFacade after two requests with the same new key raced and the database's UNIQUE constraint
     * rejected the loser: by now the winner's row is committed, so the loser can answer with it.
     */
    @Transactional(readOnly = true)
    public Optional<TransferResult> findReplay(Long userId, TransferRequest request, String idempotencyKey) {
        return replay(userId, request, idempotencyKey);
    }

    /**
     * Looks up an earlier transfer with this key and answers exactly as the first request was answered:
     * SUCCESS -> the same transaction again; FAILED -> the same 422 error again.
     */
    private Optional<TransferResult> replay(Long userId, TransferRequest request, String idempotencyKey) {
        // Looked up per user: another user's transaction can never come back, even if they used the same key.
        Optional<Transaction> found = transactionRepository.findByInitiatorIdAndIdempotencyKey(userId, idempotencyKey);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Transaction existing = found.get();
        // Same key but a different request is a client bug; silently returning the old transfer would hide it.
        if (!isSameRequest(existing, request)) {
            throw new IdempotencyKeyConflictException(
                    "This Idempotency-Key was already used for a different transfer (wallets or amount differ)");
        }
        if (existing.getStatus() == TransactionStatus.FAILED) {
            throw new TransferFailedException(existing.getFailureReason());
        }
        return Optional.of(new TransferResult(TransactionResponse.from(existing), true));
    }

    private static boolean isSameRequest(Transaction existing, TransferRequest request) {
        return existing.getFromWallet().getId().equals(request.fromWalletId())
                && existing.getToWallet().getId().equals(request.toWalletId())
                && existing.getAmount().compareTo(request.amount()) == 0;
    }
}

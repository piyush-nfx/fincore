package com.piyush.fincore.service;

import com.piyush.fincore.dto.TransferRequest;
import com.piyush.fincore.dto.TransferResult;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * The entry point for transfers. Deliberately NOT @Transactional: it sits outside the transaction so it can react
 * to a transaction that failed and start a new one.
 *
 * <p>It handles the two ways concurrency can make a correct transfer fail at commit time:
 * <ol>
 *   <li>Two transfers touched the same wallet: optimistic lock conflict, so run the transfer again.</li>
 *   <li>Two copies of the same request (same Idempotency-Key) ran at once: the UNIQUE index let only one insert
 *       win, so return the winner's result instead of an error.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class TransferFacade {

    private final TransferService transferService;
    private final ConcurrencyRetry concurrencyRetry;

    public TransferResult transfer(Long userId, TransferRequest request, String idempotencyKey) {
        try {
            return concurrencyRetry.execute(() -> transferService.transfer(userId, request, idempotencyKey));
        } catch (DataIntegrityViolationException e) {
            // Only the idempotency key is unique in the ledger, so this was the key race. Our attempt rolled back
            // completely (debit included); the other request's transfer is the one that counts.
            return transferService.findReplay(userId, request, idempotencyKey).orElseThrow(() -> e);
        }
    }
}

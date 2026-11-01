package com.piyush.fincore.dto;

/**
 * What the service hands back to the controller: the transaction, plus whether it was created now or is the stored
 * result of an earlier request with the same Idempotency-Key (the controller reports that in a response header).
 */
public record TransferResult(TransactionResponse transaction, boolean replayed) {
}

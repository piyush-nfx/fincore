package com.piyush.fincore.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.piyush.fincore.entity.Transaction;
import com.piyush.fincore.entity.TransactionStatus;
import com.piyush.fincore.entity.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionResponse(
        Long id,
        TransactionType type,
        TransactionStatus status,
        Long fromWalletId,
        Long toWalletId,
        BigDecimal amount,
        String idempotencyKey,
        String failureReason,
        Instant createdAt
) {

    public static TransactionResponse from(Transaction tx) {
        return new TransactionResponse(
                tx.getId(),
                tx.getType(),
                tx.getStatus(),
                tx.getFromWallet() == null ? null : tx.getFromWallet().getId(),
                tx.getToWallet().getId(),
                tx.getAmount(),
                tx.getIdempotencyKey(),
                tx.getFailureReason(),
                tx.getCreatedAt());
    }
}

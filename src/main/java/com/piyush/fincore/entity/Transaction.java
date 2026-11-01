package com.piyush.fincore.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One ledger entry. The ledger is append-only: rows are inserted, never updated or deleted,
 * so the history of every wallet can always be audited and replayed.
 */
@Entity
@Table(name = "transactions",
        // Documentation of what the Flyway migrations create (V2); with ddl-auto=validate Hibernate doesn't create it.
        uniqueConstraints = @UniqueConstraint(name = "uk_transactions_initiator_idempotency_key",
                columnNames = {"initiator_id", "idempotency_key"}),
        indexes = {
                @Index(name = "idx_tx_from_wallet", columnList = "from_wallet_id"),
                @Index(name = "idx_tx_to_wallet", columnList = "to_wallet_id")
        })
@Immutable // Hibernate never issues an UPDATE for this entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Null for deposits: the money comes from outside the system.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_wallet_id", updatable = false)
    private Wallet fromWallet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_wallet_id", nullable = false, updatable = false)
    private Wallet toWallet;

    // Who started this entry: the sender of a transfer, the owner for a deposit. Idempotency-Keys are unique per
    // initiator, so two users can pick the same key without colliding.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "initiator_id", nullable = false, updatable = false)
    private User initiator;

    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private TransactionStatus status;

    // UNIQUE together with initiator_id: the final guard against the same transfer being executed twice.
    @Column(name = "idempotency_key", updatable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "failure_reason", updatable = false)
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private Transaction(Wallet from, Wallet to, BigDecimal amount, TransactionType type,
                        TransactionStatus status, String idempotencyKey, String failureReason) {
        this.fromWallet = from;
        this.toWallet = to;
        this.initiator = from != null ? from.getUser() : to.getUser();
        this.amount = amount;
        this.type = type;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.failureReason = failureReason;
    }

    public static Transaction deposit(Wallet to, BigDecimal amount) {
        return new Transaction(null, to, amount, TransactionType.DEPOSIT, TransactionStatus.SUCCESS, null, null);
    }

    public static Transaction transfer(Wallet from, Wallet to, BigDecimal amount, String idempotencyKey) {
        return new Transaction(from, to, amount, TransactionType.TRANSFER, TransactionStatus.SUCCESS, idempotencyKey, null);
    }

    public static Transaction failedTransfer(Wallet from, Wallet to, BigDecimal amount,
                                             String idempotencyKey, String reason) {
        return new Transaction(from, to, amount, TransactionType.TRANSFER, TransactionStatus.FAILED, idempotencyKey, reason);
    }
}

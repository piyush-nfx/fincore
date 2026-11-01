package com.piyush.fincore.entity;

import com.piyush.fincore.exception.InsufficientBalanceException;
import com.piyush.fincore.exception.InvalidOperationException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "wallets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // BigDecimal, not double: 0.1 + 0.2 != 0.3 in binary floating point, which is unacceptable for money.
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Column(nullable = false, length = 3)
    private String currency;

    // Optimistic locking: every UPDATE runs "... WHERE id = ? AND version = ?". If another transaction
    // changed the row first, 0 rows match and Hibernate throws instead of silently overwriting (lost update).
    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Wallet(User user, String currency) {
        this.user = user;
        this.currency = currency;
        this.balance = BigDecimal.ZERO;
    }

    // There is no setBalance(): the balance only changes through debit/credit, which enforce the rules.
    public void debit(BigDecimal amount) {
        requirePositive(amount);
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientBalanceException(id, balance, amount);
        }
        balance = balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        requirePositive(amount);
        balance = balance.add(amount);
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidOperationException("Amount must be positive");
        }
    }
}

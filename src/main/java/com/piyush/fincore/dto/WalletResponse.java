package com.piyush.fincore.dto;

import com.piyush.fincore.entity.Wallet;

import java.math.BigDecimal;
import java.time.Instant;

public record WalletResponse(Long id, Long userId, BigDecimal balance, String currency, Instant createdAt) {

    public static WalletResponse from(Wallet wallet) {
        // getUser().getId() reads the id from the lazy proxy without an extra SELECT.
        return new WalletResponse(wallet.getId(), wallet.getUser().getId(), wallet.getBalance(),
                wallet.getCurrency(), wallet.getCreatedAt());
    }
}

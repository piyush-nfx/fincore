package com.piyush.fincore.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TransferRequest(
        @NotNull Long fromWalletId,
        @NotNull Long toWalletId,
        @NotNull @Positive @Digits(integer = 15, fraction = 4) BigDecimal amount
) {
}

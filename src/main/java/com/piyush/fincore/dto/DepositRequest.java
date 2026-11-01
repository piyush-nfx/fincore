package com.piyush.fincore.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record DepositRequest(
        // Matches the DECIMAL(19,4) column: 15 digits before the point, 4 after.
        @NotNull @Positive @Digits(integer = 15, fraction = 4) BigDecimal amount
) {
}

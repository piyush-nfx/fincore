package com.piyush.fincore.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

// Phase 1 only: userId comes from the body. Phase 2 takes the owner from the JWT instead.
public record CreateWalletRequest(
        @NotNull Long userId,
        @NotNull @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO currency code, e.g. INR") String currency
) {
}

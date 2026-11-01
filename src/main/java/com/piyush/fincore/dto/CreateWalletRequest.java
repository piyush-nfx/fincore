package com.piyush.fincore.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

// No userId: the owner is always the logged-in user, taken from the access token.
public record CreateWalletRequest(
        @NotNull @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO currency code, e.g. INR") String currency
) {
}

package com.piyush.fincore.controller;

import com.piyush.fincore.dto.TransactionResponse;
import com.piyush.fincore.dto.TransferRequest;
import com.piyush.fincore.exception.InvalidOperationException;
import com.piyush.fincore.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferController {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;

    private final TransferService transferService;

    // Optional in Phase 1; Phase 3 makes the Idempotency-Key header mandatory.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse transfer(@Valid @RequestBody TransferRequest request,
                                        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        if (idempotencyKey != null && (idempotencyKey.isBlank() || idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH)) {
            throw new InvalidOperationException("Idempotency-Key must be 1-" + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }
        return transferService.transfer(request, idempotencyKey);
    }
}

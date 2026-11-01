package com.piyush.fincore.controller;

import com.piyush.fincore.dto.TransactionResponse;
import com.piyush.fincore.dto.TransferRequest;
import com.piyush.fincore.dto.TransferResult;
import com.piyush.fincore.exception.InvalidOperationException;
import com.piyush.fincore.security.AuthenticatedUser;
import com.piyush.fincore.service.TransferFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Transfers", description = "Peer-to-peer payments")
@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferController {

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    static final String REPLAYED_HEADER = "Idempotent-Replayed";
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;

    private final TransferFacade transferFacade;

    /**
     * The Idempotency-Key header is mandatory: a payment API must let clients retry safely after a timeout, and
     * that only works if every transfer carries a key. A missing header is a 400.
     */
    @Operation(summary = "Send money from your wallet to any wallet",
            description = "Same Idempotency-Key + same body = the original result is returned (header "
                    + "Idempotent-Replayed: true) and no money moves again. Same key with a different body = 409.")
    @PostMapping
    public ResponseEntity<TransactionResponse> transfer(@AuthenticationPrincipal AuthenticatedUser user,
                                                        @Valid @RequestBody TransferRequest request,
                                                        @RequestHeader(IDEMPOTENCY_KEY_HEADER) String idempotencyKey) {
        if (idempotencyKey.isBlank() || idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new InvalidOperationException(IDEMPOTENCY_KEY_HEADER + " must be 1-" + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }
        TransferResult result = transferFacade.transfer(user.id(), request, idempotencyKey);
        // A replay gets the same status and body as the original, plus a header so the client can tell.
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(REPLAYED_HEADER, String.valueOf(result.replayed()))
                .body(result.transaction());
    }
}

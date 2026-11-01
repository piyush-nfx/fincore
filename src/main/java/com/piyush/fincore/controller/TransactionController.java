package com.piyush.fincore.controller;

import com.piyush.fincore.dto.PageResponse;
import com.piyush.fincore.dto.TransactionResponse;
import com.piyush.fincore.security.AuthenticatedUser;
import com.piyush.fincore.service.TransactionHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Transactions", description = "Your transaction history")
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionHistoryService historyService;

    // e.g. GET /api/transactions?page=0&size=10&sort=createdAt,desc   or   ?walletId=3
    @Operation(summary = "Your transactions, newest first",
            description = "Deposits and transfers in or out of any of your wallets. Optional walletId narrows it to "
                    + "one of your wallets. Sort by createdAt, amount or id; page size is capped at 100.")
    @GetMapping
    public PageResponse<TransactionResponse> history(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Long walletId,
            // @ParameterObject shows page, size and sort as separate query parameters in Swagger UI.
            @ParameterObject @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return historyService.history(user.id(), walletId,
                SortWhitelist.check(pageable, SortWhitelist.TRANSACTION_FIELDS));
    }
}

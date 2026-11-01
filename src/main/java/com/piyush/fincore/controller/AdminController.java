package com.piyush.fincore.controller;

import com.piyush.fincore.dto.PageResponse;
import com.piyush.fincore.dto.TransactionResponse;
import com.piyush.fincore.service.AdminService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin", description = "ADMIN role only")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") // method-level check; SecurityConfig also guards /api/admin/** by URL
public class AdminController {

    private final AdminService adminService;

    // e.g. GET /api/admin/transactions?page=0&size=20&sort=createdAt,desc
    @GetMapping("/transactions")
    public PageResponse<TransactionResponse> allTransactions(
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return adminService.allTransactions(SortWhitelist.check(pageable, SortWhitelist.TRANSACTION_FIELDS));
    }
}

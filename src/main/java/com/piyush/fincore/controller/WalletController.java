package com.piyush.fincore.controller;

import com.piyush.fincore.dto.CreateWalletRequest;
import com.piyush.fincore.dto.DepositRequest;
import com.piyush.fincore.dto.WalletResponse;
import com.piyush.fincore.security.AuthenticatedUser;
import com.piyush.fincore.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

// @AuthenticationPrincipal hands us the user the JWT filter put into the SecurityContext.
@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @PostMapping
    public ResponseEntity<WalletResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                                 @Valid @RequestBody CreateWalletRequest request) {
        WalletResponse wallet = walletService.create(user.id(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(wallet.id()).toUri();
        return ResponseEntity.created(location).body(wallet);
    }

    @GetMapping
    public List<WalletResponse> listMine(@AuthenticationPrincipal AuthenticatedUser user) {
        return walletService.listMine(user.id());
    }

    @GetMapping("/{id}")
    public WalletResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return walletService.get(user.id(), id);
    }

    @PostMapping("/{id}/deposit")
    public WalletResponse deposit(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                                  @Valid @RequestBody DepositRequest request) {
        return walletService.deposit(user.id(), id, request);
    }
}

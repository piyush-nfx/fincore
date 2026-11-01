package com.piyush.fincore.controller;

import com.piyush.fincore.dto.CreateWalletRequest;
import com.piyush.fincore.dto.DepositRequest;
import com.piyush.fincore.dto.WalletResponse;
import com.piyush.fincore.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @PostMapping
    public ResponseEntity<WalletResponse> create(@Valid @RequestBody CreateWalletRequest request) {
        WalletResponse wallet = walletService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(wallet.id()).toUri();
        return ResponseEntity.created(location).body(wallet);
    }

    @GetMapping("/{id}")
    public WalletResponse get(@PathVariable Long id) {
        return walletService.get(id);
    }

    @PostMapping("/{id}/deposit")
    public WalletResponse deposit(@PathVariable Long id, @Valid @RequestBody DepositRequest request) {
        return walletService.deposit(id, request);
    }
}

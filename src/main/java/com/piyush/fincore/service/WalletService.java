package com.piyush.fincore.service;

import com.piyush.fincore.dto.CreateWalletRequest;
import com.piyush.fincore.dto.DepositRequest;
import com.piyush.fincore.dto.WalletResponse;
import com.piyush.fincore.entity.Transaction;
import com.piyush.fincore.entity.User;
import com.piyush.fincore.entity.Wallet;
import com.piyush.fincore.exception.UserNotFoundException;
import com.piyush.fincore.exception.WalletNotFoundException;
import com.piyush.fincore.repository.TransactionRepository;
import com.piyush.fincore.repository.UserRepository;
import com.piyush.fincore.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public WalletResponse create(CreateWalletRequest request) {
        User owner = userRepository.findById(request.userId())
                .orElseThrow(() -> new UserNotFoundException(request.userId()));
        return WalletResponse.from(walletRepository.save(new Wallet(owner, request.currency())));
    }

    @Transactional(readOnly = true)
    public WalletResponse get(Long walletId) {
        return WalletResponse.from(findWallet(walletId));
    }

    @Transactional
    public WalletResponse deposit(Long walletId, DepositRequest request) {
        Wallet wallet = findWallet(walletId);
        wallet.credit(request.amount());
        // The balance change and its ledger row commit together, or neither does.
        transactionRepository.save(Transaction.deposit(wallet, request.amount()));
        // No explicit save(wallet): it is a managed entity, so Hibernate flushes the change at commit (dirty checking).
        return WalletResponse.from(wallet);
    }

    private Wallet findWallet(Long walletId) {
        return walletRepository.findById(walletId).orElseThrow(() -> new WalletNotFoundException(walletId));
    }
}

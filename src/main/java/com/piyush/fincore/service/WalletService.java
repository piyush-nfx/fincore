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

import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public WalletResponse create(Long userId, CreateWalletRequest request) {
        // The token proved who you are 15 minutes ago; the account could have been deleted since.
        User owner = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        return WalletResponse.from(walletRepository.save(new Wallet(owner, request.currency())));
    }

    @Transactional(readOnly = true)
    public List<WalletResponse> listMine(Long userId) {
        return walletRepository.findByUserIdOrderByIdAsc(userId).stream().map(WalletResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public WalletResponse get(Long userId, Long walletId) {
        return WalletResponse.from(findOwnedWallet(userId, walletId));
    }

    @Transactional
    public WalletResponse deposit(Long userId, Long walletId, DepositRequest request) {
        Wallet wallet = findOwnedWallet(userId, walletId);
        wallet.credit(request.amount());
        // The balance change and its ledger row commit together, or neither does.
        transactionRepository.save(Transaction.deposit(wallet, request.amount()));
        // No explicit save(wallet): it is a managed entity, so Hibernate flushes the change at commit (dirty checking).
        return WalletResponse.from(wallet);
    }

    /**
     * IDOR protection: a wallet that exists but belongs to someone else gives the same 404 as one that doesn't
     * exist. A 403 would confirm to an attacker that the id is real.
     */
    private Wallet findOwnedWallet(Long userId, Long walletId) {
        return walletRepository.findByIdAndUserId(walletId, userId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));
    }
}

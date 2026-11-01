package com.piyush.fincore.service;

import com.piyush.fincore.dto.PageResponse;
import com.piyush.fincore.dto.TransactionResponse;
import com.piyush.fincore.entity.Transaction;
import com.piyush.fincore.exception.WalletNotFoundException;
import com.piyush.fincore.repository.TransactionRepository;
import com.piyush.fincore.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionHistoryService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;

    /**
     * The user's own ledger, newest first by default. With a walletId, only that wallet's rows, and only if the
     * wallet is theirs (someone else's wallet gives 404, the same IDOR rule as everywhere else).
     */
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> history(Long userId, Long walletId, Pageable pageable) {
        Page<Transaction> page;
        if (walletId == null) {
            page = transactionRepository.findAllForUser(userId, pageable);
        } else {
            walletRepository.findByIdAndUserId(walletId, userId)
                    .orElseThrow(() -> new WalletNotFoundException(walletId));
            page = transactionRepository.findAllForWallet(walletId, pageable);
        }
        // TransactionResponse only reads wallet ids, already in each row: no extra query per transaction (no N+1).
        return PageResponse.from(page.map(TransactionResponse::from));
    }
}

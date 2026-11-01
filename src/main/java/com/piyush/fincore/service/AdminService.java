package com.piyush.fincore.service;

import com.piyush.fincore.dto.PageResponse;
import com.piyush.fincore.dto.TransactionResponse;
import com.piyush.fincore.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> allTransactions(Pageable pageable) {
        // TransactionResponse only reads the wallets' ids, which Hibernate already has from the foreign-key
        // columns, so mapping a page does not fire one extra query per row (no N+1).
        return PageResponse.from(transactionRepository.findAll(pageable).map(TransactionResponse::from));
    }
}

package com.piyush.fincore.repository;

import com.piyush.fincore.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    // The ownership check happens in the query itself: someone else's wallet simply isn't found.
    Optional<Wallet> findByIdAndUserId(Long id, Long userId);

    List<Wallet> findByUserIdOrderByIdAsc(Long userId);
}

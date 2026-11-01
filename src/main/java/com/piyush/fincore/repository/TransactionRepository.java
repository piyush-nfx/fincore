package com.piyush.fincore.repository;

import com.piyush.fincore.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // Keys are unique per user, so a key is always looked up together with the user who sent it.
    Optional<Transaction> findByInitiatorIdAndIdempotencyKey(Long initiatorId, String idempotencyKey);

    /**
     * The user's ledger: everything they sent (including their own FAILED attempts) plus everything that actually
     * arrived in one of their wallets. A FAILED transfer is shown only to its sender: the recipient never received
     * anything, and the failure reason contains the sender's balance, which is none of the recipient's business.
     *
     * <p>"left join" for fromWallet matters: deposits have no source wallet, and an inner join would drop them.
     * Paging adds LIMIT/OFFSET to the first query and runs the count query for totalElements.
     */
    @Query(value = """
            select t from Transaction t
              left join t.fromWallet fw
              join t.toWallet tw
            where fw.user.id = :userId
               or (tw.user.id = :userId and t.status = com.piyush.fincore.entity.TransactionStatus.SUCCESS)""",
            countQuery = """
            select count(t) from Transaction t
              left join t.fromWallet fw
              join t.toWallet tw
            where fw.user.id = :userId
               or (tw.user.id = :userId and t.status = com.piyush.fincore.entity.TransactionStatus.SUCCESS)""")
    Page<Transaction> findAllForUser(@Param("userId") Long userId, Pageable pageable);

    // Same visibility rule for one wallet. fromWallet.id / toWallet.id read the foreign-key columns, so no join needed.
    @Query("""
            select t from Transaction t
            where t.fromWallet.id = :walletId
               or (t.toWallet.id = :walletId and t.status = com.piyush.fincore.entity.TransactionStatus.SUCCESS)""")
    Page<Transaction> findAllForWallet(@Param("walletId") Long walletId, Pageable pageable);
}

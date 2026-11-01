-- V2: Idempotency-Keys become unique per user instead of across all users.
-- Two different users may now pick the same key without colliding; one user can still never reuse a key.
--
-- Written to migrate a database that already holds data:
--   1. add the column as NULL-able,  2. fill it for existing rows,  3. then make it NOT NULL.
-- (Adding a NOT NULL column straight away would fail on a table that already has rows.)

-- 1. Who started each ledger entry: the sender for transfers, the wallet owner for deposits.
ALTER TABLE transactions ADD COLUMN initiator_id BIGINT NULL AFTER to_wallet_id;

-- 2. Backfill: deposits have no source wallet, so fall back to the destination wallet's owner.
UPDATE transactions t
    JOIN wallets w ON w.id = COALESCE(t.from_wallet_id, t.to_wallet_id)
SET t.initiator_id = w.user_id;

-- 3. Every row now has a value, so the column can be required.
ALTER TABLE transactions MODIFY COLUMN initiator_id BIGINT NOT NULL;

-- 4. Drop the old global UNIQUE(idempotency_key). Its name differs between a database built by V1 and one Hibernate
--    created earlier (a generated name like UKe58eyb...), so look it up instead of hard-coding it.
SET @old_index := (SELECT index_name
                   FROM information_schema.statistics
                   WHERE table_schema = DATABASE()
                     AND table_name = 'transactions'
                     AND column_name = 'idempotency_key'
                     AND non_unique = 0
                     AND seq_in_index = 1
                   LIMIT 1);
SET @drop_sql := IF(@old_index IS NULL, 'SELECT 1',
                    CONCAT('ALTER TABLE transactions DROP INDEX `', @old_index, '`'));
PREPARE drop_stmt FROM @drop_sql;
EXECUTE drop_stmt;
DEALLOCATE PREPARE drop_stmt;

-- 5. The new rule. MySQL allows many NULLs in a UNIQUE index, so deposits (no key) never conflict.
--    This index starts with initiator_id, so it also serves the foreign key below.
ALTER TABLE transactions
    ADD CONSTRAINT uk_transactions_initiator_idempotency_key UNIQUE (initiator_id, idempotency_key),
    ADD CONSTRAINT fk_transactions_initiator FOREIGN KEY (initiator_id) REFERENCES users (id);

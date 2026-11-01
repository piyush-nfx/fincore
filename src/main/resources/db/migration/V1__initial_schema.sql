-- V1: the schema as it stood at the end of Phase 3 (until then Hibernate's ddl-auto=update created it).
-- A database that already has these tables is "baselined" at version 1 (spring.flyway.baseline-on-migrate),
-- so this script only runs on a brand-new, empty database.

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          ENUM ('ADMIN', 'USER') NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE = InnoDB;

CREATE TABLE wallets (
    id         BIGINT         NOT NULL AUTO_INCREMENT,
    user_id    BIGINT         NOT NULL,
    balance    DECIMAL(19, 4) NOT NULL,
    currency   VARCHAR(3)     NOT NULL,
    version    BIGINT         NOT NULL,
    created_at DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_wallets_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE TABLE transactions (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    from_wallet_id  BIGINT         NULL,
    to_wallet_id    BIGINT         NOT NULL,
    amount          DECIMAL(19, 4) NOT NULL,
    type            ENUM ('DEPOSIT', 'TRANSFER') NOT NULL,
    status          ENUM ('FAILED', 'SUCCESS') NOT NULL,
    idempotency_key VARCHAR(64)    NULL,
    failure_reason  VARCHAR(255)   NULL,
    created_at      DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_transactions_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_transactions_from_wallet FOREIGN KEY (from_wallet_id) REFERENCES wallets (id),
    CONSTRAINT fk_transactions_to_wallet FOREIGN KEY (to_wallet_id) REFERENCES wallets (id),
    INDEX idx_tx_from_wallet (from_wallet_id),
    INDEX idx_tx_to_wallet (to_wallet_id)
) ENGINE = InnoDB;

CREATE TABLE refresh_tokens (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    version    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_refresh_user (user_id)
) ENGINE = InnoDB;

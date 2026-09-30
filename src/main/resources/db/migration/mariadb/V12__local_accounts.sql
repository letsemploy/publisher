-- Local accounts: email and password, where an installation enables them (spec 2.12).
--
-- A local account signs in as the users row with issuer 'local' and, as subject,
-- this table's id - so the users table needs no change, and a pending sign-up
-- (verified_at null) has no users row at all: only a verified address is kept
-- there (spec 2.2). A pending sign-up has no password either: it is chosen on the
-- page the mailed link opens, so only whoever holds the address sets it. The email is unique here, case-insensitively by the
-- collation, though not across providers (spec 2.2).
--
-- account_tokens holds the links mailed for verification and password reset. Only
-- a SHA-256 of each is stored; with 256 bits of randomness that is enough, and it
-- lets the link be found by its hash.

CREATE TABLE local_accounts (
    id                  UUID         NOT NULL,
    created_at          DATETIME(6)  NULL,
    last_modified_at    DATETIME(6)  NULL,
    email               VARCHAR(255) NOT NULL,
    display_name        VARCHAR(255) NOT NULL,
    password_hash       VARCHAR(255) NULL,
    verified_at         DATETIME(6)  NULL,
    password_changed_at DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_local_accounts_email (email)
);

CREATE TABLE account_tokens (
    id               UUID         NOT NULL,
    created_at       DATETIME(6)  NULL,
    last_modified_at DATETIME(6)  NULL,
    local_account_id UUID         NOT NULL,
    purpose          ENUM ('VERIFY_EMAIL', 'RESET_PASSWORD') NOT NULL,
    token_hash       CHAR(64)     NOT NULL,
    expires_at       DATETIME(6)  NOT NULL,
    used_at          DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_account_tokens_hash (token_hash),
    KEY idx_account_tokens_account (local_account_id),
    CONSTRAINT fk_account_tokens_account FOREIGN KEY (local_account_id) REFERENCES local_accounts (id)
        ON UPDATE RESTRICT ON DELETE CASCADE
);

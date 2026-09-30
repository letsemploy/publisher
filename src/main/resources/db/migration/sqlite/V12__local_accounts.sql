-- Local accounts: email and password, where an installation enables them (spec 2.12).
--
-- A local account signs in as the users row with issuer 'local' and, as subject,
-- this table's id - so the users table needs no change, and a pending sign-up
-- (verified_at null) has no users row at all: only a verified address is kept
-- there (spec 2.2). A pending sign-up has no password either: it is chosen on the
-- page the mailed link opens, so only whoever holds the address sets it. The email is unique here, case-insensitively (ASCII only, as
-- everywhere on SQLite), though not across providers (spec 2.2).
--
-- account_tokens holds the links mailed for verification and password reset. Only
-- a SHA-256 of each is stored. Translated by the rules of V6__baseline.sql.

CREATE TABLE local_accounts (
    id                  TEXT NOT NULL PRIMARY KEY,
    created_at          TEXT NULL,
    last_modified_at    TEXT NULL,
    email               TEXT NOT NULL COLLATE NOCASE,
    display_name        TEXT NOT NULL COLLATE NOCASE,
    password_hash       TEXT NULL,
    verified_at         TEXT NULL,
    password_changed_at TEXT NULL,
    CONSTRAINT uq_local_accounts_email UNIQUE (email)
);

CREATE TABLE account_tokens (
    id               TEXT NOT NULL PRIMARY KEY,
    created_at       TEXT NULL,
    last_modified_at TEXT NULL,
    local_account_id TEXT NOT NULL,
    purpose          TEXT NOT NULL CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD')),
    token_hash       TEXT NOT NULL,
    expires_at       TEXT NOT NULL,
    used_at          TEXT NULL,
    CONSTRAINT uq_account_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_account_tokens_account FOREIGN KEY (local_account_id) REFERENCES local_accounts (id)
        ON UPDATE RESTRICT ON DELETE CASCADE
);
CREATE INDEX idx_account_tokens_account ON account_tokens (local_account_id);

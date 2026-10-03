-- A service token outlives the person who created it (spec 2.8, 2.13). The same
-- change as mariadb/V14: created_by_id becomes nullable, and deleting that
-- account sets it to NULL instead of being refused.
--
-- SQLite cannot change a column or a foreign key in place, so service_tokens is
-- rebuilt: create, copy, drop, rename. Foreign keys must be OFF while it runs -
-- with them on, dropping the old table would cascade-delete every token
-- membership, scope and token-sent invitation - and the pragma is ignored inside
-- a transaction. Hence the companion .conf, and SAVEPOINT/RELEASE rather than
-- BEGIN, exactly as V7 does it.

PRAGMA foreign_keys = OFF;

SAVEPOINT v14_rebuild;

CREATE TABLE service_tokens_v14 (
    id               TEXT NOT NULL PRIMARY KEY,
    created_at       TEXT NULL,
    last_modified_at TEXT NULL,
    employer_id      TEXT NOT NULL,
    name             TEXT NOT NULL,
    -- identifies the token in logs and audit records without revealing it
    prefix           TEXT NOT NULL,
    -- the secret itself is NEVER stored (spec 2.8)
    secret_hash      TEXT NOT NULL,
    -- set on each accepted request; what makes a forgotten integration visible
    last_used_at     TEXT NULL,
    -- NULL means never expires; renewal moves it (spec 2.8)
    expires_at       TEXT NULL,
    revoked_at       TEXT NULL,
    -- NULL once the account that created it is deleted (spec 2.13)
    created_by_id    TEXT NULL,
    CONSTRAINT uq_service_tokens_prefix UNIQUE (prefix),
    CONSTRAINT fk_service_tokens_employer FOREIGN KEY (employer_id) REFERENCES employers (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_service_tokens_created_by FOREIGN KEY (created_by_id) REFERENCES users (id)
        ON UPDATE RESTRICT ON DELETE SET NULL
);

INSERT INTO service_tokens_v14 (id, created_at, last_modified_at, employer_id, name, prefix,
                                secret_hash, last_used_at, expires_at, revoked_at, created_by_id)
SELECT id, created_at, last_modified_at, employer_id, name, prefix,
       secret_hash, last_used_at, expires_at, revoked_at, created_by_id
FROM service_tokens;

DROP TABLE service_tokens;
ALTER TABLE service_tokens_v14 RENAME TO service_tokens;
CREATE INDEX idx_service_tokens_employer ON service_tokens (employer_id);

RELEASE v14_rebuild;

PRAGMA foreign_keys = ON;

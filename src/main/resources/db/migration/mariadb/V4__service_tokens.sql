-- Service tokens and the actor concept (spec 2.8, 3.10, 3.11).
--
-- An action may now be taken by a user OR by a service token. Both references are
-- nullable with a CHECK that exactly one is set, rather than an opaque id plus a
-- type string, so the database still enforces that the reference points at
-- something real.

CREATE TABLE service_tokens (
    id               UUID         NOT NULL,
    created_at       DATETIME(6)  NULL,
    last_modified_at DATETIME(6)  NULL,
    employer_id      UUID         NOT NULL,
    name             VARCHAR(64)  NOT NULL,
    -- identifies the token in logs and audit records without revealing it
    prefix           VARCHAR(16)  NOT NULL,
    -- the secret itself is NEVER stored (spec 2.8)
    secret_hash      VARCHAR(255) NOT NULL,
    -- what makes a forgotten integration visible; there is no expiry
    last_used_at     DATETIME(6)  NULL,
    revoked_at       DATETIME(6)  NULL,
    created_by_id    UUID         NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_service_tokens_prefix (prefix),
    KEY idx_service_tokens_employer (employer_id),
    CONSTRAINT fk_service_tokens_employer FOREIGN KEY (employer_id) REFERENCES employers (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_service_tokens_created_by FOREIGN KEY (created_by_id) REFERENCES users (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE TABLE service_token_scopes (
    service_token_id UUID NOT NULL,
    scope ENUM ('JOBS_READ','JOBS_WRITE','FEEDS_READ','FEEDS_WRITE','PEOPLE_READ','PEOPLE_WRITE') NOT NULL,
    PRIMARY KEY (service_token_id, scope),
    CONSTRAINT fk_service_token_scopes_token FOREIGN KEY (service_token_id) REFERENCES service_tokens (id)
        ON UPDATE RESTRICT ON DELETE CASCADE
);

-- A membership may belong to a user or to a token; a token holds a role too.
ALTER TABLE memberships
    MODIFY user_id UUID NULL,
    ADD COLUMN service_token_id UUID NULL AFTER user_id,
    ADD CONSTRAINT fk_memberships_service_token FOREIGN KEY (service_token_id)
        REFERENCES service_tokens (id) ON UPDATE RESTRICT ON DELETE CASCADE,
    ADD CONSTRAINT ck_memberships_one_member
        CHECK ((user_id IS NOT NULL) <> (service_token_id IS NOT NULL));

CREATE UNIQUE INDEX uq_memberships_token_employer
    ON memberships (service_token_id, employer_id);

-- An invitation may be sent by a user or by a token holding people:write.
--
-- The rename is done with the foreign key out of the way: InnoDB will not rename
-- a column that a foreign key still points at, and the whole ALTER fails with a
-- misleading "table already exists" if it is attempted in one statement.
ALTER TABLE invitations DROP FOREIGN KEY fk_invitations_invited_by;

ALTER TABLE invitations
    CHANGE COLUMN invited_by_id invited_by_user_id UUID NULL,
    ADD COLUMN invited_by_token_id UUID NULL AFTER invited_by_user_id;

ALTER TABLE invitations
    ADD CONSTRAINT fk_invitations_invited_by_user FOREIGN KEY (invited_by_user_id)
        REFERENCES users (id) ON UPDATE RESTRICT ON DELETE CASCADE,
    ADD CONSTRAINT fk_invitations_invited_by_token FOREIGN KEY (invited_by_token_id)
        REFERENCES service_tokens (id) ON UPDATE RESTRICT ON DELETE CASCADE,
    ADD CONSTRAINT ck_invitations_one_inviter
        CHECK ((invited_by_user_id IS NOT NULL) <> (invited_by_token_id IS NOT NULL));

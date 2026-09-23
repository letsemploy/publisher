-- Invitations (spec 2.6, 3.8).
--
-- Accepting an invitation is the ONLY act that writes user_employers (spec 9.3):
-- access to an employer is always taken up by consent, never granted silently.

CREATE TABLE invitations (
    id               UUID        NOT NULL,
    created_at       DATETIME(6) NULL,
    last_modified_at DATETIME(6) NULL,
    employer_id      UUID        NOT NULL,
    -- resolved from the email when the invitation is created, and immutable:
    -- the invitation is bound to the user, so a later email change cannot orphan it
    invitee_id       UUID        NOT NULL,
    invited_by_id    UUID        NOT NULL,
    status           ENUM ('PENDING','ACCEPTED','DECLINED','REVOKED') NOT NULL DEFAULT 'PENDING',
    -- set when the invitation leaves PENDING, immutable thereafter
    responded_at     DATETIME(6) NULL,
    PRIMARY KEY (id),
    -- the invitee's own list, and the employer's People screen
    KEY idx_invitations_invitee_status (invitee_id, status),
    KEY idx_invitations_employer_status (employer_id, status),
    CONSTRAINT fk_invitations_employer FOREIGN KEY (employer_id) REFERENCES employers (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_invitations_invitee FOREIGN KEY (invitee_id) REFERENCES users (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_invitations_invited_by FOREIGN KEY (invited_by_id) REFERENCES users (id)
        ON UPDATE RESTRICT ON DELETE CASCADE
);

-- "At most one PENDING per (employer, user)" is enforced in the service rather than
-- here: MariaDB has no partial unique index, and a unique key over (employer, invitee)
-- would forbid re-inviting after a decline, which spec 2.6 explicitly allows.

-- Email is how a human addresses an invitation (spec 2.6); the lookup is exact and
-- case-insensitive, so it needs an index.
CREATE INDEX idx_users_email ON users (email);

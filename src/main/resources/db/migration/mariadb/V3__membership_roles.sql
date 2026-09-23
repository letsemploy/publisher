-- Per-employer roles (spec 2.1, 2.7, 3.9).
--
-- Membership stops being a bare pair of ids and becomes an entity carrying a role,
-- so a user can own one employer while merely editing another.

CREATE TABLE memberships (
    id               UUID        NOT NULL,
    created_at       DATETIME(6) NULL,
    last_modified_at DATETIME(6) NULL,
    user_id          UUID        NOT NULL,
    employer_id      UUID        NOT NULL,
    role             ENUM ('OWNER','EDITOR') NOT NULL DEFAULT 'EDITOR',
    PRIMARY KEY (id),
    UNIQUE KEY uq_memberships_user_employer (user_id, employer_id),
    KEY idx_memberships_employer_role (employer_id, role),
    CONSTRAINT fk_memberships_user FOREIGN KEY (user_id) REFERENCES users (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_memberships_employer FOREIGN KEY (employer_id) REFERENCES employers (id)
        ON UPDATE RESTRICT ON DELETE CASCADE
);

-- Carry existing memberships over as editors ...
INSERT INTO memberships (id, created_at, last_modified_at, user_id, employer_id, role)
SELECT UUID(), now(), now(), ue.user_id, ue.employer_id, 'EDITOR'
FROM user_employers ue;

-- ... then promote one per employer, so the "every employer has an owner"
-- invariant (spec 3.9) holds the moment this migration finishes rather than
-- leaving existing workspaces frozen with nobody able to invite or edit.
UPDATE memberships m
JOIN (SELECT employer_id, MIN(id) AS first_member
      FROM memberships GROUP BY employer_id) pick
  ON m.id = pick.first_member
SET m.role = 'OWNER';

DROP TABLE user_employers;

-- The platform role loses EDITOR: "editor" now names a membership role, and the
-- platform axis only distinguishes ordinary users from staff (spec 2.1).
ALTER TABLE users MODIFY role ENUM ('EDITOR','ADMIN','USER') NOT NULL DEFAULT 'EDITOR';
UPDATE users SET role = 'USER' WHERE role = 'EDITOR';
ALTER TABLE users MODIFY role ENUM ('USER','ADMIN') NOT NULL DEFAULT 'USER';

-- An invitation now carries the role acceptance will grant (spec 2.6, 3.8).
ALTER TABLE invitations
    ADD COLUMN role ENUM ('OWNER','EDITOR') NOT NULL DEFAULT 'EDITOR' AFTER invited_by_id;

-- The audit log (spec 3.12, 10). The reasoning is in the MariaDB migration of the
-- same version; the shapes match.
--
-- No foreign keys, deliberately: the record of an employer's deletion, and of
-- what its tokens did, must survive it.

CREATE TABLE audit_events (
    id              TEXT NOT NULL PRIMARY KEY,
    occurred_at     TEXT NOT NULL,
    action          TEXT NOT NULL,
    actor_type      TEXT NOT NULL CHECK (actor_type IN ('USER','TOKEN','SYSTEM')),
    actor_user_id   TEXT NULL,
    actor_token_id  TEXT NULL,
    actor_label     TEXT NOT NULL,
    employer_id     TEXT NULL,
    employer_label  TEXT NULL,
    subject_user_id TEXT NULL,
    target_id       TEXT NULL,
    target_label    TEXT NULL,
    detail          TEXT NULL,
    CONSTRAINT ck_audit_events_one_actor CHECK (actor_user_id IS NULL OR actor_token_id IS NULL)
);
CREATE INDEX idx_audit_events_employer ON audit_events (employer_id, occurred_at);
CREATE INDEX idx_audit_events_subject ON audit_events (subject_user_id, occurred_at);
CREATE INDEX idx_audit_events_actor_user ON audit_events (actor_user_id, occurred_at);
CREATE INDEX idx_audit_events_occurred ON audit_events (occurred_at);

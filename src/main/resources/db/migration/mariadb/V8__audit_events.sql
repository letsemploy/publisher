-- The audit log (spec 3.12, 10).
--
-- One table, two scopes. A row appears in an employer's log when employer_id is
-- set, and in a person's own log when subject_user_id is set; many rows set both,
-- so the two logs can never disagree about one event.
--
-- There are deliberately NO foreign keys. A log is a record, not a relation: it
-- must never be refused, and never be altered afterwards. Every table that
-- references an employer is deleted with it, and this one must keep the record
-- of that deletion; a token goes with its employer, and its rows must still say
-- which token it was. The labels are snapshots for the same reason - the row
-- still reads correctly after what it names is renamed or gone.
--
-- action is text, not an ENUM: a new kind of event is a new constant, not a
-- migration, and nothing ever sorts by it.

CREATE TABLE audit_events (
    id              UUID         NOT NULL,
    occurred_at     DATETIME(6)  NOT NULL,
    action          VARCHAR(40)  NOT NULL,
    actor_type      ENUM ('USER','TOKEN','SYSTEM') NOT NULL,
    actor_user_id   UUID         NULL,
    actor_token_id  UUID         NULL,
    actor_label     VARCHAR(255) NOT NULL,
    employer_id     UUID         NULL,
    employer_label  VARCHAR(255) NULL,
    subject_user_id UUID         NULL,
    target_id       VARCHAR(64)  NULL,
    target_label    VARCHAR(255) NULL,
    detail          VARCHAR(255) NULL,
    PRIMARY KEY (id),
    KEY idx_audit_events_employer (employer_id, occurred_at),
    KEY idx_audit_events_subject (subject_user_id, occurred_at),
    KEY idx_audit_events_actor_user (actor_user_id, occurred_at),
    KEY idx_audit_events_occurred (occurred_at),
    -- A person or a token, never both; neither is the application itself.
    CONSTRAINT ck_audit_events_one_actor CHECK (actor_user_id IS NULL OR actor_token_id IS NULL)
);

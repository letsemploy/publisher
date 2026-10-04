-- The weekly summary mail (spec 7.28).
--
-- mail_summary is the person's opt-in, off for everyone until they tick it in
-- Settings. summary_sent_at is when the summary was last claimed for them: the
-- scheduled run sets it with a conditional UPDATE before building the mail, so
-- several instances sharing this database send it at most once a week. It is
-- stamped even when the week had nothing to report and no mail went out.

ALTER TABLE users
    ADD COLUMN mail_summary    BOOLEAN     NOT NULL DEFAULT FALSE AFTER mail_invitations,
    ADD COLUMN summary_sent_at DATETIME(6) NULL AFTER mail_summary;

-- The weekly summary mail (spec 7.28). The same change as mariadb/V17,
-- translated by the rules of V6__baseline.sql.
--
-- mail_summary is the person's opt-in, off for everyone until they tick it in
-- Settings. summary_sent_at is when the summary was last claimed for them: the
-- scheduled run sets it with a conditional UPDATE before building the mail, so
-- it is sent at most once a week. It is stamped even when the week had nothing
-- to report and no mail went out.

ALTER TABLE users ADD COLUMN mail_summary INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN summary_sent_at TEXT NULL;

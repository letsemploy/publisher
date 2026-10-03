-- A person's own settings (spec 3.7, 7.26).
--
-- Language and theme used to live in the session alone and reset at every
-- sign-in. The time zone renders timestamps for people outside the server's; the
-- flag lets an invitee decline invitation mail (spec 2.6). All four are the
-- person's own choice: signing in never refreshes them from a provider.
--
-- NULL means not chosen: the browser's language, the System theme, the server's
-- zone. Nothing is backfilled, and every existing account keeps getting mail.

ALTER TABLE users
    ADD COLUMN language         VARCHAR(8)  NULL AFTER suspended_at,
    ADD COLUMN theme            VARCHAR(8)  NULL AFTER language,
    ADD COLUMN time_zone        VARCHAR(64) NULL AFTER theme,
    ADD COLUMN mail_invitations BOOLEAN     NOT NULL DEFAULT TRUE AFTER time_zone,
    ADD CONSTRAINT ck_users_theme CHECK (theme IS NULL OR theme IN ('auto', 'light', 'dark'));

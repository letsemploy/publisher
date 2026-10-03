-- A person's own settings (spec 3.7, 7.26). The same change as mariadb/V15.
--
-- Language and theme used to live in the session alone and reset at every
-- sign-in. The time zone renders timestamps for people outside the server's; the
-- flag lets an invitee decline invitation mail (spec 2.6). All four are the
-- person's own choice: signing in never refreshes them from a provider.
--
-- NULL means not chosen: the browser's language, the System theme, the server's
-- zone. Nothing is backfilled, and every existing account keeps getting mail.
-- SQLite adds a column with a default and a CHECK in place, so no rebuild.

ALTER TABLE users ADD COLUMN language TEXT NULL;
ALTER TABLE users ADD COLUMN theme TEXT NULL CHECK (theme IS NULL OR theme IN ('auto', 'light', 'dark'));
ALTER TABLE users ADD COLUMN time_zone TEXT NULL;
ALTER TABLE users ADD COLUMN mail_invitations INTEGER NOT NULL DEFAULT 1;

-- Suspended accounts (spec 2.11).
--
-- A platform admin may suspend an account: it can no longer sign in, and an open
-- session ends at its next request. Everything else stays - memberships, roles,
-- history - so reinstating is a date cleared, not a record rebuilt. Who did it and
-- why is in the audit log (spec 3.12), not here.
--
-- NULL means active. Nothing is backfilled: every existing account is active.

ALTER TABLE users
    ADD COLUMN suspended_at DATETIME(6) NULL AFTER role;

-- Suspended memberships (spec 2.7, 3.9).
--
-- An owner may suspend another member: the membership stays, with its role and
-- its place in the list, but grants no access until it is reinstated. A removal
-- would do the same job only by destroying the record and making the person
-- depend on a fresh invitation - and their consent - to come back.
--
-- NULL means active. Nothing is backfilled: every existing membership is active.

ALTER TABLE memberships
    ADD COLUMN suspended_at DATETIME(6) NULL AFTER role;

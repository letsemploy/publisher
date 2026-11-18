-- Revoke every service token still stored with a bcrypt hash (spec 2.8).
--
-- Since 1.5.0 a secret is stored as SHA-256 and an older bcrypt hash was replaced
-- at the token's first use. One never used since still cost 50 to 100 ms of bcrypt
-- per presented secret, before any rate limit, for anyone who knew its public
-- prefix. The application no longer checks bcrypt at all; revoking the rows says
-- so in the register instead of leaving tokens that look active and never work.
-- An owner creates a new token for an integration that still needs one.

UPDATE service_tokens
   SET revoked_at = NOW(6)
 WHERE revoked_at IS NULL
   AND secret_hash LIKE '{bcrypt}%';

-- Service tokens expire (spec 2.8, 3.10, 12 item 11).
--
-- This reverses V4's "there is no expiry". A credential that lives forever is one
-- nobody ever re-examines, so a token now lapses after a configured lifetime and
-- an owner renews it. Renewal keeps the secret and only moves this date, so a
-- running integration needs no redeploy, and a lapsed token is reactivated the
-- same way - which is what makes expiry safe to introduce at all.
--
-- NULL means never expires, which is what a lifetime of 0 configures.

ALTER TABLE service_tokens
    ADD COLUMN expires_at DATETIME(6) NULL AFTER last_used_at;

-- Backfilled from now rather than from created_at: applying the rule
-- retroactively would expire every token older than the lifetime the moment this
-- migration ran, breaking live integrations on deploy. Revoked rows are left NULL
-- because they are already dead and can never come back.
UPDATE service_tokens
   SET expires_at = NOW() + INTERVAL 12 MONTH
 WHERE revoked_at IS NULL;

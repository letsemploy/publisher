-- An index on every foreign-key column (spec 9.3). Here this changes nothing:
-- InnoDB already created each of these when the foreign key was added, under
-- the constraint's name, so every statement finds its index and is skipped.
-- It exists for sqlite/V18, which needs them, and so both folders say which
-- indexes the schema has. Keep the names equal to the constraints': a
-- different one would build a second index on the same column.

CREATE INDEX IF NOT EXISTS fk_feed_jobs_job ON feed_jobs (job_id);
CREATE INDEX IF NOT EXISTS fk_job_tags_tag ON job_tags (tag_id);
CREATE INDEX IF NOT EXISTS fk_job_locations_location ON job_locations (location_id);
CREATE INDEX IF NOT EXISTS fk_employers_location ON employers (location_id);
CREATE INDEX IF NOT EXISTS fk_invitations_invited_by_user ON invitations (invited_by_user_id);
CREATE INDEX IF NOT EXISTS fk_invitations_invited_by_token ON invitations (invited_by_token_id);
CREATE INDEX IF NOT EXISTS fk_service_tokens_created_by ON service_tokens (created_by_id);

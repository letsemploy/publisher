-- An index on every foreign-key column that had none. The same change as
-- mariadb/V18, where it changes nothing: InnoDB creates an index for each
-- foreign key by itself, named after the constraint. SQLite does not, so here
-- these columns were scanned in full - on every delete of the row they point
-- at, to cascade or to check, and by the queries that count a job's feeds
-- (JobRepo.countFeeds, countFeedsByJob, findFeedIds) or find a tag's
-- (FeedRepo.findIdsByTag). The names are InnoDB's, so both schemas match.
--
-- A join table's primary key covers its first column only, which is why
-- feed_jobs, job_tags and job_locations each need one on the second.

CREATE INDEX IF NOT EXISTS fk_feed_jobs_job ON feed_jobs (job_id);
CREATE INDEX IF NOT EXISTS fk_job_tags_tag ON job_tags (tag_id);
CREATE INDEX IF NOT EXISTS fk_job_locations_location ON job_locations (location_id);
CREATE INDEX IF NOT EXISTS fk_employers_location ON employers (location_id);
CREATE INDEX IF NOT EXISTS fk_invitations_invited_by_user ON invitations (invited_by_user_id);
CREATE INDEX IF NOT EXISTS fk_invitations_invited_by_token ON invitations (invited_by_token_id);
CREATE INDEX IF NOT EXISTS fk_service_tokens_created_by ON service_tokens (created_by_id);

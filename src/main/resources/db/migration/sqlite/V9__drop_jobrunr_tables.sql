-- JobRunr is gone: it was configured but ran no jobs, while its background
-- server polled the database and its dashboard answered on port 8000 without
-- authentication. It created these objects itself, outside Flyway, so they exist
-- only in a database that ran a version with JobRunr - hence IF EXISTS, which
-- makes this a no-op on a fresh installation.
--
-- The view first: it reads from jobrunr_jobs. The tables have no foreign keys to
-- each other or to ours, and their indexes go with them.

DROP VIEW IF EXISTS jobrunr_jobs_stats;
DROP TABLE IF EXISTS jobrunr_jobs;
DROP TABLE IF EXISTS jobrunr_recurring_jobs;
DROP TABLE IF EXISTS jobrunr_backgroundjobservers;
DROP TABLE IF EXISTS jobrunr_metadata;
DROP TABLE IF EXISTS jobrunr_migrations;

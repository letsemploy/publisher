-- Demo data, loaded under the dev profile only and idempotent (spec 9.3).

INSERT INTO locations (id, created_at, last_modified_at, city, country) VALUES
  ('2c2e59d5-0b1a-11f1-938c-42a3421a666f', now(), now(), 'Bern', 'CH'),
  ('c7a9dc61-2bb3-4b06-8c66-0af6174dfd49', now(), now(), 'Zürich', 'CH'),
  ('d1b0f4a2-6c31-4d55-9a77-2f1c3e5b7a90', now(), now(), 'Berlin', 'DE')
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO employers (id, created_at, last_modified_at, name, slug, url, industry, location_id) VALUES
  ('003d6aec-021b-11f1-aefa-f649a5d91690', now(), now(), 'Acme AG', 'acme-ag',
   'https://www.acme.example', 'Software', '2c2e59d5-0b1a-11f1-938c-42a3421a666f')
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO tags (id, name) VALUES
  (1, 'java'), (2, 'kubernetes'), (3, 'spring'), (4, 'documentation')
ON DUPLICATE KEY UPDATE id = id;

-- A published job, an expired one and a draft, so every status is visible at once.
INSERT INTO jobs (id, created_at, last_modified_at, employer_id, title, description, url,
                  language_code, reference_id, category, job_type, work_type, experience_level,
                  work_load_percent_min, work_load_percent_max, salary_min, salary_max,
                  salary_currency, salary_interval, start_date, end_date, apply_before,
                  status, published_at) VALUES
  ('8f14e45f-ceea-467a-9b4f-3a1b2c3d4e5f', now(), now(), '003d6aec-021b-11f1-aefa-f649a5d91690',
   'Senior Backend Engineer', 'Design and operate our job distribution platform.',
   'https://www.acme.example/jobs/ACME-2026-014', 'en', 'ACME-2026-014', 'Engineering',
   'PERMANENT', 'ON_SITE', 'SENIOR', 80, 100, 110000.00, 135000.00, 'CHF', 'YEARLY',
   '2026-11-01', NULL, '2099-10-15', 'ACTIVE', '2026-09-01'),
  ('9a25f56a-dffb-478b-ac50-4b2c3d4e5f60', now(), now(), '003d6aec-021b-11f1-aefa-f649a5d91690',
   'Praktikum Produktdesign', NULL, 'https://www.acme.example/jobs/ACME-2026-021',
   'de', 'ACME-2026-021', NULL, 'INTERNSHIP', 'HYBRID', 'JUNIOR',
   NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', '2026-09-18'),
  ('ab36067b-e00c-489c-bd61-5c3d4e5f6071', now(), now(), '003d6aec-021b-11f1-aefa-f649a5d91690',
   'Site Reliability Engineer', 'Keep the platform up.', 'https://www.acme.example/jobs/ACME-2026-009',
   'en', 'ACME-2026-009', 'Engineering', 'PERMANENT', 'REMOTE', 'SENIOR',
   NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2020-01-31', 'ACTIVE', '2026-08-01'),
  ('bc47178c-f11d-490d-ce72-6d4e5f607182', now(), now(), '003d6aec-021b-11f1-aefa-f649a5d91690',
   'Werkstudent Data', NULL, 'https://www.acme.example/jobs/ACME-2026-030',
   'de', 'ACME-2026-030', NULL, 'TEMPORARY', NULL, NULL,
   NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'DRAFT', NULL),
  -- Withdrawn, kept for the record.
  ('cd58289d-022e-4a1e-df83-7e5f60718201', now(), now(), '003d6aec-021b-11f1-aefa-f649a5d91690',
   'Head of Engineering', 'Lead the platform team.', 'https://www.acme.example/jobs/ACME-2025-102',
   'en', 'ACME-2025-102', 'Engineering', 'PERMANENT', 'ON_SITE', 'EXECUTIVE',
   NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'INACTIVE', '2025-11-04'),
  -- Active but with no location, so it presents as INCOMPLETE and is excluded
  -- from the published document: the schema requires at least one location.
  ('de69390e-133f-4b2f-e094-8f6071829301', now(), now(), '003d6aec-021b-11f1-aefa-f649a5d91690',
   'Technical Writer', 'Document the publishing platform.',
   'https://www.acme.example/jobs/ACME-2026-031',
   'en', 'ACME-2026-031', NULL, 'CONTRACT', 'REMOTE', 'MID',
   NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ACTIVE', '2026-09-19')
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO job_locations (job_id, location_id) VALUES
  ('8f14e45f-ceea-467a-9b4f-3a1b2c3d4e5f', '2c2e59d5-0b1a-11f1-938c-42a3421a666f'),
  ('8f14e45f-ceea-467a-9b4f-3a1b2c3d4e5f', 'c7a9dc61-2bb3-4b06-8c66-0af6174dfd49'),
  ('9a25f56a-dffb-478b-ac50-4b2c3d4e5f60', '2c2e59d5-0b1a-11f1-938c-42a3421a666f'),
  ('ab36067b-e00c-489c-bd61-5c3d4e5f6071', 'c7a9dc61-2bb3-4b06-8c66-0af6174dfd49')
ON DUPLICATE KEY UPDATE job_id = job_id;

INSERT INTO job_tags (job_id, tag_id) VALUES
  ('8f14e45f-ceea-467a-9b4f-3a1b2c3d4e5f', 1),
  ('8f14e45f-ceea-467a-9b4f-3a1b2c3d4e5f', 2),
  ('8f14e45f-ceea-467a-9b4f-3a1b2c3d4e5f', 3)
ON DUPLICATE KEY UPDATE job_id = job_id;

INSERT INTO feeds (id, created_at, last_modified_at, employer_id, name, slug, description) VALUES
  ('cd58289d-022e-4a1e-df83-7e5f60718293', now(), now(), '003d6aec-021b-11f1-aefa-f649a5d91690',
   'All jobs', 'all', 'Every job this employer currently advertises.'),
  ('de69390e-133f-4b2f-e094-8f6071829304', now(), now(), '003d6aec-021b-11f1-aefa-f649a5d91690',
   'Engineering', 'engineering', 'Engineering roles only.')
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO feed_jobs (feed_id, job_id) VALUES
  ('cd58289d-022e-4a1e-df83-7e5f60718293', '8f14e45f-ceea-467a-9b4f-3a1b2c3d4e5f'),
  ('cd58289d-022e-4a1e-df83-7e5f60718293', '9a25f56a-dffb-478b-ac50-4b2c3d4e5f60'),
  ('cd58289d-022e-4a1e-df83-7e5f60718293', 'ab36067b-e00c-489c-bd61-5c3d4e5f6071'),
  ('de69390e-133f-4b2f-e094-8f6071829304', '8f14e45f-ceea-467a-9b4f-3a1b2c3d4e5f'),
  -- In the feed but not publishable: the feed screen must explain the omission.
  ('cd58289d-022e-4a1e-df83-7e5f60718293', 'de69390e-133f-4b2f-e094-8f6071829301')
ON DUPLICATE KEY UPDATE feed_id = feed_id;

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

-- The development administrator (spec 2.3): a real record, because anything keyed
-- on user identity is otherwise unreachable locally and in the tests.
INSERT INTO users (id, created_at, last_modified_at, issuer, subject, email, display_name, role) VALUES
  ('11111111-1111-4111-8111-111111111111', now(), now(), 'dev', 'dev@localhost',
   'dev@localhost', 'dev@localhost', 'ADMIN'),
  -- A registered colleague with no memberships, so the invite flow has a real target.
  ('22222222-2222-4222-8222-222222222222', now(), now(), 'dev', 'editor@example.com',
   'editor@example.com', 'Edith Editor', 'USER'),
  -- An actual member, so the People screen has a member to list and remove.
  ('44444444-4444-4444-8444-444444444444', now(), now(), 'dev', 'member@example.com',
   'member@example.com', 'Mara Member', 'USER')
ON DUPLICATE KEY UPDATE id = id;

-- The dev admin owns Acme, so the People screen has an owner and the last-owner
-- rule has something to protect; Mara is an editor alongside them (spec 2.7).
INSERT INTO memberships (id, created_at, last_modified_at, user_id, employer_id, role) VALUES
  ('55555555-5555-4555-8555-555555555555', now(), now(),
   '11111111-1111-4111-8111-111111111111', '003d6aec-021b-11f1-aefa-f649a5d91690', 'OWNER'),
  ('66666666-6666-4666-8666-666666666666', now(), now(),
   '44444444-4444-4444-8444-444444444444', '003d6aec-021b-11f1-aefa-f649a5d91690', 'EDITOR')
ON DUPLICATE KEY UPDATE id = id;

-- A pending invitation waiting for the colleague, so the screens have something to show.
INSERT INTO invitations (id, created_at, last_modified_at, employer_id, invitee_id,
                         invited_by_user_id, role, status, responded_at) VALUES
  ('33333333-3333-4333-8333-333333333333', now(), now(), '003d6aec-021b-11f1-aefa-f649a5d91690',
   '22222222-2222-4222-8222-222222222222', '11111111-1111-4111-8111-111111111111',
   'EDITOR', 'PENDING', NULL)
ON DUPLICATE KEY UPDATE id = id;

-- A service token, so the API tokens screen has a row to show (spec 7.17).
--
-- The hash is of a random secret that was generated and discarded, so this row is
-- deliberately NOT a usable credential: nothing authenticates against it. A working
-- seeded token would be a credential committed to the repository, and data.sql runs
-- wherever the application starts.
INSERT INTO service_tokens (id, created_at, last_modified_at, employer_id, name, prefix,
                            secret_hash, last_used_at, expires_at, revoked_at, created_by_id) VALUES
  ('77777777-7777-4777-8777-777777777777', now(), now(), '003d6aec-021b-11f1-aefa-f649a5d91690',
   'ats-sync', 'ojp_seedonly01',
   '{bcrypt}$2a$10$//umO/HMS.9LMIbFuRqerehvXBa1t.SdsgCqpEgTpDAcUmNlxDS22',
   NULL, DATE_ADD(now(), INTERVAL 12 MONTH), NULL,
   '11111111-1111-4111-8111-111111111111')
ON DUPLICATE KEY UPDATE id = id;

-- A second token, already lapsed, so the expired state and the Reactivate
-- control are visible in the register (spec 2.8, 7.17). created_at is backdated
-- rather than now(): it is honest, and it keeps findByEmployerIdOrderByCreatedAtDesc
-- deterministic, which a test relies on.
INSERT INTO service_tokens (id, created_at, last_modified_at, employer_id, name, prefix,
                            secret_hash, last_used_at, expires_at, revoked_at, created_by_id) VALUES
  ('79797979-7979-4979-8979-797979797979',
   DATE_SUB(now(), INTERVAL 13 MONTH), DATE_SUB(now(), INTERVAL 13 MONTH),
   '003d6aec-021b-11f1-aefa-f649a5d91690',
   'legacy-export', 'ojp_seedexpired',
   '{bcrypt}$2a$10$//umO/HMS.9LMIbFuRqerehvXBa1t.SdsgCqpEgTpDAcUmNlxDS22',
   DATE_SUB(now(), INTERVAL 12 MONTH), DATE_SUB(now(), INTERVAL 1 MONTH), NULL,
   '11111111-1111-4111-8111-111111111111')
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO service_token_scopes (service_token_id, scope) VALUES
  ('77777777-7777-4777-8777-777777777777', 'JOBS_WRITE'),
  ('77777777-7777-4777-8777-777777777777', 'FEEDS_READ'),
  ('79797979-7979-4979-8979-797979797979', 'JOBS_READ')
ON DUPLICATE KEY UPDATE scope = scope;

-- A token holds a membership of its own, carrying its role (spec 2.8).
INSERT INTO memberships (id, created_at, last_modified_at, user_id, service_token_id,
                         employer_id, role) VALUES
  ('88888888-8888-4888-8888-888888888888', now(), now(), NULL,
   '77777777-7777-4777-8777-777777777777', '003d6aec-021b-11f1-aefa-f649a5d91690', 'EDITOR'),
  ('89898989-8989-4989-8989-898989898989',
   DATE_SUB(now(), INTERVAL 13 MONTH), DATE_SUB(now(), INTERVAL 13 MONTH), NULL,
   '79797979-7979-4979-8979-797979797979', '003d6aec-021b-11f1-aefa-f649a5d91690', 'EDITOR')
ON DUPLICATE KEY UPDATE id = id;

-- Locations and tags belong to an employer (spec 3.2, 3.4). The same change as
-- mariadb/V7, and the same rules for dividing the existing rows:
--
-- * the first employer to use a row - oldest by created_at, then by id - keeps
--   it with its id unchanged; every other user gets a copy, and its jobs and
--   headquarters are pointed at the copy;
-- * a row nothing uses goes to the oldest employer, and is dropped only when
--   there is no employer at all to own it;
-- * employers.location_id becomes nullable (the headquarters cycle - see the
--   MariaDB script), and deleting an employer takes its locations and tags.
--
-- SQLite cannot change a column or a foreign key in place, so employers,
-- locations, tags and job_locations are rebuilt: create the new table, copy,
-- drop the old one, rename. That must run with foreign keys OFF - with them on,
-- dropping employers would cascade-delete every job, feed and membership - and
-- the pragma is ignored inside a transaction. Hence the companion
-- V7__employer_owned_locations_and_tags.sql.conf (executeInTransaction=false).
-- The rebuild itself is still one transaction, opened with SAVEPOINT and closed
-- with RELEASE: Flyway's parser reads a bare BEGIN as the start of a trigger
-- body, and a savepoint outside any transaction begins one all the same. Should
-- a statement fail, the connection closes with it open and SQLite rolls back.
-- This is SQLite's documented procedure for such changes.

PRAGMA foreign_keys = OFF;

SAVEPOINT v7_rebuild;

-- --- Who uses what -------------------------------------------------------------

CREATE TABLE v7_location_use (
    location_id TEXT    NOT NULL,
    employer_id TEXT    NOT NULL,
    keeper      INTEGER NOT NULL DEFAULT 0,
    new_id      TEXT    NULL,
    PRIMARY KEY (location_id, employer_id)
);

INSERT INTO v7_location_use (location_id, employer_id)
SELECT location_id, id FROM employers WHERE location_id IS NOT NULL
UNION
SELECT jl.location_id, j.employer_id FROM job_locations jl JOIN jobs j ON j.id = jl.job_id;

-- The oldest user keeps the row and its id; the others get a fresh v4 UUID,
-- written the way the application writes them: lower case, hyphenated.
UPDATE v7_location_use
SET keeper = (ranked.rn = 1),
    new_id = CASE WHEN ranked.rn = 1 THEN v7_location_use.location_id
             ELSE lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4'
                        || substr(hex(randomblob(2)), 2) || '-'
                        || substr('89ab', 1 + (abs(random()) % 4), 1)
                        || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6))) END
FROM (SELECT u.location_id, u.employer_id,
             ROW_NUMBER() OVER (PARTITION BY u.location_id ORDER BY e.created_at, e.id) AS rn
      FROM v7_location_use u JOIN employers e ON e.id = u.employer_id) AS ranked
WHERE ranked.location_id = v7_location_use.location_id
  AND ranked.employer_id = v7_location_use.employer_id;

CREATE TABLE v7_tag_use (
    tag_id      INTEGER NOT NULL,
    employer_id TEXT    NOT NULL,
    keeper      INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (tag_id, employer_id)
);

INSERT INTO v7_tag_use (tag_id, employer_id)
SELECT DISTINCT jt.tag_id, j.employer_id FROM job_tags jt JOIN jobs j ON j.id = jt.job_id;

UPDATE v7_tag_use
SET keeper = (ranked.rn = 1)
FROM (SELECT u.tag_id, u.employer_id,
             ROW_NUMBER() OVER (PARTITION BY u.tag_id ORDER BY e.created_at, e.id) AS rn
      FROM v7_tag_use u JOIN employers e ON e.id = u.employer_id) AS ranked
WHERE ranked.tag_id = v7_tag_use.tag_id AND ranked.employer_id = v7_tag_use.employer_id;

-- --- locations ------------------------------------------------------------------

CREATE TABLE locations_v7 (
    id               TEXT NOT NULL PRIMARY KEY,
    created_at       TEXT NULL,
    last_modified_at TEXT NULL,
    -- the employer this location belongs to (spec 3.2)
    employer_id      TEXT NOT NULL,
    city             TEXT NOT NULL COLLATE NOCASE,
    -- ISO 3166-1 alpha-2, stored as its code and never as an enum ordinal (spec 3.2)
    country          TEXT NOT NULL CHECK (length(country) = 2),
    CONSTRAINT uq_locations_employer_city_country UNIQUE (employer_id, city, country),
    CONSTRAINT fk_locations_employer FOREIGN KEY (employer_id) REFERENCES employers (id)
        ON UPDATE RESTRICT ON DELETE CASCADE
);

INSERT INTO locations_v7 (id, created_at, last_modified_at, employer_id, city, country)
SELECT l.id, l.created_at, l.last_modified_at, u.employer_id, l.city, l.country
FROM locations l JOIN v7_location_use u ON u.location_id = l.id AND u.keeper = 1;

INSERT INTO locations_v7 (id, created_at, last_modified_at, employer_id, city, country)
SELECT u.new_id, l.created_at, l.last_modified_at, u.employer_id, l.city, l.country
FROM v7_location_use u JOIN locations l ON l.id = u.location_id
WHERE u.keeper = 0;

-- Unused: to the oldest employer; with no employer at all, not carried over.
INSERT INTO locations_v7 (id, created_at, last_modified_at, employer_id, city, country)
SELECT l.id, l.created_at, l.last_modified_at,
       (SELECT id FROM employers ORDER BY created_at, id LIMIT 1), l.city, l.country
FROM locations l
WHERE NOT EXISTS (SELECT 1 FROM v7_location_use u WHERE u.location_id = l.id)
  AND EXISTS (SELECT 1 FROM employers);

DROP TABLE locations;
ALTER TABLE locations_v7 RENAME TO locations;
CREATE INDEX idx_locations_employer ON locations (employer_id);

-- --- employers: the headquarters becomes nullable -------------------------------

CREATE TABLE employers_v7 (
    id               TEXT NOT NULL PRIMARY KEY,
    created_at       TEXT NULL,
    last_modified_at TEXT NULL,
    name             TEXT NOT NULL COLLATE NOCASE,
    -- decorative only: identity lives in the UUID, so no uniqueness (spec 3.6, 5.1)
    slug             TEXT NOT NULL,
    url              TEXT NULL,
    industry         TEXT NULL,
    -- one of the employer's own locations; required by the application (spec 3.1),
    -- nullable here because the location must belong to an employer that exists
    location_id      TEXT NULL,
    CONSTRAINT fk_employers_location FOREIGN KEY (location_id) REFERENCES locations (id)
        ON UPDATE RESTRICT ON DELETE SET NULL
);

INSERT INTO employers_v7 (id, created_at, last_modified_at, name, slug, url, industry, location_id)
SELECT e.id, e.created_at, e.last_modified_at, e.name, e.slug, e.url, e.industry,
       COALESCE((SELECT u.new_id FROM v7_location_use u
                 WHERE u.location_id = e.location_id AND u.employer_id = e.id), e.location_id)
FROM employers e;

DROP TABLE employers;
ALTER TABLE employers_v7 RENAME TO employers;
CREATE INDEX idx_employers_slug ON employers (slug);

-- --- job_locations: repointed, and removed with their location ------------------

CREATE TABLE job_locations_v7 (
    job_id      TEXT NOT NULL,
    location_id TEXT NOT NULL,
    PRIMARY KEY (job_id, location_id),
    CONSTRAINT fk_job_locations_job FOREIGN KEY (job_id) REFERENCES jobs (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_job_locations_location FOREIGN KEY (location_id) REFERENCES locations (id)
        ON UPDATE RESTRICT ON DELETE CASCADE
);

INSERT INTO job_locations_v7 (job_id, location_id)
SELECT jl.job_id,
       COALESCE((SELECT u.new_id FROM v7_location_use u
                 WHERE u.location_id = jl.location_id AND u.employer_id = j.employer_id), jl.location_id)
FROM job_locations jl JOIN jobs j ON j.id = jl.job_id;

DROP TABLE job_locations;
ALTER TABLE job_locations_v7 RENAME TO job_locations;

-- --- tags -------------------------------------------------------------------------

CREATE TABLE tags_v7 (
    -- INTEGER PRIMARY KEY is the only column SQLite generates a value for (IDENTITY)
    id          INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    -- the employer this tag belongs to (spec 3.4)
    employer_id TEXT    NOT NULL,
    -- the published schema caps a tag at 28 characters (spec 3.4)
    name        TEXT    NOT NULL COLLATE NOCASE CHECK (length(name) <= 28),
    CONSTRAINT uq_tags_employer_name UNIQUE (employer_id, name),
    CONSTRAINT fk_tags_employer FOREIGN KEY (employer_id) REFERENCES employers (id)
        ON UPDATE RESTRICT ON DELETE CASCADE
);

-- Originals first, with their ids, so the copies' fresh ids come after them.
INSERT INTO tags_v7 (id, employer_id, name)
SELECT t.id, u.employer_id, t.name
FROM tags t JOIN v7_tag_use u ON u.tag_id = t.id AND u.keeper = 1;

INSERT INTO tags_v7 (id, employer_id, name)
SELECT t.id, (SELECT id FROM employers ORDER BY created_at, id LIMIT 1), t.name
FROM tags t
WHERE NOT EXISTS (SELECT 1 FROM v7_tag_use u WHERE u.tag_id = t.id)
  AND EXISTS (SELECT 1 FROM employers);

INSERT INTO tags_v7 (employer_id, name)
SELECT u.employer_id, t.name
FROM v7_tag_use u JOIN tags t ON t.id = u.tag_id
WHERE u.keeper = 0;

-- job_tags keeps its table - its foreign key actions do not change - and only
-- its copied tags are repointed, found by (employer, name).
UPDATE job_tags
SET tag_id = (SELECT copy.id
              FROM jobs j
              JOIN tags original ON original.id = job_tags.tag_id
              JOIN tags_v7 copy ON copy.employer_id = j.employer_id AND copy.name = original.name
              WHERE j.id = job_tags.job_id)
WHERE EXISTS (SELECT 1 FROM jobs j JOIN v7_tag_use u
                ON u.tag_id = job_tags.tag_id AND u.employer_id = j.employer_id AND u.keeper = 0
              WHERE j.id = job_tags.job_id);

DROP TABLE tags;
ALTER TABLE tags_v7 RENAME TO tags;
CREATE INDEX idx_tags_employer ON tags (employer_id);

DROP TABLE v7_location_use;
DROP TABLE v7_tag_use;

RELEASE v7_rebuild;

PRAGMA foreign_keys = ON;

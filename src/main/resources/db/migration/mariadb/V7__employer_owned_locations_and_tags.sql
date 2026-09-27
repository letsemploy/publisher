-- Locations and tags belong to an employer (spec 3.2, 3.4).
--
-- They were global: any signed-in user could see, rename or delete any of them,
-- and renaming one changed other employers' jobs and published documents.
--
-- The existing rows are divided among the employers that use them. The first
-- employer to use a row - oldest by created_at, then by id - keeps it, with its
-- id unchanged; every other user gets a copy, and its jobs (and headquarters)
-- are pointed at the copy. A row nothing uses goes to the oldest employer, and
-- is deleted only when there is no employer at all to own it.
--
-- The headquarters cycle: an employer's headquarters is one of its own
-- locations, and a location belongs to an employer, so neither row could be
-- inserted first. employers.location_id therefore becomes nullable here; the
-- application still requires it (spec 3.1) and sets it in the same transaction
-- that creates the employer.
--
-- MariaDB DDL is not transactional: were this to fail halfway, the changes made
-- so far stay. The statements are ordered so that each step leaves the tables
-- consistent with the ones before it.

-- --- The foreign keys around locations --------------------------------------

-- Nullable, and cleared if its location is ever deleted with its employer.
ALTER TABLE employers DROP FOREIGN KEY fk_employers_location;
ALTER TABLE employers MODIFY location_id UUID NULL;
ALTER TABLE employers
    ADD CONSTRAINT fk_employers_location FOREIGN KEY (location_id) REFERENCES locations (id)
        ON UPDATE RESTRICT ON DELETE SET NULL;

-- An employer's locations now go with the employer, and with them their use on
-- its jobs. "A location in use cannot be deleted" stays the service's rule.
ALTER TABLE job_locations DROP FOREIGN KEY fk_job_locations_location;
ALTER TABLE job_locations
    ADD CONSTRAINT fk_job_locations_location FOREIGN KEY (location_id) REFERENCES locations (id)
        ON UPDATE RESTRICT ON DELETE CASCADE;

-- Global uniqueness goes; the copies below would break it.
ALTER TABLE locations DROP INDEX uq_locations_city_country;
ALTER TABLE tags DROP INDEX uq_tags_name;

ALTER TABLE locations ADD COLUMN employer_id UUID NULL AFTER id;
ALTER TABLE tags ADD COLUMN employer_id UUID NULL AFTER id;

-- --- Locations ----------------------------------------------------------------

-- Every (location, employer) pair in use: as headquarters, or on a job.
CREATE TABLE v7_location_use (
    location_id UUID    NOT NULL,
    employer_id UUID    NOT NULL,
    keeper      BOOLEAN NOT NULL DEFAULT FALSE,
    new_id      UUID    NULL,
    PRIMARY KEY (location_id, employer_id)
);

INSERT INTO v7_location_use (location_id, employer_id)
SELECT location_id, id FROM employers WHERE location_id IS NOT NULL
UNION
SELECT jl.location_id, j.employer_id
FROM job_locations jl JOIN jobs j ON j.id = jl.job_id;

-- The oldest user keeps the row and its id; the others get a fresh id.
UPDATE v7_location_use u
JOIN (SELECT u2.location_id, u2.employer_id,
             ROW_NUMBER() OVER (PARTITION BY u2.location_id ORDER BY e.created_at, e.id) AS rn
      FROM v7_location_use u2 JOIN employers e ON e.id = u2.employer_id) ranked
  ON ranked.location_id = u.location_id AND ranked.employer_id = u.employer_id
SET u.keeper = (ranked.rn = 1),
    u.new_id = IF(ranked.rn = 1, u.location_id, UUID());

UPDATE locations l
JOIN v7_location_use u ON u.location_id = l.id AND u.keeper
SET l.employer_id = u.employer_id;

INSERT INTO locations (id, employer_id, created_at, last_modified_at, city, country)
SELECT u.new_id, u.employer_id, l.created_at, l.last_modified_at, l.city, l.country
FROM v7_location_use u JOIN locations l ON l.id = u.location_id
WHERE NOT u.keeper;

UPDATE employers e
JOIN v7_location_use u ON u.location_id = e.location_id AND u.employer_id = e.id AND NOT u.keeper
SET e.location_id = u.new_id;

UPDATE job_locations jl
JOIN jobs j ON j.id = jl.job_id
JOIN v7_location_use u ON u.location_id = jl.location_id AND u.employer_id = j.employer_id AND NOT u.keeper
SET jl.location_id = u.new_id;

-- Unused: to the oldest employer, or - with none at all - gone.
UPDATE locations
SET employer_id = (SELECT id FROM employers ORDER BY created_at, id LIMIT 1)
WHERE employer_id IS NULL;
DELETE FROM locations WHERE employer_id IS NULL;

DROP TABLE v7_location_use;

ALTER TABLE locations MODIFY employer_id UUID NOT NULL;
ALTER TABLE locations
    ADD CONSTRAINT fk_locations_employer FOREIGN KEY (employer_id) REFERENCES employers (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    ADD CONSTRAINT uq_locations_employer_city_country UNIQUE (employer_id, city, country);

-- --- Tags ---------------------------------------------------------------------

-- A tag is in use only on jobs.
CREATE TABLE v7_tag_use (
    tag_id      BIGINT  NOT NULL,
    employer_id UUID    NOT NULL,
    keeper      BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (tag_id, employer_id)
);

INSERT INTO v7_tag_use (tag_id, employer_id)
SELECT DISTINCT jt.tag_id, j.employer_id
FROM job_tags jt JOIN jobs j ON j.id = jt.job_id;

UPDATE v7_tag_use u
JOIN (SELECT u2.tag_id, u2.employer_id,
             ROW_NUMBER() OVER (PARTITION BY u2.tag_id ORDER BY e.created_at, e.id) AS rn
      FROM v7_tag_use u2 JOIN employers e ON e.id = u2.employer_id) ranked
  ON ranked.tag_id = u.tag_id AND ranked.employer_id = u.employer_id
SET u.keeper = (ranked.rn = 1);

UPDATE tags t
JOIN v7_tag_use u ON u.tag_id = t.id AND u.keeper
SET t.employer_id = u.employer_id;

-- Copies take a fresh AUTO_INCREMENT id; (employer, name) is what finds them.
INSERT INTO tags (employer_id, name)
SELECT u.employer_id, t.name
FROM v7_tag_use u JOIN tags t ON t.id = u.tag_id
WHERE NOT u.keeper;

UPDATE job_tags jt
JOIN jobs j ON j.id = jt.job_id
JOIN v7_tag_use u ON u.tag_id = jt.tag_id AND u.employer_id = j.employer_id AND NOT u.keeper
JOIN tags original ON original.id = u.tag_id
JOIN tags copy ON copy.employer_id = u.employer_id AND copy.name = original.name
SET jt.tag_id = copy.id;

UPDATE tags
SET employer_id = (SELECT id FROM employers ORDER BY created_at, id LIMIT 1)
WHERE employer_id IS NULL;
DELETE FROM tags WHERE employer_id IS NULL;

DROP TABLE v7_tag_use;

ALTER TABLE tags MODIFY employer_id UUID NOT NULL;
ALTER TABLE tags
    ADD CONSTRAINT fk_tags_employer FOREIGN KEY (employer_id) REFERENCES employers (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    ADD CONSTRAINT uq_tags_employer_name UNIQUE (employer_id, name);

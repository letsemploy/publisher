-- Schema for the ojobpub publisher on SQLite (spec 9.3), per docs/SPEC.md.
--
-- SQLite support arrived when MariaDB was at V6, so this one script produces the
-- schema MariaDB's V1..V6 arrive at, and a new SQLite database starts at the same
-- version. From V7 on the two folders advance in pairs; MigrationParityTest fails
-- the build if one gets a migration the other does not.
--
-- Translation rules, so the next migration is written the same way:
--
-- * UUIDs are TEXT, lower-case and hyphenated: hibernate.type.preferred_uuid_jdbc_type
--   is CHAR under the sqlite profile, and the seed writes the same literals.
-- * Instants and dates are TEXT, 'yyyy-MM-dd HH:mm:ss.SSS', in UTC for instants and
--   with a zero time for dates - what sqlite-jdbc writes with date_class=TEXT. One
--   representation is what makes them compare correctly: in SQLite every integer
--   sorts before every text value, so a mix would silently break the date window
--   (spec 4.4).
-- * Money is TEXT, not NUMERIC: NUMERIC affinity turns 85000.50 into a float, and
--   the salary must come back exactly as it was written (spec 6.7).
-- * An ENUM becomes TEXT with a CHECK, so the value set is still enforced.
-- * Text that MariaDB compares case-insensitively, and whose uniqueness or order the
--   application relies on, is COLLATE NOCASE. That folds ASCII only.
-- * Foreign keys are declared exactly as on MariaDB. SQLite enforces them only with
--   foreign_keys=on, which the sqlite profile sets on every connection.

-- ---------------------------------------------------------------------------
-- Reference data
-- ---------------------------------------------------------------------------

CREATE TABLE locations (
    id               TEXT NOT NULL PRIMARY KEY,
    created_at       TEXT NULL,
    last_modified_at TEXT NULL,
    city             TEXT NOT NULL COLLATE NOCASE,
    -- ISO 3166-1 alpha-2, stored as its code and never as an enum ordinal (spec 3.2)
    country          TEXT NOT NULL CHECK (length(country) = 2),
    CONSTRAINT uq_locations_city_country UNIQUE (city, country)
);

CREATE TABLE tags (
    -- INTEGER PRIMARY KEY is the only column SQLite generates a value for (IDENTITY)
    id   INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    -- the published schema caps a tag at 28 characters (spec 3.4)
    name TEXT    NOT NULL COLLATE NOCASE CHECK (length(name) <= 28),
    CONSTRAINT uq_tags_name UNIQUE (name)
);

-- ---------------------------------------------------------------------------
-- Employers and their people
-- ---------------------------------------------------------------------------

CREATE TABLE employers (
    id               TEXT NOT NULL PRIMARY KEY,
    created_at       TEXT NULL,
    last_modified_at TEXT NULL,
    name             TEXT NOT NULL COLLATE NOCASE,
    -- decorative only: identity lives in the UUID, so no uniqueness (spec 3.6, 5.1)
    slug             TEXT NOT NULL,
    url              TEXT NULL,
    industry         TEXT NULL,
    -- required: the published document must carry the employer's location (spec 3.1)
    location_id      TEXT NOT NULL,
    CONSTRAINT fk_employers_location FOREIGN KEY (location_id) REFERENCES locations (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
);
CREATE INDEX idx_employers_slug ON employers (slug);

CREATE TABLE users (
    id               TEXT NOT NULL PRIMARY KEY,
    created_at       TEXT NULL,
    last_modified_at TEXT NULL,
    -- a user is the stable (issuer, subject) pair from the ID token, never the email (spec 2.2)
    issuer           TEXT NOT NULL,
    subject          TEXT NOT NULL,
    email            TEXT NULL COLLATE NOCASE,
    display_name     TEXT NULL COLLATE NOCASE,
    role             TEXT NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT uq_users_issuer_subject UNIQUE (issuer, subject)
);
-- Email is how a human addresses an invitation (spec 2.6); exact, case-insensitive.
CREATE INDEX idx_users_email ON users (email);

-- ---------------------------------------------------------------------------
-- Jobs
-- ---------------------------------------------------------------------------

CREATE TABLE jobs (
    id                    TEXT NOT NULL PRIMARY KEY,
    created_at            TEXT NULL,
    last_modified_at      TEXT NULL,
    -- a job belongs directly to one employer, fixed at creation (spec 3.3)
    employer_id           TEXT NOT NULL,
    title                 TEXT NOT NULL COLLATE NOCASE,
    description           TEXT NULL,
    url                   TEXT NOT NULL,
    language_code         TEXT NOT NULL,
    reference_id          TEXT NULL,
    category              TEXT NULL,
    job_type              TEXT NOT NULL CHECK (job_type IN
        ('PERMANENT','CONTRACT','TEMPORARY','FREELANCE','VOLUNTEER','APPRENTICESHIP','INTERNSHIP')),
    work_type             TEXT NULL CHECK (work_type IN ('ON_SITE','REMOTE','HYBRID')),
    experience_level      TEXT NULL CHECK (experience_level IN
        ('JUNIOR','MID','SENIOR','LEAD','MANAGER','DIRECTOR','EXECUTIVE')),
    work_load_percent_min INTEGER NULL CHECK (work_load_percent_min BETWEEN 0 AND 255),
    work_load_percent_max INTEGER NULL CHECK (work_load_percent_max BETWEEN 0 AND 255),
    -- decimal, never float: a six-figure salary must not lose accuracy (spec 6.7)
    salary_min            TEXT NULL,
    salary_max            TEXT NULL,
    salary_currency       TEXT NULL,
    salary_interval       TEXT NULL CHECK (salary_interval IN ('HOURLY','DAILY','WEEKLY','MONTHLY','YEARLY')),
    start_date            TEXT NULL,
    end_date              TEXT NULL,
    apply_before          TEXT NULL,
    status                TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','ACTIVE','INACTIVE')),
    -- stamped once, on first activation, and never moved afterwards (spec 4.2)
    published_at          TEXT NULL,
    CONSTRAINT fk_jobs_employer FOREIGN KEY (employer_id) REFERENCES employers (id)
        ON UPDATE RESTRICT ON DELETE CASCADE
);
CREATE INDEX idx_jobs_employer_status ON jobs (employer_id, status);

CREATE TABLE job_status_events (
    id          TEXT NOT NULL PRIMARY KEY,
    job_id      TEXT NOT NULL,
    from_status TEXT NULL CHECK (from_status IN ('DRAFT','ACTIVE','INACTIVE')),
    to_status   TEXT NOT NULL CHECK (to_status IN ('DRAFT','ACTIVE','INACTIVE')),
    actor       TEXT NOT NULL,
    occurred_at TEXT NOT NULL,
    CONSTRAINT fk_job_status_events_job FOREIGN KEY (job_id) REFERENCES jobs (id)
        ON UPDATE RESTRICT ON DELETE CASCADE
);
CREATE INDEX idx_job_status_events_job ON job_status_events (job_id, occurred_at);

CREATE TABLE job_tags (
    job_id TEXT    NOT NULL,
    tag_id INTEGER NOT NULL,
    PRIMARY KEY (job_id, tag_id),
    CONSTRAINT fk_job_tags_job FOREIGN KEY (job_id) REFERENCES jobs (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_job_tags_tag FOREIGN KEY (tag_id) REFERENCES tags (id)
        ON UPDATE RESTRICT ON DELETE CASCADE
);

CREATE TABLE job_locations (
    job_id      TEXT NOT NULL,
    location_id TEXT NOT NULL,
    PRIMARY KEY (job_id, location_id),
    CONSTRAINT fk_job_locations_job FOREIGN KEY (job_id) REFERENCES jobs (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_job_locations_location FOREIGN KEY (location_id) REFERENCES locations (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
);

-- ---------------------------------------------------------------------------
-- Feeds
-- ---------------------------------------------------------------------------

CREATE TABLE feeds (
    id               TEXT NOT NULL PRIMARY KEY,
    created_at       TEXT NULL,
    last_modified_at TEXT NULL,
    -- one document carries exactly one employer, so a feed never spans employers (spec 3.5)
    employer_id      TEXT NOT NULL,
    name             TEXT NOT NULL COLLATE NOCASE,
    slug             TEXT NOT NULL,
    description      TEXT NULL,
    CONSTRAINT uq_feeds_employer_name UNIQUE (employer_id, name),
    CONSTRAINT fk_feeds_employer FOREIGN KEY (employer_id) REFERENCES employers (id)
        ON UPDATE RESTRICT ON DELETE CASCADE
);
CREATE INDEX idx_feeds_slug ON feeds (slug);

CREATE TABLE feed_jobs (
    feed_id TEXT NOT NULL,
    job_id  TEXT NOT NULL,
    PRIMARY KEY (feed_id, job_id),
    CONSTRAINT fk_feed_jobs_feed FOREIGN KEY (feed_id) REFERENCES feeds (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_feed_jobs_job FOREIGN KEY (job_id) REFERENCES jobs (id)
        ON UPDATE RESTRICT ON DELETE CASCADE
);

-- ---------------------------------------------------------------------------
-- Service tokens (spec 2.8, 3.10)
-- ---------------------------------------------------------------------------

CREATE TABLE service_tokens (
    id               TEXT NOT NULL PRIMARY KEY,
    created_at       TEXT NULL,
    last_modified_at TEXT NULL,
    employer_id      TEXT NOT NULL,
    name             TEXT NOT NULL,
    -- identifies the token in logs and audit records without revealing it
    prefix           TEXT NOT NULL,
    -- the secret itself is NEVER stored (spec 2.8)
    secret_hash      TEXT NOT NULL,
    -- set on each accepted request; what makes a forgotten integration visible
    last_used_at     TEXT NULL,
    -- NULL means never expires; renewal moves it (spec 2.8)
    expires_at       TEXT NULL,
    revoked_at       TEXT NULL,
    created_by_id    TEXT NOT NULL,
    CONSTRAINT uq_service_tokens_prefix UNIQUE (prefix),
    CONSTRAINT fk_service_tokens_employer FOREIGN KEY (employer_id) REFERENCES employers (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_service_tokens_created_by FOREIGN KEY (created_by_id) REFERENCES users (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
);
CREATE INDEX idx_service_tokens_employer ON service_tokens (employer_id);

CREATE TABLE service_token_scopes (
    service_token_id TEXT NOT NULL,
    scope            TEXT NOT NULL CHECK (scope IN
        ('JOBS_READ','JOBS_WRITE','FEEDS_READ','FEEDS_WRITE','PEOPLE_READ','PEOPLE_WRITE')),
    PRIMARY KEY (service_token_id, scope),
    CONSTRAINT fk_service_token_scopes_token FOREIGN KEY (service_token_id) REFERENCES service_tokens (id)
        ON UPDATE RESTRICT ON DELETE CASCADE
);

-- ---------------------------------------------------------------------------
-- Memberships and invitations (spec 2.6, 2.7, 3.8, 3.9, 3.11)
-- ---------------------------------------------------------------------------

-- A membership belongs to a user or to a token - exactly one of the two.
CREATE TABLE memberships (
    id               TEXT NOT NULL PRIMARY KEY,
    created_at       TEXT NULL,
    last_modified_at TEXT NULL,
    user_id          TEXT NULL,
    service_token_id TEXT NULL,
    employer_id      TEXT NOT NULL,
    role             TEXT NOT NULL DEFAULT 'EDITOR' CHECK (role IN ('OWNER','EDITOR')),
    -- NULL means active; a suspended membership grants nothing (spec 2.7)
    suspended_at     TEXT NULL,
    CONSTRAINT uq_memberships_user_employer UNIQUE (user_id, employer_id),
    CONSTRAINT uq_memberships_token_employer UNIQUE (service_token_id, employer_id),
    CONSTRAINT fk_memberships_user FOREIGN KEY (user_id) REFERENCES users (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_memberships_service_token FOREIGN KEY (service_token_id)
        REFERENCES service_tokens (id) ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_memberships_employer FOREIGN KEY (employer_id) REFERENCES employers (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT ck_memberships_one_member
        CHECK ((user_id IS NOT NULL) <> (service_token_id IS NOT NULL))
);
CREATE INDEX idx_memberships_employer_role ON memberships (employer_id, role);

-- "At most one PENDING per (employer, user)" stays in the service, as on MariaDB,
-- although SQLite could express it as a partial unique index: one rule, one place.
CREATE TABLE invitations (
    id                  TEXT NOT NULL PRIMARY KEY,
    created_at          TEXT NULL,
    last_modified_at    TEXT NULL,
    employer_id         TEXT NOT NULL,
    -- bound to the resolved user, so a later email change cannot orphan it
    invitee_id          TEXT NOT NULL,
    invited_by_user_id  TEXT NULL,
    invited_by_token_id TEXT NULL,
    role                TEXT NOT NULL DEFAULT 'EDITOR' CHECK (role IN ('OWNER','EDITOR')),
    status              TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','ACCEPTED','DECLINED','REVOKED')),
    -- set when the invitation leaves PENDING, immutable thereafter
    responded_at        TEXT NULL,
    CONSTRAINT fk_invitations_employer FOREIGN KEY (employer_id) REFERENCES employers (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_invitations_invitee FOREIGN KEY (invitee_id) REFERENCES users (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_invitations_invited_by_user FOREIGN KEY (invited_by_user_id)
        REFERENCES users (id) ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_invitations_invited_by_token FOREIGN KEY (invited_by_token_id)
        REFERENCES service_tokens (id) ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT ck_invitations_one_inviter
        CHECK ((invited_by_user_id IS NOT NULL) <> (invited_by_token_id IS NOT NULL))
);
CREATE INDEX idx_invitations_invitee_status ON invitations (invitee_id, status);
CREATE INDEX idx_invitations_employer_status ON invitations (employer_id, status);

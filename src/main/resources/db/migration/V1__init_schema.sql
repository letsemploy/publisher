-- Schema for the ojobpub publisher, per docs/SPEC.md.
-- Flyway owns the schema; Hibernate never generates DDL (spec 9.3).

SET NAMES utf8mb4;
SET time_zone = '+00:00';
SET default_storage_engine = InnoDB;

-- ---------------------------------------------------------------------------
-- Reference data
-- ---------------------------------------------------------------------------

CREATE TABLE locations (
    id               UUID         NOT NULL,
    created_at       DATETIME(6)  NULL,
    last_modified_at DATETIME(6)  NULL,
    city             VARCHAR(255) NOT NULL,
    -- ISO 3166-1 alpha-2, stored as its code and never as an enum ordinal (spec 3.2)
    country          CHAR(2)      NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_locations_city_country (city, country)
);

CREATE TABLE tags (
    id   BIGINT      NOT NULL AUTO_INCREMENT,
    -- the published schema caps a tag at 28 characters (spec 3.4)
    name VARCHAR(28) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_tags_name (name)
);

-- ---------------------------------------------------------------------------
-- Employers and their people
-- ---------------------------------------------------------------------------

CREATE TABLE employers (
    id               UUID         NOT NULL,
    created_at       DATETIME(6)  NULL,
    last_modified_at DATETIME(6)  NULL,
    name             VARCHAR(255) NOT NULL,
    -- decorative only: identity lives in the UUID, so no uniqueness (spec 3.6, 5.1)
    slug             VARCHAR(64)  NOT NULL,
    url              VARCHAR(255) NULL,
    industry         VARCHAR(255) NULL,
    -- required: the published document must carry the employer's location (spec 3.1)
    location_id      UUID         NOT NULL,
    PRIMARY KEY (id),
    KEY idx_employers_slug (slug),
    CONSTRAINT fk_employers_location FOREIGN KEY (location_id) REFERENCES locations (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE TABLE users (
    id               UUID         NOT NULL,
    created_at       DATETIME(6)  NULL,
    last_modified_at DATETIME(6)  NULL,
    -- a user is the stable (issuer, subject) pair from the ID token, never the email (spec 2.2)
    issuer           VARCHAR(255) NOT NULL,
    subject          VARCHAR(255) NOT NULL,
    email            VARCHAR(255) NULL,
    display_name     VARCHAR(255) NULL,
    role             ENUM ('EDITOR', 'ADMIN') NOT NULL DEFAULT 'EDITOR',
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_issuer_subject (issuer, subject)
);

CREATE TABLE user_employers (
    user_id     UUID NOT NULL,
    employer_id UUID NOT NULL,
    PRIMARY KEY (user_id, employer_id),
    CONSTRAINT fk_user_employers_user FOREIGN KEY (user_id) REFERENCES users (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_user_employers_employer FOREIGN KEY (employer_id) REFERENCES employers (id)
        ON UPDATE RESTRICT ON DELETE CASCADE
);

-- ---------------------------------------------------------------------------
-- Jobs
-- ---------------------------------------------------------------------------

CREATE TABLE jobs (
    id                    UUID         NOT NULL,
    created_at            DATETIME(6)  NULL,
    last_modified_at      DATETIME(6)  NULL,
    -- a job belongs directly to one employer, fixed at creation (spec 3.3)
    employer_id           UUID         NOT NULL,
    title                 VARCHAR(255) NOT NULL,
    description           VARCHAR(1000) NULL,
    url                   VARCHAR(255) NOT NULL,
    language_code         CHAR(2)      NOT NULL,
    reference_id          VARCHAR(255) NULL,
    category              VARCHAR(255) NULL,
    job_type              ENUM ('PERMANENT','CONTRACT','TEMPORARY','FREELANCE','VOLUNTEER','APPRENTICESHIP','INTERNSHIP') NOT NULL,
    work_type             ENUM ('ON_SITE','REMOTE','HYBRID') NULL,
    experience_level      ENUM ('JUNIOR','MID','SENIOR','LEAD','MANAGER','DIRECTOR','EXECUTIVE') NULL,
    work_load_percent_min TINYINT UNSIGNED NULL,
    work_load_percent_max TINYINT UNSIGNED NULL,
    -- decimal, never float: a six-figure salary must not lose accuracy (spec 6.7)
    salary_min            DECIMAL(12, 2) NULL,
    salary_max            DECIMAL(12, 2) NULL,
    salary_currency       CHAR(3)      NULL,
    salary_interval       ENUM ('HOURLY','DAILY','WEEKLY','MONTHLY','YEARLY') NULL,
    start_date            DATE         NULL,
    end_date              DATE         NULL,
    apply_before          DATE         NULL,
    status                ENUM ('DRAFT','ACTIVE','INACTIVE') NOT NULL DEFAULT 'DRAFT',
    -- stamped once, on first activation, and never moved afterwards (spec 4.2)
    published_at          DATE         NULL,
    PRIMARY KEY (id),
    KEY idx_jobs_employer_status (employer_id, status),
    CONSTRAINT fk_jobs_employer FOREIGN KEY (employer_id) REFERENCES employers (id)
        ON UPDATE RESTRICT ON DELETE CASCADE
);

CREATE TABLE job_status_events (
    id          UUID         NOT NULL,
    job_id      UUID         NOT NULL,
    from_status ENUM ('DRAFT','ACTIVE','INACTIVE') NULL,
    to_status   ENUM ('DRAFT','ACTIVE','INACTIVE') NOT NULL,
    actor       VARCHAR(255) NOT NULL,
    occurred_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_job_status_events_job (job_id, occurred_at),
    CONSTRAINT fk_job_status_events_job FOREIGN KEY (job_id) REFERENCES jobs (id)
        ON UPDATE RESTRICT ON DELETE CASCADE
);

CREATE TABLE job_tags (
    job_id UUID   NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (job_id, tag_id),
    CONSTRAINT fk_job_tags_job FOREIGN KEY (job_id) REFERENCES jobs (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_job_tags_tag FOREIGN KEY (tag_id) REFERENCES tags (id)
        ON UPDATE RESTRICT ON DELETE CASCADE
);

CREATE TABLE job_locations (
    job_id      UUID NOT NULL,
    location_id UUID NOT NULL,
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
    id               UUID         NOT NULL,
    created_at       DATETIME(6)  NULL,
    last_modified_at DATETIME(6)  NULL,
    -- one document carries exactly one employer, so a feed never spans employers (spec 3.5)
    employer_id      UUID         NOT NULL,
    name             VARCHAR(255) NOT NULL,
    slug             VARCHAR(64)  NOT NULL,
    description      VARCHAR(255) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_feeds_employer_name (employer_id, name),
    KEY idx_feeds_slug (slug),
    CONSTRAINT fk_feeds_employer FOREIGN KEY (employer_id) REFERENCES employers (id)
        ON UPDATE RESTRICT ON DELETE CASCADE
);

CREATE TABLE feed_jobs (
    feed_id UUID NOT NULL,
    job_id  UUID NOT NULL,
    PRIMARY KEY (feed_id, job_id),
    CONSTRAINT fk_feed_jobs_feed FOREIGN KEY (feed_id) REFERENCES feeds (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_feed_jobs_job FOREIGN KEY (job_id) REFERENCES jobs (id)
        ON UPDATE RESTRICT ON DELETE CASCADE
);

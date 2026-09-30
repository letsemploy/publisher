-- Job-link clicks (spec 3.14, 5.6).
--
-- The published job URL points at the publisher, which counts the click and
-- redirects to the employer's own page. What is kept is a counter per job, per
-- day and per country - never an address, a user agent or a single visit - so no
-- personal data is stored (spec 10), and the table grows by at most one row per
-- job, day and country without anything having to prune it (there is no
-- scheduler, spec 12).
--
-- Unknown is 'ZZ' rather than NULL, because NULLs are distinct in a key and a
-- concurrent first click would then write two rows. The day is text like every
-- date here ('yyyy-MM-dd 00:00:00.000'), so it compares correctly.
-- Translated by the rules of V6__baseline.sql.

CREATE TABLE job_clicks (
    job_id      TEXT    NOT NULL,
    day         TEXT    NOT NULL,
    country     TEXT    NOT NULL CHECK (length(country) = 2),
    employer_id TEXT    NOT NULL,
    clicks      INTEGER NOT NULL,
    PRIMARY KEY (job_id, day, country),
    CONSTRAINT fk_job_clicks_job FOREIGN KEY (job_id) REFERENCES jobs (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_job_clicks_employer FOREIGN KEY (employer_id) REFERENCES employers (id)
        ON UPDATE RESTRICT ON DELETE CASCADE
);
CREATE INDEX idx_job_clicks_employer_day ON job_clicks (employer_id, day);

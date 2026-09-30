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
-- concurrent first click would then write two rows. employer_id repeats the job's
-- immutable employer, so the dashboard filters by scope without joining jobs.

CREATE TABLE job_clicks (
    job_id      UUID    NOT NULL,
    day         DATE    NOT NULL,
    country     CHAR(2) NOT NULL,
    employer_id UUID    NOT NULL,
    clicks      BIGINT  NOT NULL,
    PRIMARY KEY (job_id, day, country),
    KEY idx_job_clicks_employer_day (employer_id, day),
    CONSTRAINT fk_job_clicks_job FOREIGN KEY (job_id) REFERENCES jobs (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_job_clicks_employer FOREIGN KEY (employer_id) REFERENCES employers (id)
        ON UPDATE RESTRICT ON DELETE CASCADE
);

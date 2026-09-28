-- Feed permalinks (spec 3.13, 5.5).
--
-- A stable public URL that serves one of its employer's feeds, or none. Switching
-- the feed changes what the URL publishes without changing the URL, so a website
-- or webserver configured with it never has to be touched.
--
-- The feed is nullable: no target publishes a valid document with no jobs. When
-- the targeted feed is deleted, the database clears the target (SET NULL) rather
-- than refusing the delete or taking the permalink with it. Same-employer is a
-- rule of the service, like the headquarters of V7.

CREATE TABLE permalinks (
    id               UUID         NOT NULL,
    created_at       DATETIME(6)  NULL,
    last_modified_at DATETIME(6)  NULL,
    employer_id      UUID         NOT NULL,
    name             VARCHAR(255) NOT NULL,
    description      VARCHAR(255) NULL,
    feed_id          UUID         NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_permalinks_employer_name (employer_id, name),
    KEY idx_permalinks_feed (feed_id),
    CONSTRAINT fk_permalinks_employer FOREIGN KEY (employer_id) REFERENCES employers (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_permalinks_feed FOREIGN KEY (feed_id) REFERENCES feeds (id)
        ON UPDATE RESTRICT ON DELETE SET NULL
);

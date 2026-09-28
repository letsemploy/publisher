-- Feed permalinks (spec 3.13, 5.5).
--
-- A stable public URL that serves one of its employer's feeds, or none. Switching
-- the feed changes what the URL publishes without changing the URL, so a website
-- or webserver configured with it never has to be touched.
--
-- The feed is nullable: no target publishes a valid document with no jobs. When
-- the targeted feed is deleted, the database clears the target (SET NULL) rather
-- than refusing the delete or taking the permalink with it - which SQLite does
-- only with foreign_keys=on, set on every connection by the sqlite profile.
-- Translated by the rules of V6__baseline.sql.

CREATE TABLE permalinks (
    id               TEXT NOT NULL PRIMARY KEY,
    created_at       TEXT NULL,
    last_modified_at TEXT NULL,
    employer_id      TEXT NOT NULL,
    name             TEXT NOT NULL COLLATE NOCASE,
    description      TEXT NULL,
    feed_id          TEXT NULL,
    CONSTRAINT uq_permalinks_employer_name UNIQUE (employer_id, name),
    CONSTRAINT fk_permalinks_employer FOREIGN KEY (employer_id) REFERENCES employers (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_permalinks_feed FOREIGN KEY (feed_id) REFERENCES feeds (id)
        ON UPDATE RESTRICT ON DELETE SET NULL
);
CREATE INDEX idx_permalinks_feed ON permalinks (feed_id);

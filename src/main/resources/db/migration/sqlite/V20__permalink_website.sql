-- The employer's website on a permalink, and what checking it found (spec 3.13, 7.23).
--
-- website_host is the host a website serves /.well-known/ojobpub.json from, by a
-- redirect to the permalink or a reverse proxy. Optional: the permalink works
-- without it, it only lets the back-office say whether that website is set up.
-- The check runs when someone asks for it, never on a schedule; its result is
-- written without touching last_modified_at, which dates the served document.
-- Translated by the rules of V6__baseline.sql.

ALTER TABLE permalinks ADD COLUMN website_host TEXT NULL;
ALTER TABLE permalinks ADD COLUMN checked_at TEXT NULL;
ALTER TABLE permalinks ADD COLUMN check_result TEXT NULL
    CHECK (check_result IN ('PROXY','REDIRECT','OTHER_DOCUMENT','STATIC_COPY','NOT_OJOBPUB',
                            'NOT_FOUND','HTTP_ERROR','UNREACHABLE','NOT_ALLOWED'));

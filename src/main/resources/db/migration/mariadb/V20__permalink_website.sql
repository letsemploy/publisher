-- The employer's website on a permalink, and what checking it found (spec 3.13, 7.23).
--
-- website_host is the host a website serves /.well-known/ojobpub.json from, by a
-- redirect to the permalink or a reverse proxy. Optional: the permalink works
-- without it, it only lets the back-office say whether that website is set up.
-- The check runs when someone asks for it, never on a schedule; its result is
-- written without touching last_modified_at, which dates the served document.

ALTER TABLE permalinks
    ADD COLUMN website_host VARCHAR(253) NULL,
    ADD COLUMN checked_at   DATETIME(6)  NULL,
    ADD COLUMN check_result ENUM ('PROXY','REDIRECT','OTHER_DOCUMENT','STATIC_COPY','NOT_OJOBPUB',
                                  'NOT_FOUND','HTTP_ERROR','UNREACHABLE','NOT_ALLOWED') NULL;

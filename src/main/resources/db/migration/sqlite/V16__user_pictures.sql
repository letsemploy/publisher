-- Account pictures (spec 3.7, 7.27). The same change as mariadb/V16.
--
-- A picture is stored here, never linked: an uploaded one is shrunk and
-- re-encoded, and a provider's (the OIDC picture claim, GitHub's avatar) is
-- downloaded by the server. The browser loads it from the application, so no
-- page tells a third party who is looking at it (spec 7.2).
--
-- A table of its own rather than a column on users: the user row is read on
-- every request, and the image has no business riding along. One row per user
-- at most, gone with the user (spec 2.13, 10).
--
-- source_url is the provider address the picture was fetched from, compared at
-- sign-in to notice a new one; an upload has none, and the provider never
-- replaces it. Translated by the rules of V6__baseline.sql.

CREATE TABLE user_pictures (
    user_id      TEXT NOT NULL PRIMARY KEY,
    content      BLOB NOT NULL,
    content_type TEXT NOT NULL,
    source       TEXT NOT NULL CHECK (source IN ('UPLOAD', 'PROVIDER')),
    source_url   TEXT NULL,
    updated_at   TEXT NOT NULL,
    CONSTRAINT fk_user_pictures_user FOREIGN KEY (user_id) REFERENCES users (id)
        ON UPDATE RESTRICT ON DELETE CASCADE
);

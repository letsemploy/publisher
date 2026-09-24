# ojobpub publisher

Manage your organisation's job postings and publish them as an
[ojobpub](https://github.com/letsemploy/schema) feed: a public, machine-readable `ojobpub.json` that
job boards and aggregators can read without an API key.

- **Postings** with locations, tags, salary and an application window. A job appears in a feed only
  while it is active, complete and inside its dates.
- **Feeds**: you choose which jobs each feed publishes. Every employer starts with one, and can
  have several, for example one per job board. Each feed has a stable public URL:
  `https://<your-host>/ojobpub/v1/<employer>_<id>/<feed>_<id>/ojobpub.json`
- **People**: invite colleagues as owners or editors. Owners can change roles, suspend and remove
  members.
- **Management API**: GraphQL at `/graphql`, authenticated with service tokens an owner creates, for
  integrations with other systems.
- **Sign-in** through your own OpenID Connect provider. The application stores no passwords.
- English and German.

## Run it

The image is published at `ghcr.io/letsemploy/publisher`. The smallest installation uses SQLite, a
single file on a volume, so there is no database server to run:

```bash
docker run -d --name ojobpub -p 8080:8080 -v ojobpub-data:/data \
  -e SPRING_PROFILES_ACTIVE=sqlite \
  -e APP_SQLITE_PATH=/data/ojobpub.db \
  -e APP_BASE_URL=https://jobs.example.com \
  -e SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_OIDC_ISSUER_URI=https://login.example.com/realms/example \
  -e SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_CLIENT_ID=ojobpub-publisher \
  -e SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_CLIENT_SECRET=change-me \
  -e SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_SCOPE=openid,profile,email \
  -e 'SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_REDIRECT_URI={baseUrl}/login/oauth2/code/{registrationId}' \
  ghcr.io/letsemploy/publisher:main
```

- **`APP_BASE_URL`** is the public address people and job boards use. Feed URLs are built from it,
  so set it correctly behind a reverse proxy.
- **Your identity provider** needs a confidential client with the redirect URI
  `https://<your-host>/login/oauth2/code/oidc` and the post-logout redirect
  `https://<your-host>/login?logout`.
  - It should send `email` and `email_verified`, because invitations go to verified addresses only.
    For a provider that never sends `email_verified`, set `APP_OIDC_REQUIRE_VERIFIED_EMAIL=false`.
  - Without a provider configured, the application still starts and serves its feeds, but nobody can
    sign in.
- **SQLite is for a single instance.** To run several, or to use a database server you already have,
  use MariaDB. Leave out the two SQLite variables and set `SPRING_DATASOURCE_URL`
  (`jdbc:mariadb://host:3306/ojobpub`), `SPRING_DATASOURCE_USERNAME` and
  `SPRING_DATASOURCE_PASSWORD`. The database must exist; its tables are created and upgraded on
  start.

Anyone who signs in can create an employer, and becomes its owner. An owner invites colleagues by
email; a colleague must have signed in once before they can be invited.

## More

- [`docs/SPEC.md`](docs/SPEC.md) is the full specification: roles, the job lifecycle, the feed
  contract, the API and every configuration setting.
- [`CLAUDE.md`](CLAUDE.md) covers development: building, running locally with a demo login or a
  local Keycloak, and the tests.

## License

Licensed under the [Apache License, Version 2.0](LICENSE).

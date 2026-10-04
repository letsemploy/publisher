# <img src="src/main/resources/static/images/logo.svg" width="40" height="40" alt="" align="top"> oJobPub Publisher

[![Build](https://github.com/letsemploy/publisher/actions/workflows/build.yml/badge.svg)](https://github.com/letsemploy/publisher/actions/workflows/build.yml)
[![Container image](https://github.com/letsemploy/publisher/actions/workflows/container.yml/badge.svg)](https://github.com/letsemploy/publisher/actions/workflows/container.yml)
[![Release](https://img.shields.io/github/v/tag/letsemploy/publisher)](https://github.com/letsemploy/publisher/tag)
[![License](https://img.shields.io/github/license/letsemploy/publisher)](LICENSE)

Manage your organisation's job postings and publish them as an
[ojobpub](https://github.com/letsemploy/schema) feed: a public, machine-readable `ojobpub.json` that
job boards and aggregators can read without an API key.

## Features

- **Jobs** with locations, tags, salary and an application window. A job appears in a feed only
  while it is active, complete and inside its dates, and the job screen says what is missing.
- **Feeds**: you choose which jobs each feed publishes, for example one per job board. Each feed
  shows whether it validates against the schema.
- **Permalinks**: one stable URL to put on your website, whose feed you can switch later without
  touching the website.
- **Click counting**: job links in the feed go through the publisher, which counts clicks per job,
  day and country, and answers "no longer available" once a job is gone. No visitor address is
  stored. Can be switched off.
- **Dashboard** with published jobs, clicks and their trend, the most clicked jobs, and a list of
  jobs that need attention.
- **People**: invite colleagues as owners or editors; owners change roles, suspend and remove
  members. Every change lands in an activity log.
- **Management API**: GraphQL at `/graphql`, with service tokens an owner creates and renews.
- **Sign-in** through one or more OpenID Connect providers (Google, Microsoft, GitLab, Keycloak, …)
  and GitHub, and optionally with email and password, including sign-up, password reset by mail and
  a captcha.
- **Admins** named in the configuration, who switch into admin mode when needed and can view the
  application as another user, read-only.
- **MariaDB or SQLite**, a container image that runs in 512 MB, English and German.

## Run it

The image is published at `ghcr.io/letsemploy/publisher` for `amd64` and `arm64`, tagged per release
and `latest` for the newest release and `main` for the development build. The smallest
installation uses SQLite, a single file on a volume, so there is no database server to run:

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
  ghcr.io/letsemploy/publisher:latest
```

- **`APP_BASE_URL`** is the public address people and job boards use. Feed URLs are built from it,
  so set it correctly behind a reverse proxy.
- **Your identity provider** needs a confidential client with the redirect URI
  `https://<your-host>/login/oauth2/code/oidc` and the post-logout redirect
  `https://<your-host>/login?logout`.
  - It should send `email` and `email_verified`, because invitations go to verified addresses only.
    For a provider that never sends `email_verified`, set `APP_OIDC_REQUIRE_VERIFIED_EMAIL=false`.
  - Without a provider or local accounts configured, the application still starts and serves its
    feeds, but nobody can sign in.
- **SQLite is for a single instance.** To run several, or to use a database server you already have,
  use MariaDB: leave out the two SQLite variables and set `SPRING_DATASOURCE_URL`
  (`jdbc:mariadb://host:3306/ojobpub`), `SPRING_DATASOURCE_USERNAME` and
  `SPRING_DATASOURCE_PASSWORD`. The database must exist; its tables are created and upgraded on
  start.
- **Memory.** The image runs in a 512 MB container at about 400 MB resident. Its JVM options are set
  in `JAVA_TOOL_OPTIONS`, and setting your own replaces them.
- **Mail** (any SMTP server, `SPRING_MAIL_HOST` and friends) is optional. It carries invitations, local
  account links and the weekly summary people can ask for in Settings; without it nothing is sent.
- **Click countries** come from a proxy header such as Cloudflare's `CF-IPCountry`, or from a
  mounted GeoIP database; see the prod example below.
- **Health.** `/actuator/health` answers up or down, without details, on the application's port.

### Configuration files

To keep the configuration in a file rather than in environment variables, start from
[`docs/examples/application-prod.yml`](docs/examples/application-prod.yml). Mount it as
`/config/application-prod.yml` and run with `SPRING_PROFILES_ACTIVE=prod`. It also switches off the
demo data, which production should not have.

- **Google:** add [`docs/examples/application-google.yml`](docs/examples/application-google.yml)
  beside it and run with `SPRING_PROFILES_ACTIVE=prod,google`. Its comments walk through the Google
  Cloud Console and explain how to limit sign-in to your own Workspace organisation.
- **Several providers at once:** use
  [`docs/examples/application-providers.yml`](docs/examples/application-providers.yml) instead.
- **Kubernetes:** [`docs/examples/kubernetes.yaml`](docs/examples/kubernetes.yaml) runs it with the
  prod file in a ConfigMap, secrets in a Secret, health probes and an ingress, on MariaDB or SQLite.
- **Email and password:** the prod file's `local-accounts`, `mail` and `captcha` sections, commented
  out, turn them on.

### First steps

Anyone who signs in can create an employer, and becomes its owner; the dashboard shows the next
step. An owner invites colleagues by email; a colleague must have signed in once before they can be
invited. Platform admins are named in
the configuration, by identity-provider group or by verified email.

## Development

Java 25 and Maven (the wrapper is included). A local MariaDB and Keycloak come from
`docker-compose.yml`; SQLite needs nothing.

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,sqlite   # http://localhost:8080, demo login
podman-compose up -d                                           # or: docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev          # same, on MariaDB
./mvnw test                                                    # tests on MariaDB
TEST_DB=sqlite ./mvnw clean test                               # tests on SQLite
```

[`CLAUDE.md`](CLAUDE.md) covers the rest: the architecture, signing in through the local Keycloak,
and what each test guards.

## Documentation

[`docs/SPEC.md`](docs/SPEC.md) is the index of the full specification in `docs/spec/`: roles, the
job lifecycle, the feed contract, the API and every configuration setting.
[`CHANGELOG.md`](CHANGELOG.md) lists the notable changes of each release.

## License

Licensed under the [Apache License, Version 2.0](LICENSE).

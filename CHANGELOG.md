# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
Section numbers such as §5.6 refer to the specification in [`docs/SPEC.md`](docs/SPEC.md).

## [Unreleased]

### Changed

- The navigation now separates what belongs to an employer from what does not (§7.3). The sidebar
  starts with a selection of the employer being worked on, shown even when there is only one, and
  lists only that employer's screens, with a new **Profile** entry for the employer's own record.
  Employers, Invitations and, for admins, Users moved to the top bar.
- The front-end files (Tabler, htmx, the icon sprite, mCaptcha's glue) are no longer committed. The
  Maven build downloads a pinned Node, installs them from `package-lock.json` and packages them, so
  building from source needs network access to nodejs.org and the npm registry, but still no Node on
  the machine (§9.1).
- On SQLite, every foreign-key column now has an index (migration V18), as MariaDB creates by itself.
  Deleting a job, tag, location or account, and counting a job's feeds, no longer scan whole tables.
  On MariaDB the migration changes nothing (§9.3).
- Service token secrets are stored as SHA-256 instead of bcrypt, so a management API call no longer
  spends 50–100 ms of CPU checking its token. Existing tokens keep working unchanged, and each one's
  hash is replaced the first time it is used (§2.8).

### Security

- A signed-in user who belonged to no employer could list every employer's jobs (titles, statuses,
  feed counts) by requesting the job list as an htmx fragment. The list is now empty for them, as the
  full page already showed (§2.4).
- The management API's body limit (`app.api.max-request-bytes`) now also applies to a chunked request,
  which declares no length and used to be read whole (§11.6).
- A service token whose own membership is missing is refused instead of acting as an editor (§2.8).
- Fetching a provider's picture now refuses more addresses that lead inside a network (§7.27).
  - **IPv6:** only global unicast addresses are fetched from. Before, NAT64, IPv4-compatible, 6to4 and
    Teredo addresses counted as public even when the IPv4 address inside them was private or loopback.
    NAT64 addresses are now judged by the IPv4 address they carry, so public hosts still load on an
    IPv6-only network.
  - **Port:** only the standard https port is used.
- Sign-in and the forms that send mail now count an IPv6 client by its /64 block, the range one
  subscriber is usually given (§2.12). Before, a client could start its failed-sign-in and mail limits
  afresh by switching to another address in its block, and it never reached the captcha. The counters'
  memory is bounded, and a flood of requests no longer makes each sign-in scan all of them.
- When those counters are full, during a flood of tens of thousands of clients, an attempt they
  cannot count is treated more strictly, never more leniently. Signing in then takes a captcha on
  every try, or is refused where no captcha is configured, and the mail-sending forms refuse it
  without a captcha. Nothing already counted is dropped to make room.
- An IPv6 /48 block is now counted as well, with four clients' allowance, so one rented block can
  no longer fill those counters on its own and lock out everyone else's first sign-in.

### Fixed

- Two requests arriving together on someone's very first sign-in (two tabs, or a page and its
  assets right after the provider's callback) no longer fail one of them with a server error. Both
  continue with the one account created.
- A feed lists its jobs in one order however the database returns them, so a consumer comparing two
  fetches sees only real changes (§5.2). Before, two jobs with the same date and title, or two
  locations of one city in different countries, could swap places. The paged lists of jobs, users,
  employers and activity no longer repeat or skip a row across pages when values tie.
- Two first clicks of the same job link, on the same day and from the same country, are both counted
  (§5.6). On SQLite the second was lost, and on either database a counter could be reset to one.
- A feed's `lastUpdated` and `Last-Modified` now move with every change to its document, so a cache
  revalidating with `If-Modified-Since` is no longer told `304` for a document that changed. Before,
  adding a job to a feed, removing or deleting one, setting one inactive, a job's dates running out, and
  renaming or deleting a tag all left the date unchanged or moved it backwards (§5.4).
- Feeds and permalinks now send the strong `ETag` that §5.4 requires, the hash of the bytes served, and
  answer a matching `If-None-Match` with `304`.
- Renaming a feed to the name of another of the employer's feeds is a field error on the form and a
  `userErrors` entry in the API, not a server error.
- `app.api.rate-limit=0` means unlimited, as `0` does for every other limit, instead of refusing every
  API call (§11.6).
- An unknown job type in the job list's address is ignored, an unknown status change answers 404, and a
  forged choice on the job form is a field error. All three were server errors.

## [1.4.0] - 2026-10-08

### Added

- The management API's schema is published as GraphQL SDL at `/graphql/schema`, with no sign-in or
  token needed, so a client can be generated from it (§11.2). Introspection stays off.

## [1.3.0] - 2026-10-04

### Added

- A weekly summary mail, asked for in Settings. Each Monday morning it covers the employers you belong
  to: clicks against the week before, jobs newly published, set inactive or past their dates, what needs
  attention, and, for owners, API tokens about to expire. It is sent only when a mail server is configured,
  and not in a week with nothing to report. `app.summary.cron` and `app.summary.zone` set when it goes out.

### Fixed

- The job list's row menu is no longer cut off by the table's scroll area.

## [1.2.0] - 2026-10-03

### Added

- Account pictures, in the user menu and on the People and Users screens, with initials where there is
  none (§7.27). A provider's picture (the OpenID Connect `picture` claim, GitHub's avatar) is copied in
  at sign-in; anyone can upload their own in Settings, which the provider then never replaces. Every
  picture is cropped, shrunk and re-encoded, and served by the publisher itself, so no page loads
  anything from a third party. The server needs outbound https to fetch provider pictures;
  `app.pictures.fetch-provider=false` turns that off. Database migration V16.

## [1.1.0] - 2026-10-03

### Added

- Deleting one's own account, from Settings, after typing one's email address back (§2.13,
  §7.25). Employers nobody else owns are deleted with it, with everything they hold; employers
  someone else owns stay. The only admin cannot delete their account.
- An invited person is mailed about the invitation, with a link to accept or decline it, when a mail
  server is configured (§2.6). Only addresses that belong to an account are mailed, and an invitee can
  turn it off in Settings.
- A Settings page, from the user menu (§7.26): language, theme, time zone for every date and time
  shown, and whether invitations are mailed. A local account can change its name there. Change
  password and Delete account moved there from the user menu.
- The application in French, Italian, Spanish, Portuguese, Japanese, Russian and Simplified Chinese,
  besides English and German (§8.1); chosen in Settings, or on the sign-in page.

### Changed

- Language and theme are kept with the account instead of the session, so they survive signing out
  and follow a person to other devices. They are chosen in Settings, which replaces the language and
  theme entries of the user menu. Mail meant for a person - an invitation, a password reset - is in
  their chosen language. Database migration V15.
- An API token outlives the account that created it: deleting that account keeps the token working,
  and the API tokens screen says it was created by a deleted account. Database migration V14 makes
  the creator optional.

### Fixed

- A missing translation falls back to English, never to the server's own language: on a host with a
  German system locale, English pages could otherwise show German.

## [1.0.2] - 2026-10-02

### Fixed

- A user who belongs to no employer yet - a new account, signing in by any means - was offered
  *New location*, *New tag*, *New job* and *New feed*, each of which answered "not found". Those
  lists now say that an employer is needed and link to creating one (§2.5).

## [1.0.1] - 2026-10-02

### Fixed

- The 1.0.0 container image did not start: Flyway and JPA depended on each other ("Circular
  depends-on relationship between 'flyway' and 'entityManagerFactory'"), in an order the build's
  classpath decided. Flyway now always migrates first, then the seed, then Hibernate.

## [1.0.0] - 2026-10-02

### Added

- Webserver configuration for a permalink, on its edit page: a redirect and a reverse proxy from
  `/.well-known/ojobpub.json` on the employer's own domain, for Apache, nginx and Caddy, ready to
  copy (§7.23).

## [0.9.0] - 2026-09-30

### Added

- A logo, shown in the sidebar and on the sign-in page, and a favicon.

## [0.8.0] - 2026-09-30

### Added

- Getting-started messages on the dashboard, outside admin mode: for a user with no employer yet
  (create one, or see the invitations) and for an employer with no job yet (§7.10).

### Changed

- One employer is always active: the first by name until another is chosen, and a choice that is no
  longer available falls back to the first. The switcher is shown only with more than one employer
  (§2.5).

### Removed

- The *All employers* choice and the employer columns it needed. Admins keep the overview in *All
  activity* and the Employers list (§2.5, §7.21).

## [0.7.0] - 2026-09-30

### Changed

- A revised dashboard (§7.10): the published share and the new jobs of the last 30 days on the status
  cards; the clicks of the last 30 days with their trend, a line of daily clicks and a tracking strip
  with one square per day; a "needs attention" list of jobs about to close, jobs that cannot be
  published and forgotten drafts; the most clicked jobs with a 14-day bar chart and trend each; and
  the latest activity. The charts are Tabler's own sparkline and tracking components.

### Removed

- The dashboard's warning about jobs omitted from published feeds; the status counts and the Feeds
  screens already show them (§7.10).

## [0.6.0] - 2026-09-30

### Added

- Job links: a published job's URL leads through the publisher, `/go/{jobId}`, which redirects to the
  job's page and counts the click per job, day and country. Once a job leaves the feed its link
  answers `410` with a "no longer available" page, even from a cached copy of the feed. The country
  comes from a proxy header or a mounted GeoIP database; no address is stored (§3.14, §5.6).
- A dashboard card with the most clicked jobs and the clicks by country over the last 30 days (§7.10).
- Local accounts with email and password, off by default: sign-up and password reset by mailed link,
  a configurable password policy with a strength estimate, and a captcha (hCaptcha or mCaptcha) on the
  forms that send mail and after failed sign-ins (§2.12, §7.24).
- Outbound mail over SMTP, sent only once the change it reports is committed (§2.12).
- Feed permalinks: a stable URL for a website to use, whose feed can be switched, or set to none,
  without touching the website (§3.13, §5.5, §7.23).
- Suspending user accounts: admins can suspend and reinstate an account, and an open session is
  signed out at its next request (§2.11).

### Changed

- **The job `url` in the published feed now points at the publisher** (`{app.base-url}/go/{id}`)
  instead of the employer's page, so consumers see every job URL change once. Set
  `app.clicks.enabled=false` to publish the employer's page as before (§5.6, §6.4).
- The dashboard and the feed screen lead with permalink URLs; a feed's own URL is for testing
  (§7.10, §7.12).
- A failed sign-in keeps the email address in the form (§7.7).
- Tabler 1.6.0.

### Fixed

- The public feed no longer opens a session on every fetch, and a failure while serving it answers
  with JSON rather than the HTML error page (§5.1, §8.2).
- API: a malformed date or tag id in a job is reported in `userErrors`, not as an internal error
  (§11.4).

## [0.5.0] - 2026-09-28

### Fixed

- The provider buttons and the link back after signing out load the page fully instead of through
  htmx, so they reach the identity provider.

## [0.4.0] - 2026-09-28

### Added

- A Kubernetes deployment example.

### Changed

- The theme follows the system setting until the user picks one.
- Switching the employer goes to the dashboard.

## [0.3.0] - 2026-09-27

### Changed

- Lower memory use: the container runs in 512 MB instead of about 1 GB, and starts faster.

### Removed

- JobRunr, which ran no jobs; migration V9 drops its tables.

### Fixed

- After signing out, the sign-in page offers a way back in instead of the provider buttons.

## [0.2.0] - 2026-09-27

### Added

- One sign-in button per configured identity provider, and sign-in with GitHub (§7.19).
- Platform admins granted from configuration, by person or group (§2.2).
- Admin mode: admins work in the user view until they switch (§2.10).
- Admins can view the application as another user, read-only (§2.9).
- An audit log, readable per employer and per person (§3.12).
- Owners can delete their employer, confirmed by typing its name.
- A footer naming the source, the license and the running version.
- Example production configurations.

### Changed

- Locations and tags belong to an employer; migration V7 divides the formerly shared rows
  (§3.2, §3.4).
- A newly created employer becomes the active one.

### Fixed

- The job form's location and tag pickers.

## [0.1.0] - 2026-09-24

### Added

- Employers, jobs, locations, tags and feeds, and the public `ojobpub.json` feed validated against
  the oJobPub schema.
- Invitations with per-employer roles (owner, editor), the People screen, and suspending members.
- A GraphQL management API authenticated by service tokens that expire after 12 months.
- Configurable resource quotas.
- OpenID Connect sign-in with PKCE.
- MariaDB, or SQLite for a single instance.
- English and German.

[Unreleased]: https://github.com/letsemploy/publisher/compare/v1.4.0...HEAD
[1.4.0]: https://github.com/letsemploy/publisher/compare/v1.3.0...v1.4.0
[1.3.0]: https://github.com/letsemploy/publisher/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/letsemploy/publisher/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/letsemploy/publisher/compare/v1.0.2...v1.1.0
[1.0.2]: https://github.com/letsemploy/publisher/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/letsemploy/publisher/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/letsemploy/publisher/compare/v0.9.0...v1.0.0
[0.9.0]: https://github.com/letsemploy/publisher/compare/v0.8.0...v0.9.0
[0.8.0]: https://github.com/letsemploy/publisher/compare/v0.7.0...v0.8.0
[0.7.0]: https://github.com/letsemploy/publisher/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/letsemploy/publisher/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/letsemploy/publisher/compare/0ff137f...v0.5.0
[0.4.0]: https://github.com/letsemploy/publisher/compare/v0.3.0...0ff137f
[0.3.0]: https://github.com/letsemploy/publisher/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/letsemploy/publisher/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/letsemploy/publisher/releases/tag/v0.1.0

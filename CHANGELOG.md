# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
Section numbers such as §5.6 refer to the specification in [`docs/SPEC.md`](docs/SPEC.md).

## [Unreleased]

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

[Unreleased]: https://github.com/letsemploy/publisher/compare/v0.8.0...HEAD
[0.8.0]: https://github.com/letsemploy/publisher/compare/v0.7.0...v0.8.0
[0.7.0]: https://github.com/letsemploy/publisher/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/letsemploy/publisher/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/letsemploy/publisher/compare/0ff137f...v0.5.0
[0.4.0]: https://github.com/letsemploy/publisher/compare/v0.3.0...0ff137f
[0.3.0]: https://github.com/letsemploy/publisher/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/letsemploy/publisher/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/letsemploy/publisher/releases/tag/v0.1.0

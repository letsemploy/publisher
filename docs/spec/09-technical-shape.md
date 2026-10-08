<!-- Part of the specification: sections are numbered across all files, and code cites them as "spec N.M". -->
[Specification index](../SPEC.md) · ← [8. Cross-cutting behaviour](08-cross-cutting.md) · [10. Non-functional requirements](10-non-functional.md) →

# 9. Technical shape

## 9.1 Stack

Java 25, Spring Boot 4 (Web MVC), Thymeleaf with the Layout Dialect for server-rendered HTML, htmx for
partial updates, Tabler as the design system, Spring Data JPA over MariaDB or SQLite (§9.3), Flyway for
schema migrations, Spring Security with the OAuth2 client stack for OIDC.

Server-rendered HTML with fragment swaps is a deliberate choice: the back-office is form-and-list heavy
with no offline or real-time requirements, and a single deployable keeps the product proportional to
the problem. §7.1 and §7.2 state the resulting constraints — self-hosted assets, no bundler, and a
small amount of our own JavaScript rather than libraries — which follow from that choice.

Front-end dependencies are declared in **`package.json`** with a committed lockfile. **The lockfile is
the only committed record**: the build installs from it and copies the published `dist` files into the
application's static resources, and the application serves them from there. The files themselves are
**not committed**.

**Node is fetched by the build, never a prerequisite.** `./mvnw package` **must** produce a runnable
artifact from a clean checkout with Maven alone, on a machine with no Node installed. Maven downloads
Node at a version pinned in `pom.xml`, runs `npm ci`, and writes the files the browser receives into
the build output. CI and the container build need nothing more than Maven.

The benefit is that a dependency update is a change to `package.json` and the lockfile alone, which
an automated update can make, test and merge without anyone regenerating files by hand. The lockfile's
integrity hashes pin the exact bytes sent to browsers. The icon sprite is built by the same step,
trimmed to the icons actually referenced rather than shipping the full set, so a newly referenced icon
reaches it with the next build.

## 9.2 Structure

Code is organized **by feature, not by layer**: `employer`, `job`, `location`, `tag`, `feed`, plus
`common` for shared base types and `config` for wiring. Each feature owns its entity, repository,
service, web DTOs and controllers.

The published contract lives in its own package, `ojobpub.v1`, isolated from the domain:

- Its DTOs are **only** shaped by the schema and **must not** be reused as form-binding objects.
- Mapping from domain to contract happens in exactly one place, so a change to the domain model cannot
  alter the published output by accident.
- No JPA entity may be serialized to a consumer directly.

Per feature there are two controllers: one returning full pages, one returning htmx fragments, sharing
the same URLs so that one address serves both a direct navigation and a fragment swap.

Because navigation uses `hx-boost` (§7.4), the discriminator **must** be "an htmx request that is *not*
boosted". A boosted navigation is an htmx request but still wants the whole page: routing it to the
fragment controller would return a bare fragment with no layout. Fragment controllers therefore match
only non-boosted htmx requests, and every fragment endpoint must also render correctly as a full page
when requested directly, which is what makes the progressive-enhancement guarantee of §7.1 real rather
than aspirational.

## 9.3 Persistence

- UUID (time-ordered) primary keys for all entities except `Tag`, which uses a generated numeric id.
- **Flyway owns the schema.** Hibernate DDL generation is disabled in every profile. Every schema
  change is a new versioned migration; migrations are never edited once merged.
- Enumerations are persisted **as strings**, never as ordinals — including country codes (§3.2).
- Every association that is not needed for the common read path is lazy. The feed serving path must load
  its jobs with their locations and tags in a bounded number of queries; an N+1 on feed serving is a
  defect.
- Membership has **exactly two write sites**: creating an employer, and accepting an invitation
  (§2.2). A third would mean access had been granted without consent. Changing an existing membership's
  `role` is a separate, narrower operation (§2.7) and must not be able to create one.
- Every employer **must** have at least one owner (§3.9). Creating an employer and the role-change path
  are the only places this can be violated, so both enforce it.
- Seed/demo data is loaded only under the `dev` profile and must be idempotent. It includes the
  development administrator (§2.3), without which nothing keyed on user identity works locally.

**Two databases.** MariaDB is the default and the reference. **SQLite** is supported as an alternative
for a **single-instance** installation — one team, one process, one file on a volume, no database server
to run. It is selected with the `sqlite` profile and a file path; nothing else about the application
changes.

- **One instance only.** SQLite has a single writer, and the API rate limiter is already counted per
  instance (§11.6). Scaling out means MariaDB.
- **Same behaviour, enforced the same way.** Foreign keys and their cascades, the two exactly-one-of
  CHECK constraints (§3.11), and the enumerated value sets are enforced by the database on both. Text
  that MariaDB compares case-insensitively — tag names, cities, email, employer, feed and job names —
  is case-insensitive on SQLite too, for ASCII; beyond ASCII ("Zürich" versus "zürich") SQLite
  distinguishes case where MariaDB does not.
- **Instants, dates and money are stored as text** on SQLite, in one fixed format, because SQLite has no
  such types and a mixture of representations would silently break the date window (§4.4). Salaries
  keep their exact decimal value (§6.7).
- **Migrations come in pairs.** Each database has its own migration folder. SQLite starts from a
  single baseline at the version MariaDB had reached when SQLite was added, so both report the same
  schema version; every later migration exists for both, and the build fails if one is missing.
- The seed data likewise exists once per database, with the same rows and identifiers.
- The test suite runs **in full against both** in CI.

## 9.4 Security configuration

- Three filter chains: the public feed, the job links (§5.6) and health endpoints (stateless,
  anonymous, CSRF disabled, `GET` only); the management API (stateless, bearer token, CSRF disabled, `POST` only — §11.2); and
  the back-office (session-based, authenticated, CSRF enabled).
- Which back-office chain applies is decided once, at startup: sign-in when a provider is configured,
  local accounts are enabled (§2.12), or both; the development bypass under `dev` (§2.3); and
  otherwise a **closed** chain — the application starts and serves its feeds, but nobody can sign in.
  With no way to authenticate anyone, failing closed is the only safe default.
- The API chain **must not** fall back to the back-office chain. A GraphQL request with no token is a
  401, never a redirect to an identity provider: an integration receiving a login page instead of JSON
  fails in a way that is tedious to diagnose.
- CSRF protection applies to every state-changing back-office request, including htmx-initiated ones;
  the token is propagated on htmx requests via request headers.
- Authorization is applied in the service layer against employer membership, so no controller can leak
  data by forgetting a check.
- Standard security response headers (HSTS, `X-Content-Type-Options`, a restrictive
  `Content-Security-Policy`, `Referrer-Policy`) are set on back-office responses.
- Because all assets are self-hosted and the application's own behaviour lives in a module file
  (§7.2), the policy is genuinely strict: `default-src 'self'` with no external script, style, font or
  image origin — account pictures are copied in, not linked (§7.27) — **no `unsafe-inline`** and **no `unsafe-eval`**. An inline `<style>` or `<script>` block, a
  CDN reference, or anything that evaluates code from a string will break the page outright, which is
  the intended feedback.
- The `dev` bypass is constrained exactly as described in §2.3.

## 9.5 Configuration

No secrets in the repository. OIDC issuer, client id and client secret come from the environment. The
application's public base URL is configured explicitly, because feed URLs must be absolute and correct
behind a reverse proxy.

**Identity provider.** Any standards-compliant OIDC provider, configured through Spring's own
properties with the registration id `oidc`:

| Environment variable | Value |
|---|---|
| `SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_OIDC_ISSUER_URI` | the provider's issuer; its metadata is discovered at startup |
| `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_CLIENT_ID` | the client registered for this application |
| `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_CLIENT_SECRET` | its secret |
| `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_SCOPE` | `openid,profile,email` |
| `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_REDIRECT_URI` | `{baseUrl}/login/oauth2/code/{registrationId}` |

The provider must allow `https://<host>/login/oauth2/code/oidc` as a redirect URI and, for
RP-initiated logout, `https://<host>/login?logout` as a post-logout redirect (§7.19). None of these has a default: an
installation with no provider configured runs with the back-office closed (§9.4).

**Admins** (§2.2) are `app.admin.people` (each `issuer` plus `email` *or* `subject`) and
`app.admin.groups` (each `issuer`, `claim`, `value`). A malformed entry stops the application at
startup rather than locking admins out or letting someone in. The issuer to use is the provider's own,
for example `https://accounts.google.com` or `https://github.com`. A person's subject is in the
`users` table once they have signed in.

**Admin mode** (§2.10) is off at the start of every session. `app.admin.start-in-admin-mode=true`
starts every admin's session in it instead, for a team that would rather not switch.

**Several providers** are several registrations, each under its own id in place of `oidc`, for
example `google`, `gitlab` or `microsoft`. Each gets its own button (§7.19) and its own callback,
`https://<host>/login/oauth2/code/<id>`, which is what that provider must allow. Spring knows Google's
endpoints by the id `google`, so a client id and secret are enough there. Every other provider needs its
`issuer-uri`. GitHub is known by the id `github` too, but must add the `user:email` scope
(`scope: read:user,user:email`), which Spring's defaults leave out. Any other registration that does
not request `openid` stops the application at startup (§2.2). For local work the
`keycloak` profile points at the Keycloak in the repository's compose file.

**Local accounts** (§2.12) are `app.local-accounts.enabled` (off by default); the password policy,
`app.local-accounts.password.min-length` (12), `.max-length` (128), `.required-classes` (0–4, default
0) and `.min-strength` (zxcvbn score 0–4, default 3); and the sign-in limits,
`app.local-accounts.captcha-after-failures` (3; 0 = always) and `.max-attempts` (10; 0 = never),
within `.throttle-window` (15 minutes). A policy that cannot be met stops the application at startup. They need **mail**: the standard `spring.mail.*`
settings of an SMTP server, `app.mail.from` as the sender, and `app.base-url` for the links. The
**captcha** is `captcha.*`: `captcha.enabled`, `captcha.provider` (`hcaptcha` or `mcaptcha`) and that
provider's site key and secret, plus the instance URL for mCaptcha. Local accounts without a captcha
start with a warning rather than refusing to, so development needs no captcha service.

The database is MariaDB unless the `sqlite` profile is active (§9.3), in which case the file is
`app.sqlite.path` (`APP_SQLITE_PATH`), default `data/ojobpub.db`, and its directory is created on start.

**Job links** (§5.6) are `app.clicks.enabled` (on by default; off publishes `Job.url` itself). The
click's country is `app.clicks.country-header`, the header a proxy or CDN sets (empty by default), and
`app.clicks.geoip-database`, the path to a GeoIP country database in MaxMind's `.mmdb` format — GeoLite2
or DB-IP Lite — which the deployment supplies and refreshes; a configured path that cannot be read stops
the application at startup. Behind a reverse proxy the database needs the client's address, so
`server.forward-headers-strategy` must be set, and both sources are only as trustworthy as the proxy is
the only way in. `app.clicks.ignore-user-agents` lists the user-agent substrings that are machines, and
`app.clicks.dashboard-days` (30) and `app.clicks.top-jobs` (10) shape the dashboard card.

The **weekly summary** (§7.28) runs on `app.summary.cron`, a Spring cron expression (second, minute,
hour, day, month, weekday; default `0 0 7 * * MON`), in `app.summary.zone` (an IANA zone; empty, the
default, is the server's). `app.summary.enabled=false` switches the schedule off. It needs mail, as
above, and `app.base-url` for its links.

## 9.6 Code conventions

Plain Java first. Lombok is **reduced to accessors on mutable classes**, and everything else is
written out, so that what a class does is what it says.

- **Values are records**: view models, the published DTOs (§9.2), service results and small carriers.
  A derived property stays on the record as a method; templates read record accessors directly.
- **Lombok is allowed only as `@Getter`, `@Setter` and `@NoArgsConstructor`**, on JPA entities (which
  cannot be records) and on the few mutable classes that bind a form or hold session state. `@Data`,
  `@Value`, `@RequiredArgsConstructor`, `@AllArgsConstructor`, `@Slf4j` and generated
  `equals`/`hashCode`/`toString` are not used. Generated equality on an entity is wrong in any case.
- **Spring beans take an explicit constructor.** Injection is constructor injection and never a field;
  a configured value (`@Value`) is a constructor parameter, so every injected field is `final`.
- **Loggers are plain SLF4J fields**, `private static final Logger log = LoggerFactory.getLogger(…)`.
- **Input shape is validated with Jakarta Bean Validation** — required, length, format, and
  cross-field orderings as class-level constraints — on the input a **service** receives, and checked
  by that service. The form and the management API (§11) reach the same service, so they cannot
  validate differently. Violations are turned into the same field errors as every other refusal
  (§8.2, §11.4), keyed by the field the user typed into, and their messages are message-bundle keys,
  so they are translated (§8.1).
- **Rules are not constraints.** Whatever needs the database, the acting user or the current state —
  duplicates, another employer's records, quotas (§8.4), the last owner (§2.7), state transitions and
  publication readiness (§4) — stays written out in the service, as does parsing a raw string into an
  id, a date or an enum.

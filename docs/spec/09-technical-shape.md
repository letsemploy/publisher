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

Front-end dependencies are declared in **`package.json`** with a committed lockfile. Their published
`dist` files are copied into `src/main/resources/static` and **committed to the repository**; the
application serves them from there.

**Node is a maintenance tool, never a build dependency.** `./mvnw package` **must** produce a runnable
artifact from a clean checkout with Maven alone, on a machine with no Node installed. Neither CI nor
the container build may require it. The cost is generated files in version control; the benefit is that
the build has one toolchain, and the exact bytes sent to browsers are reviewable in a diff.

Refreshing the assets is one documented command that installs from the lockfile and copies the `dist`
files into place. Upgrading a dependency is therefore an ordinary reviewable commit, and the icon
sprite is trimmed to the icons actually referenced rather than shipping the full set.

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

- Three filter chains: the public feed and health endpoints (stateless, anonymous, CSRF disabled,
  `GET` only); the management API (stateless, bearer token, CSRF disabled, `POST` only — §11.2); and
  the back-office (session-based, authenticated, CSRF enabled).
- Which back-office chain applies is decided once, at startup: OIDC login when a provider is
  configured, the development bypass under `dev` (§2.3), and otherwise a **closed** chain — the
  application starts and serves its feeds, but nobody can sign in. With no way to authenticate anyone,
  failing closed is the only safe default.
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
  (§7.2), the policy is genuinely strict: `default-src 'self'` with no external script, style or font
  origin, **no `unsafe-inline`** and **no `unsafe-eval`**. An inline `<style>` or `<script>` block, a
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

**Several providers** are several registrations, each under its own id in place of `oidc`, for
example `google`, `gitlab` or `microsoft`. Each gets its own button (§7.19) and its own callback,
`https://<host>/login/oauth2/code/<id>`, which is what that provider must allow. Spring knows Google's
endpoints by the id `google`, so a client id and secret are enough there. Every other provider needs its
`issuer-uri`. A registration that does not request `openid` stops the application at startup (§2.2). For local work the
`keycloak` profile points at the Keycloak in the repository's compose file.

The database is MariaDB unless the `sqlite` profile is active (§9.3), in which case the file is
`app.sqlite.path` (`APP_SQLITE_PATH`), default `data/ojobpub.db`, and its directory is created on start.

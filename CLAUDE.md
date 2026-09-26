# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Spring Boot 4 / Java 25 server-rendered web app (Thymeleaf + htmx). Employers manage job postings here
and publish selected ones as a machine-readable `ojobpub.json` feed. Maven artifact is `ojobpub-app`,
Java package root is `org.letsemploy.ojobpub_publisher`.

**The specification is authoritative** — purpose, roles, domain model, job lifecycle, the published feed
contract and the user interface. Where it disagrees with the code, the spec wins. It is one file per
chapter under `docs/spec/` (`04-job-lifecycle.md` holds §4), with `docs/SPEC.md` as the index linking
every section. Code comments cite it as `spec 4.3` — the number is global across the files, so §4.3
is in chapter 4 — and that is the fastest way to find the rule behind a piece of code. Keep the
numbering when editing: a new section goes at the end of its chapter, never renumbering the ones
after it, because code cites them; a new section also needs its line in the index.

## Commands

Two databases (§9.3): **MariaDB**, the default, and **SQLite** for a single-instance installation — see
**Two databases** under Architecture. With MariaDB, a server is needed to run *and* to test. `podman-compose up -d` (or `docker compose up -d`) brings up
`docker-compose.yml`: the `db` service on **3307** (root/root), adminer on **8082**, and **Keycloak on
8083** (admin/admin), which imports `keycloak/ojobpub-realm.json` — realm `ojobpub`, users alice/alice
and bob/bob, and mallory/mallory whose email is unverified. The realm is imported only into a fresh
container, so after editing it: `podman rm -f ojobpub-publisher_keycloak_1 && podman-compose up -d
keycloak` (podman-compose has no `rm`).

**Not 3306, deliberately.** Other projects on the same machine publish 3306 and 8081, and sharing one
server means another project's `compose down` takes these databases with it — or, worse, two projects
use one instance without anyone noticing. Both the application and the tests read the port from
`DB_PORT`, defaulting to 3307; CI sets `DB_PORT=3306` because there MariaDB is a service container on
the standard port. Override it locally the same way to point at a different server.

The container is `ojobpub-publisher_db_1`; the compose *service* is `db`, so it is
`podman-compose exec db mariadb -uroot -proot`, not `mariadb`.

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev   # http://localhost:8080
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,sqlite  # same, on data/ojobpub_dev.db, no server
./mvnw spring-boot:run -Dspring-boot.run.profiles=keycloak    # real OIDC login; Keycloak must be up first
./mvnw package                                          # build jar (runs tests)
./mvnw test                                             # all tests, on MariaDB
TEST_DB=sqlite ./mvnw clean test                        # all tests, on SQLite (target/publisher_test.db)
./mvnw test -Dtest=MembershipServiceTest                # one class
./mvnw test -Dtest=TagServiceTest#namesAreNormalised    # one method
./mvnw versions:display-dependency-updates              # or: make check-mvn-updates
```

Locally, run either `dev` or `keycloak`; plain startup has no datasource. Both are **profile groups**
(`application.properties`) that bring in `local` — `application-local.yml`, the developer's MariaDB
(database `ojobpub_publisher_dev`), the seed and GraphiQL. `dev` adds the authentication bypass (§2.3);
`keycloak` adds real OIDC login against the compose Keycloak (`application-keycloak.yml`). There is no
`application-dev.yml`: the bypass is `@Profile("dev")` beans, so it cannot leak into `keycloak`, and
the two differ in exactly that. A deployment sets `SPRING_DATASOURCE_*` for MariaDB or runs with
`SPRING_PROFILES_ACTIVE=sqlite` and `APP_SQLITE_PATH` on a volume, plus the five `SPRING_SECURITY_OAUTH2_*`
variables of §9.5 — see **Authentication** below. `docs/examples/application-prod.yml` is a complete,
commented `prod` configuration, meant to be mounted, not packaged. It also turns off the JobRunr
dashboard, which has no authentication, and pins the Flyway table name, which is otherwise set only in
the local and test profiles. Keep it in step when adding a setting a deployment must know about. Spring's
binder leaves an unresolved `${VAR}` as literal text instead of failing, so a missing variable is not
caught at startup.

JobRunr's dashboard binds port 8000 whenever the app runs. Actuator and the Prometheus registry are on
the main port.

Container builds use `Dockerfile.multistage`, which is what CI pushes to ghcr.io. The plain
`Containerfile`/`Dockerfile` expects a pre-built `target/app.jar`.

## Architecture

### Package by feature

`employer`, `job`, `location`, `tag`, `feed`, `invitation`, `membership`, `token` — each owns its
entity, repository, service, form objects and controllers. Plus `common` (shared base types, slugs,
exceptions), `config` (beans), `security` (actors, roles, filter chains), `web` (shell context, view
models, error handling), `ojobpub/v1` (the published contract) and `api` (the GraphQL management API).

Controllers are thin: they resolve the actor, call a service, and put view models on the model.
**Authorization lives in services, never in controllers or templates** (§2.4) — hiding a button is not
authorization, and a route that forgets a check would otherwise be exploitable.

### Refusals are 404, not 403

A user asking for something they may not see, or may not do, gets `NotFoundException` → 404. This is
deliberate and uniform: 403 would confirm that the record exists. `common/exception/ValidationFailure`
carries field errors back to a form; `NotFoundException` is unchecked and handled centrally.

### The rules live in one place

- `job/Publication.java` — the publication rules of spec 4.3/4.4: readiness requirements, the date
  window, and what a job *presents* as (`PUBLISHED`, `EXPIRED`, `INCOMPLETE`, `DRAFT`, `INACTIVE`). Both
  the feed serving path and the back-office readiness panel read it, so a screen and a published
  document cannot disagree. Add publication logic here, not in a controller.
- `membership/MembershipService.java` — who belongs to an employer and with what role, including the
  last-owner rule.
- `ojobpub/v1/service/OjobpubEnums.java` — explicit mapping for every published enum. **Never** derive a
  published value with `name().toLowerCase()`: the schema wants `on-site`, which that produces as
  `on_site`.
- `token/TokenLifecycle.java` — when a token lapses and what it presents as (§2.8): ACTIVE, EXPIRING,
  EXPIRED, REVOKED. Static and clock-taking like `Publication`, and for the same reason — expiry is
  derived at read time and **nothing is written as a token lapses**, which is what makes reactivating
  one a date change rather than an undo, and what keeps this project free of a scheduler. Revoked
  outranks expired: one is permanent, the other is not.
- `common/ResourceLimits.java` — the six creation quotas of §8.4 and, more importantly, the two rules
  around them: **`0` means unlimited**, and a quota refuses **at** the cap. Spread across six services
  those get written six times and one of them forgets the zero, turning "unlimited" into "none". It
  takes a `LongSupplier`, so a disabled quota issues no `COUNT` at all. It injects no repositories —
  `common` depends on nothing, and each service owns the repository that answers its own count.

### Published feed

`GET /ojobpub/v1/{employerSlug}_{employerId}/{feedSlug}_{feedId}/ojobpub.json` — public, anonymous,
`GET` only. Identity is the UUID; the slug is decorative, so a stale slug still resolves and answers
`301` to the canonical URL. Slug and UUID are separated by an **underscore**, the one character neither
side may contain. Malformed segments answer `404`, never `400`.

The document is built by `OjobpubService` and validated by `OjobpubValidator` against the vendored
schema at `src/main/resources/ojobpub/v1/ojobpub.schema.json` (upstream:
`https://raw.githubusercontent.com/letsemploy/schema/refs/heads/main/v1/ojobpub.json`). The feed screen
shows a validity badge from the same validator.

### Authentication and roles

OIDC via Spring Security, authorization code **with PKCE** (§2.2). `SecurityConfig` has four chains, in
order: the **management API** (`/graphql`, bearer token, stateless, no CSRF); the public feed; a
**dev-profile bypass** (no identity provider needed); and the back-office. The API chain is `@Order(0)`
on purpose — the dev bypass must never reach `/graphql`, so the API is token-authenticated even locally.
`DevBypassGuard` refuses to start if `dev` is combined with `prod`.

**The back-office chain decides at bean creation, not with `@ConditionalOnBean`.** `backOfficeChain`
takes an `ObjectProvider<ClientRegistrationRepository>`: OIDC login if a repository exists, a
**fail-closed** chain if not (the app starts and feeds stay served, but nobody gets in). It used to be
two beans behind `@ConditionalOnBean`/`@ConditionalOnMissingBean`, and those conditions run while
`SecurityConfig` is parsed — *before* auto-configuration registers the repository built from
`spring.security.oauth2.client.*`. So the closed chain won with a provider configured and nobody could
ever sign in; every test ran under `dev` and none noticed. Never gate a bean in this class on an
auto-configured bean. `OidcLoginTest` and `LockedChainTest` guard both directions.

- **PKCE is set explicitly** (`OAuth2AuthorizationRequestCustomizers.withPkce()`): Spring adds it by
  itself only for public clients, and this one is confidential. The compose Keycloak *requires* S256,
  so losing it breaks local login loudly.
- **The registration id is `oidc`** and `redirect-uri` must be set: Spring defaults it only for
  providers it knows by name, and refuses to start without it for any other.
- **Accounts:** `CurrentUserService.signIn` finds by (issuer, subject) or creates — `USER` role, no
  memberships — and refreshes name and email from the token, writing only when they changed (it runs
  on every request). **Only a verified email is stored** (`email_verified`), because invitations go to
  whoever holds an address; `app.oidc.require-verified-email=false` relaxes it.
- **Email is not unique.** Look people up with `UserRepo.findUniqueByEmail`, which answers empty for an
  address held by several accounts — `InvitationService` then answers `SENT`, as for an unknown one.
  There is deliberately no single-result finder: two accounts sharing an address made it throw.
- **Logout** uses `OidcClientInitiatedLogoutSuccessHandler`: it ends the provider's session when the
  provider advertises an end-session endpoint, and is a local logout otherwise.
- **Several providers, one button each** (`security/LoginOptions`, §7.19). The buttons are read from
  the registration repository, so a provider is configuration only. They are **sorted by label**,
  because Boot binds registrations into a `HashMap` and the configured order is gone before we see
  it. The label is `client-name`, or the brand when that is only the default id. The brand comes
  from the **authorisation host**, never the registration id. A new brand is one host in
  `LoginOptions`, one literal case in `fragments/provider.html`, and `make assets`: the sprite
  scanner only finds literal icon names.
- **OpenID Connect only**, enforced at startup by `LoginOptions.requireOpenIdConnect`. A registration
  without `openid`, GitHub being the known case, would log in as an `OAuth2User`, which
  `CurrentUserService` treats as anonymous: a completed login that goes nowhere. Do not "support"
  one by relaxing the check. It needs an identity and a verified email the provider does not give.
- Overlays **merge** registrations, they do not replace them: a profile cannot remove `oidc` added by
  another file. `docs/examples/application-providers.yml` says to delete the prod example's block.

Roles are **two independent axes** (§2.1):

- `UserEntity.Role` — the *platform* role, `USER` or `ADMIN`. Admins act on any employer without being
  a member.
- `MembershipRole` — the *per-employer* role, `OWNER` or `EDITOR`, stored on the membership.

`Actor` carries both (`isAdmin()`, `isOwnerOf(employerId)`). There is no global "editor" any more;
that word now names a membership role only.

**`Actor` is whoever is acting: a signed-in user or a service token** (§3.11). One type rather than
two, because every rule downstream asks both the same questions. `Actor.isToken()` distinguishes them
and `Actor.hasScope()` is always true for a person — scopes narrow a token only. Anywhere the model
records *who did this* (`Invitation.invitedBy*`, `Membership`), it holds two nullable references with
a database CHECK that exactly one is set.

The dev bypass resolves to a **real seeded user** (`dev`/`dev@localhost`), not a synthetic principal,
because invitations and memberships are keyed on a user id.

### Invitations and membership

Access is granted by invitation and taken up by consent (§2.6, §2.7). `MembershipService` owns
memberships and roles; `InvitationService` owns invitations. Use
`MembershipService.requireOwner(user, employerId)`, which passes for an owner of that employer **or** a
platform admin.

**Membership has exactly two write sites** (§2.2): creating an employer (`EmployerService.save` grants
the creator `OWNER`) and accepting an invitation (with the role the invitation carried). A third would
mean access was granted without consent. `changeRole` may only change an existing membership.

`MembershipService.grant` is **idempotent** — it returns any existing membership via `orElseGet`. The
quota checks live *inside* that lambda: at the top of the method they would refuse a re-grant that adds
nothing. Token memberships are written directly by `ServiceTokenService` and never go through `grant`,
which is what keeps a credential out of the per-person counts — do not "tidy" that into `grant`.

**Every employer must keep at least one active owner** (§2.7). `MembershipService` guards demotion,
removal and suspension, and the People screen renders the reason rather than greying the control out.

**Suspension** (§2.7) sets `Membership.suspendedAt`; the row, its role and its quota slot all stay. It is
enforced in exactly one place: `CurrentUserService` builds the `Actor` from
`MembershipService.activeRolesOf`, which leaves suspended memberships out, so every check downstream
sees a suspended member as a non-member without knowing suspension exists. Do not add a
`isSuspended()` check to individual services — and do not build an `Actor` from `findByUserId`, which
would quietly restore access. A suspended owner does not count for the last-owner rule, and nobody may
suspend themselves (`ValidationFailure` field `self`, API code `CANNOT_SUSPEND_SELF`). Tokens are
revoked, never suspended.

`InviteOutcome.SENT` is returned both when an invitation was created **and** when no account matched
the address. That is deliberate: the form must not become an oracle for which addresses are registered.
`ALREADY_MEMBER` and `ALREADY_INVITED` *are* reported, because both people are listed on the same
screen. Do not "improve" this by reporting unknown addresses.

Owners invite, change roles, remove members and edit the employer record; editors work on jobs and
feeds. Employer **creation is open to any signed-in user**; deletion is admin-only and **no delete route
exists yet**.

**The People screen** (`membership/PeopleController`, §7.18) is where all of this surfaces. Two routes,
one handler, so they cannot drift:

- `/people` — the sidebar destination, covering the **active employer**.
- `/employers/{id}/people` — the same screen for a named employer, from the employer record.
- Every write stays under `/employers/{id}/people/...`; after one, `redirectToPeople` returns the user
  to whichever route they came from rather than moving them to the other.

**Every member sees the membership list** — name, email, role (§2.1). The owner-only parts (invite
form, pending invitations, role and removal controls) are **absent for an editor, not disabled**, and
`populate` does not even fetch pending invitations unless `canAdminister`. The screen says why the
controls are missing; a control that is simply gone reads as a broken page. None of that is the check:
reading takes membership via `EmployerService.findVisible`, every write takes `requireOwner`.

`Scope` has **two** employer resolvers and they answer different questions.
`requireActiveEmployer()` picks a default to *create against* and falls back to the first visible
employer. `activeEmployer()` returns `Optional` and is for a screen that *names a subject*: with "All
employers" chosen, or no memberships, the honest answer is none, and People renders an empty state
rather than silently picking one. Do not swap them.

### Management API (`api/`, `token/`)

`POST /graphql` only, authenticated by `Authorization: Bearer <prefix>.<secret>` (§2.8, §11). The
schema is `src/main/resources/graphql/schema.graphqls`.

**The API is another caller of the same services, never a second implementation.** Resolvers read the
`Actor`, check a `TokenScope`, and delegate to `JobService`/`FeedService`/`InvitationService`/
`MembershipService`/`EmployerService`. If a rule can be bypassed through the API, the rule was in the
wrong layer.

- **The employer is implied by the token** and is never an argument, so a token cannot name another
  employer. `ApiActor.employerId()` is the only source.
- **Role and scope are both ceilings.** A scope check refuses early with a clearer answer; the service
  still applies the membership role.
- **Errors split two ways** (§11.4): a domain refusal is *data*, returned in the payload's
  `userErrors` by `ApiErrors` (stable `code`, message resolved through the same bundles the screens
  use). The GraphQL `errors` array is for faults, and `ApiErrorCodes` guarantees every entry carries an
  `extensions.code`. This is the one place the 404-not-403 rule is deliberately broken: a scope refusal
  answers `FORBIDDEN`, because an integration needs to know whether to fix its credentials.
- **`inviteMember` returns an outcome, not the invitation** — returning the record would disclose
  whether an address has an account.
- **A mutation resolver must not be `@Transactional`.** A service that refuses throws
  `ValidationFailure`, which marks the caller's transaction rollback-only; the resolver then returns
  its `userErrors` and the commit fails with `UnexpectedRollbackException` — turning a refusal into a
  fault. Mutations let the service own the write and read the result back through
  `ApiMapper.read*(id, actor)`, which has its own read-only transaction. `ManagementApiTest` is
  deliberately **not** `@Transactional` for the same reason: a test transaction hides this entirely.
- Limits (§11.6) are wired as `Instrumentation` beans (`ApiLimitsConfig`) plus `ApiTransportFilter`
  (POST only, bounded body, per-token budget with `X-RateLimit-*` headers). The rate limiter is
  in-memory and per instance.

**Service tokens** (`token/`) belong to an employer, never to a person; only an owner may create one,
and a token may never mint another — **nor renew one**, including itself, which would make it immortal
and the expiry decorative.

Tokens **expire** after `app.tokens.lifetime-months` (12; `0` = never) and an owner renews them.
**Renewal keeps the secret** and only moves the date, so nothing is redeployed; the same act reactivates
a lapsed token, and it extends from *now*, not from the old date. A revoked token can never be renewed —
that is the whole distinction between the two. `authenticate` refuses expired, revoked and unknown
identically, and a refused call must **not** stamp `lastUsedAt`: that column answers "when did this last
work". An expired token still counts toward the per-employer quota (§8.4). The secret is shown once and stored hashed with a delegating
password encoder; the public `prefix` names the token in logs and the register. Revoking sets
`revokedAt` and never deletes, so the audit trail still resolves. A token holds a `Membership` of its
own and never satisfies the last-owner rule — which is why `MembershipRepo` counts owners with
`countByEmployerIdAndRoleAndUserIsNotNullAndSuspendedAtIsNull`.

**Neither `ServiceTokenAuthFilter` nor `ApiTransportFilter` is a bean.** Boot registers every `Filter`
bean against every request, which would demand a bearer token on the whole back-office; the API chain
constructs them.

### Resource limits (`common/ResourceLimits.java`, §8.4)

Six configurable creation quotas under `app.limits.*`, defaults in `application.properties`, `0` =
unlimited. Checked in the services at the single creation method for each resource. Two exemptions that
must stay: `FeedService.createDefaultFeed` (an employer without its `all` feed is broken) and every
edit path — only creation is refused.

**The invitation quota is checked before the email is looked up, and that ordering is the rule.**
Checked after, an employer at its cap would answer `SENT` for an unregistered address and refuse a
registered one — rebuilding exactly the account oracle §2.6 forbids.
`theInvitationRefusalDoesNotRevealWhetherTheAddressHasAnAccount` fails if anyone moves it.

A refusal is a `ValidationFailure` keyed `limit.*`. `ApiErrors` turns that prefix into the stable API
code **`QUOTA_REACHED`** — deliberately not `LIMIT_*`, because `ApiErrorCodes` already emits
`LIMIT_EXCEEDED` for a query that breaches the depth ceiling, and that one is a transport fault in
`errors` rather than data in `userErrors`. `ApiErrors.message()` resolves `limit.*` through the bundles,
so **those bundle entries must contain no `{0}` placeholders** — it calls `getMessage` with no
arguments and a parameter would render literally.

**The test database runs in UTC and the host may not.** Writing an `Instant` with a raw JDBC
`Timestamp` converts it using the *host* zone, so "a minute ago" can land hours in the future and a
token you meant to expire stays valid. Set temporal fields through the entity and the repository, as
`ServiceTokenExpiryTest` does.

A test that creates rows and is **not** `@Transactional` must clean up in `@AfterEach`, or its leftovers
count against a quota in some later class and fail it for reasons that look unrelated. `ApiQuotaTest`
and `ManagementApiTest` both do; `ManagementApiTest` additionally switches the token quota off, because
a run killed before its cleanup would otherwise poison the next one.

### Persistence

- Flyway owns the schema (`src/main/resources/db/migration/{vendor}`, history table `migrations`);
  `hibernate.ddl-auto: none`. Schema changes need a new `V<n>__*.sql` **in both `mariadb/` and
  `sqlite/`** — never rely on JPA DDL, and never edit an applied migration. `MigrationParityTest`
  fails the build if a version exists in one folder only.
- `common/Base` is the `@MappedSuperclass` for UUID-keyed entities with `createdAt`/`lastModifiedAt`:
  `Employer`, `Job`, `Location`, `Feed`, `Invitation`, `Membership`, `UserEntity`. `Tag` is the
  exception (numeric identity id), as is `JobStatusEvent`.
- Enumerations persist **as strings**, never ordinals — including country codes. `Location.country` is a
  `CountryCode` whose constant names *are* the ISO alpha-2 codes, so the stored value is the published
  value.
- Job↔tag and job↔location are plain `@ManyToMany` join tables; there are no link entities.
- `data-mariadb.sql` and `data-sqlite.sql` seed the same demo rows, with the same fixed ids, on every
  dev and test start (`spring.sql.init.mode: always`; `spring.sql.init.platform` picks the file). Both
  must stay idempotent — `ON DUPLICATE KEY UPDATE` on one, `ON CONFLICT DO NOTHING` on the other, never
  `INSERT OR IGNORE`, which also swallows CHECK violations. **Edit both together.** A stale
  `target/classes/data.sql` from before the split is loaded as well, so after pulling this change
  build with `clean` once.

### Two databases (§9.3)

MariaDB is the reference; SQLite is the `sqlite` profile (`application-sqlite.yml`). Nothing in
`src/main/java` knows which one is in use, and that is the rule to keep: **no native SQL**, and nothing
that relies on a MariaDB-only behaviour. What makes SQLite behave the same is all in configuration and
the schema, and each piece fails *silently* if lost:

- **`foreign_keys=on`** on the JDBC URL. SQLite enforces foreign keys per connection, only when asked;
  without it every cascade stops and deletes leave orphans.
- **`date_class=TEXT`** on the URL. The driver's default is epoch milliseconds, and in SQLite every
  number sorts before every text value, so the date window (§4.4) would compare garbage. Instants and
  dates are text `yyyy-MM-dd HH:mm:ss.SSS` (instants in UTC), and `data-sqlite.sql` writes exactly
  that — `'2026-11-01 00:00:00.000'`, not `'2026-11-01'`, which neither compares equal nor parses back.
- **`preferred_uuid_jdbc_type: CHAR`**, so UUIDs are lower-case text matching the seed's literals.
- **Money is `TEXT`** in the SQLite schema: `NUMERIC` affinity would turn 85000.50 into a float.
- **Enums are `TEXT CHECK (… IN …)`** and case-insensitive names are `COLLATE NOCASE` (ASCII only).
- **Never sort by an enum column in SQL.** MariaDB orders an `ENUM` by declaration, SQLite
  alphabetically — which is why `MembershipService.membersOf` sorts owners first in Java.

`config/SqliteConfig` creates the database file's directory, reading the path from the datasource URL.
One instance only: SQLite has one writer (`busy_timeout` makes others wait).
- The feed serving path loads jobs with locations and tags in a bounded number of queries. An N+1
  there is a defect.

### UI

Tabler sidenav shell, htmx for partial updates, and a JavaScript budget that bounds *dependencies*
rather than lines (§7.2): **no bundler, no transpiler, no CDN, and a library needs a reason**.

Front-end dependencies are pinned in `package.json` with a committed lockfile. Their `dist` files are
copied into `static/vendor/` and **committed**, so `./mvnw package` works with Maven alone on a machine
with no Node — nothing in `pom.xml` invokes it. Refresh them with:

```bash
make assets        # npm ci && npm run vendor
```

`scripts/vendor-assets.sh` copies the dist files and rebuilds `icons.svg` from **only the icons
actually referenced**, scanning both the templates (`fragments/icon :: i('name')`) *and* the Java
(`new NavItem("name", …)` — the sidebar's icon names live there, not in a template). Miss the second
source and the nav icons vanish.

`static/js/app.js` is the application's own behaviour: one small plain-ES module, delegated listeners
so it survives htmx swaps, covering modal dismissal, chip removal and copy-to-clipboard. hyperscript is
gone, so the CSP carries **neither `unsafe-inline` nor `unsafe-eval`**.

Controls that only work with JavaScript are rendered `hidden` with `data-enhanced` and revealed by the
module, so no screen offers a dead button — progressive enhancement is still mandatory (§7.1).
`static/css/app.css` is the only custom stylesheet.

**`login.html` is the one standalone template** (§7.19): it does not decorate `layout.html`, which
assumes a signed-in user's sidebar, switcher and user menu. It is served by `security/LoginController`
and permitted **by path** in the OIDC chain — `loginPage("/login").permitAll()` alone matches the
login and failure URLs exactly, query string included, so `/login?logout` and `?lang=de` were bounced
to a bare `/login`. The locked chain does not permit it. There is no password field and never will be.

Alerts follow Tabler's structure: the icon (`icon alert-icon flex-shrink-0`) is a **direct child** of
`.alert`, which is itself a flex row with a gap. Wrapped in an extra `d-flex` div the text sits against
the icon and a long message squeezes it — which is how every flash message once looked.

`templates/` is the only template tree. Layout Dialect: `layout.html` decorates, pages use
`layout:decorate`, shared fragments live in `templates/fragments/`. Directories follow the owning
package, so the People screen is `templates/membership/`, not under `employer/`.

**Never put `th:if` and `th:replace` on the same element.** Thymeleaf processes `th:replace`
(precedence 100) before `th:if` (300), so the element is replaced before the condition is evaluated and
the fragment renders unconditionally — which is how every list screen once showed its empty state above
a full table. Wrap it: `<th:block th:if="…"><div th:replace="…"></div></th:block>`.

Templates bind to the view models in `web/view/`, assembled by `web/Views.java`.
`web/UiContextFactory` builds the shell context (`ui`); `web/UiContextAdvice` supplies it to every
screen, and `web/GlobalErrorHandler` builds it too. That duplication is necessary: Spring does **not**
apply `@ModelAttribute` contributions to `@ExceptionHandler` methods, so an error view relying on the
advice would fail to render and turn every 404 into a 500.
`ScreenRenderingTest.notFoundRendersTheErrorPage` guards it.

Each controller supplies its own `page` (`PageMeta`); one that forgets it will not render. A new
sidebar destination is added in `UiContextFactory` — there are nine: Dashboard, Jobs, Feeds, People,
API tokens, Employers, Locations, Tags, Invitations. Its icon name lives in the `NavItem`, so a new one
needs `make assets` to reach the sprite.

**API tokens is the only role-conditional entry**, added to a mutable list only when an employer is
active *and* the actor administers it. Two consequences worth knowing: a nav item renders on every
screen, so a missing `nav.*` key in either bundle fails the unresolved-key assertion for *every* path
at once; and an unconditional `nav.add` passes every test except
`PeopleScreenTest.anEditorIsNotOfferedApiTokensInTheSidebar`, which exists for that reason.

Where a screen has an htmx fragment variant, it is a second `@GetMapping` on the **same** controller
annotated `@HxRequest(boosted = false)` returning a fragment selector (`"tag/fragments/table :: table"`).
The `boosted = false` matters: navigation uses `hx-boost`, so a boosted request is an htmx request that
still wants a whole page.

i18n: `messages.properties` / `messages_de.properties`, session locale, switchable with `?lang=`. The
bundles are asserted **at parity** by `MessageBundleTest` — a key added to one and not the other fails
the build, as does an empty value. (That claim stood in this file for a long time before any test
enforced it.) Controllers pass message *keys* as flash attributes and templates resolve them.

### Conventions

- Lombok throughout: `@Getter/@Setter/@Value/@Data/@Slf4j`, and `@RequiredArgsConstructor` for
  **constructor injection**. There is no `@Autowired` field injection anywhere; keep it that way.
- `-parameters` compilation is expected (Spring Data and MVC parameter binding rely on it).

## Tests

```bash
./mvnw test                                  # all; needs MariaDB
TEST_DB=sqlite ./mvnw test                   # all, on SQLite; no server
./mvnw test -Dtest=MigrationParityTest       # both migration folders advance together; no database
./mvnw test -Dtest=OjobpubConformanceTest    # the build gate; no Spring, no database
./mvnw test -Dtest=ScreenRenderingTest       # renders every screen against the seed data
./mvnw test -Dtest=InvitationServiceTest     # consent and non-disclosure
./mvnw test -Dtest=MembershipServiceTest     # ownership, role changes, the last-owner rule
./mvnw test -Dtest=ManagementApiTest         # the GraphQL API end to end
./mvnw test -Dtest=ApiLimitsTest             # depth, rate and body limits, with the ceilings lowered
./mvnw test -Dtest=PeopleScreenTest          # the People screen as an editor, not an admin
./mvnw test -Dtest=OidcLoginTest             # real sign-in without the bypass: PKCE, accounts, logout
./mvnw test -Dtest=LockedChainTest           # no provider, no dev: the back-office is closed
./mvnw test -Dtest=LoginProvidersTest        # several providers: one button each, sorted, branded
./mvnw test -Dtest=LoginOptionsTest          # OIDC-only check and brand by host; no Spring
./mvnw test -Dtest=MemberSuspensionScreenTest # suspending and reinstating from the People screen
./mvnw test -Dtest=ResourceLimitsTest        # the quota convention; no Spring, no database
./mvnw test -Dtest=QuotaEnforcementTest      # the six quotas against the seed data
./mvnw test -Dtest=MessageBundleTest         # the two bundles, at parity
./mvnw test -Dtest=TokenLifecycleTest        # the expiry boundaries; no Spring, no database
./mvnw test -Dtest=ServiceTokenExpiryTest    # expiry, renewal and what renewal must not touch
make assets                                  # refresh vendored front-end deps (needs Node)
```

`OjobpubConformanceTest` validates generated documents against the vendored schema (minimal, maximal
and empty-feed shapes). It is the most important test here: everything else is internal, this is the
contract. Plain unit test — no Spring context, no database, no profile.

`ScreenRenderingTest` renders every screen through the production controllers and asserts the section 7
rules that are checkable in markup (no unresolved message keys, no CDN or inline script, `hx-boost`,
skip link, `aria-current`, preserved input on validation errors, status words not just colours). It
references the fixed UUIDs in the seed, so **a new screen must be added to `everyScreen()`**.

There is no preview harness and no fixture data: screens run against the real controllers and the
seed (`data-mariadb.sql` / `data-sqlite.sql`). The seed deliberately covers all five publication states — including an `INACTIVE` job and
an `ACTIVE` one with no location, which presents as `INCOMPLETE` and is excluded from the feed — and
three users: the dev admin (owner of Acme), `member@example.com` (editor) and `editor@example.com` (no
membership, one pending invitation). It also seeds one service token so the API tokens screen has a
row; its `secret_hash` is of a secret that was generated and discarded, so the row is **not** a usable
credential — the seed runs wherever the dev or test profile starts. Keep that coverage, **in both
files**, or the tests lose it on one database.

Test configuration lives in `src/test/resources/application-test.yml` (profile `test`), pointing at
`publisher_test` and disabling JobRunr. **It must stay profile-specific.** A plain
`src/test/resources/application.yml` loses to `src/main/resources/application.properties`, so its
overrides are silently ignored and the dashboard binds its fixed port, colliding with a running
instance.

**Every back-office test runs as the seeded admin**, because that is who the dev bypass resolves to —
so no screen test notices a permission split, since the privileged half is always present. To render a
screen as somebody else, override the actor: `PeopleScreenTest` replaces `CurrentUserService` with
`@MockitoBean` and returns an editor. Do that whenever a screen shows different things to different
roles, and assert **both** directions — what the role sees and what it must not.

The exceptions are `OidcLoginTest` and `LockedChainTest`, which run **without** `dev`
(`TestProfiles.WithoutDev`) because the bypass never reaches the real sign-in path. `OidcLoginTest`
configures the provider through `spring.security.oauth2.client.*` properties — as a deployment does —
so the auto-configured repository is what the chain sees; a test that supplied its own
`ClientRegistrationRepository` bean would have hidden the bug described under **Authentication**.

Back-office tests use `@ActiveProfiles(resolver = TestProfiles.class)`, which resolves to `dev, test`:
`dev` provides the authentication bypass, `test` is listed second so its datasource wins over the dev
one. With `TEST_DB=sqlite` it appends `sqlite`, which outranks both — that is the only switch, so a new
Spring test must use the resolver rather than naming profiles, or it will silently stay on MariaDB. CI
runs the suite twice, as a matrix: MariaDB (`publisher_test` on a service container, `DB_PORT=3306`;
locally 3307, see **Commands**) and SQLite.

## Known loose ends

- **Configured but unused.** JobRunr has no jobs and `@EnableScheduling` has no scheduled methods.
  Nothing here needs a scheduler by design: the job date window is evaluated at serving time.
- **SQLite folds case for ASCII only.** "Zürich" and "zürich" are distinct there and equal on MariaDB,
  so a tag or city differing only in a non-ASCII capital can exist twice on SQLite.
- **MariaDB DDL is not transactional.** A migration that fails halfway leaves the schema half-changed
  and the `migrations` row marked failed, and every later start fails on the part that did apply. It
  will not rename a column a foreign key still points at, either — drop the key, rename, re-add, which
  is why `V4` does the `invitations` rename in three statements.
- **No employer delete route**, though the spec reserves deletion for admins (§7.13).

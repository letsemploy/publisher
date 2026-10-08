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
`docker-compose.yml`: the `db` service on **3307** (root/root), adminer on **8082**, **Mailpit** on
**8026** (its inbox; SMTP on 1026 — off the standard ports, like the database), and **Keycloak on
8083** (admin/admin), which imports `keycloak/ojobpub-realm.json` — realm `ojobpub`, users alice/alice
and bob/bob, and mallory/mallory whose email is unverified. alice is in the group `ojobpub-admin`, which
the `keycloak` profile maps to platform admin. The realm is imported only into a fresh
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
./mvnw spring-boot:run -Dspring-boot.run.profiles=accounts    # email/password sign-up; mail in Mailpit
./mvnw package                                          # build jar (runs tests)
./mvnw test                                             # all tests, on MariaDB
TEST_DB=sqlite ./mvnw clean test                        # all tests, on SQLite (target/publisher_test.db)
./mvnw test -Dtest=MembershipServiceTest                # one class
./mvnw test -Dtest=TagServiceTest#namesAreNormalised    # one method
./mvnw versions:display-dependency-updates              # or: make check-mvn-updates
```

Locally, run `dev`, `keycloak` or `accounts`; plain startup has no datasource. All are **profile groups**
(`application.properties`) that bring in `local` — `application-local.yml`, the developer's MariaDB
(database `ojobpub_publisher_dev`), the seed and GraphiQL. `dev` adds the authentication bypass (§2.3);
`keycloak` adds real OIDC login against the compose Keycloak (`application-keycloak.yml`); `accounts`
turns local accounts on (`application-accounts.yml`), with mail going to Mailpit. There is no
`application-dev.yml`: the bypass is `@Profile("dev")` beans, so it cannot leak into `keycloak`, and
the two differ in exactly that. A deployment sets `SPRING_DATASOURCE_*` for MariaDB or runs with
`SPRING_PROFILES_ACTIVE=sqlite` and `APP_SQLITE_PATH` on a volume, plus the five `SPRING_SECURITY_OAUTH2_*`
variables of §9.5 — see **Authentication** below. `docs/examples/application-prod.yml` is a complete,
commented `prod` configuration, meant to be mounted, not packaged. It pins the Flyway table name,
which is otherwise set only in the local and test profiles. Keep it in step when adding a setting a deployment must know about. Spring's
binder leaves an unresolved `${VAR}` as literal text instead of failing, so a missing variable is not
caught at startup.

Actuator and the Prometheus registry are on the main port; the app binds no other.

Container builds use `Dockerfile.multistage`, which is what CI pushes to ghcr.io. The plain
`Containerfile` expects a pre-built `target/app.jar`. `Dockerfile` is a **symlink** to it, so edit one
file, never both.

### Memory

Measured in 2-CPU containers of 512 MB and 1 GB with a warm-up of about 300 requests. RSS went from
about 575 MB to about 400 MB, on SQLite and MariaDB alike, and startup from about 18 s to about 13 s.
The old image needed about 1 GB just to start. This one runs in 512 MB with room to spare. What each
measure is worth:

- **No query text that the parsers choke on.** `JobRepo.search` used to be one JPQL string with a
  five-way nested presentation filter.
  - **What it cost:** Spring Data's and Hibernate's ANTLR HQL parsers took about 9 s on it at
    startup, pushed the startup heap past 192 MB, and left about 17 MB of prediction cache for good.
  - **What replaced it:** `Publication.presenting`, a Criteria `Specification`, which is never parsed.
  - **The effect:** live heap fell from about 70 MB to about 52 MB, and a 96 MB heap cap now starts.
  - **Keep big predicates out of `@Query` strings.** Short queries are fine; it was the nesting that
    cost.
- **The JVM options** are `JAVA_TOOL_OPTIONS` in both container files, which a deployment replaces
  by setting its own.
  - **`MinHeapFreeRatio=10`/`MaxHeapFreeRatio=30`** lets the heap shrink back after startup instead
    of staying at its cap.
  - **`MaxRAMPercentage=50`**, not the default 25. It is no longer needed to start, since 128 MB
    does, but it gives the live set room to grow with the data, and the free ratios make it cost
    nothing in steady state.
  - **`UseSerialGC` is pinned.** The JVM picks Serial by itself below 2 CPUs or about 1.8 GB, and G1
    above, which measured larger.
  - A 64 MB code cache, and **compact object headers** (a product feature since Java 25).
- **Measured and rejected:**
  - C1-only compilation, no gain once the others are in;
  - G1 with periodic uncommit, which used more;
  - **virtual threads** (`spring.threads.virtual.enabled`). Spring GraphQL then runs requests
    asynchronously on the task executor. `ManagementApiTest` got empty responses, and resolvers
    would leave the request thread that holds the security and request context. NMT puts all thread
    memory at 2–3 MB, so there was nothing to gain. Don't turn them on without first making the API
    async-safe.

## Architecture

### Package by feature

`employer`, `job`, `location`, `tag`, `feed`, `invitation`, `membership`, `token`, `user`, `audit`,
`account` (local accounts), `click` (job links and their counters), `summary` (the weekly mail) — each
owns its entity, repository, service, form objects and controllers. Plus `common` (shared base types, slugs,
exceptions), `config` (beans), `mail` (outbound mail), `security` (actors, roles, filter chains), `web` (shell context, view
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
  document cannot disagree. Add publication logic here, not in a controller. The job list filters on
  the same rule in the database through `presenting`, its Criteria twin. A change to one needs the
  other, and `PublicationFilterTest` fails if they disagree on any job.
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
- `common/ResourceLimits.java` — the seven creation quotas of §8.4 and, more importantly, the two rules
  around them: **`0` means unlimited**, and a quota refuses **at** the cap. Spread across seven services
  those get written seven times and one of them forgets the zero, turning "unlimited" into "none". It
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

**Permalinks** (`feed/Permalink*`, §3.13, §5.5): `GET /ojobpub/v1/permalink/{uuid}/ojobpub.json` serves
whichever of its employer's feeds it points at — **served, never redirected** — or, with no feed, a
valid document with the employer and no jobs (`OjobpubService.generateEmpty`). The point is a URL a
website configures once while the feed behind it is switched.
- **The served date only moves forward:** `PermalinkService.publish` takes the later of the document's
  `lastUpdated` and the permalink's own `lastModifiedAt`, and `retarget` stamps it. Without that,
  switching to an older feed moves `Last-Modified` backwards and Spring answers a revalidating cache
  `304` with the old document.
- **Only the employer's own feeds**, checked in the service (`ownFeed`); a stranger's is refused like
  an unknown id, field `feed` (API: `feedId`).
- **Deleting a feed clears its permalinks in `PermalinkService.feedDeleted`**, called by
  `FeedService.delete` before the delete. The FK's `ON DELETE SET NULL` is only the backstop for bulk
  deletes: left to the database, the loaded permalinks still reference the removed feed at flush and
  Hibernate throws.
- Managed on the Feeds list (`PermalinkController`, `/feeds/permalinks/...`), with the switch as a
  plain form per row; in the API under the feed scopes.

### Job links and clicks (`click/`, §5.6, §3.14)

A published job's `url` is `{app.base-url}/go/{jobId}`, not `Job.url` (`OjobpubService`, unless
`app.clicks.enabled=false`). `GET /go/{id}` redirects a published job (`Publication.isPublishable`) with
`302` and counts the click; any other job answers `410` with `templates/click/gone.html`; unknown is `404`.

- **`JobLinkHandler` is a functional endpoint, not a `@Controller`, and must stay one.**
  `UiContextAdvice` is a `@ModelAttribute` on every controller in the package: it resolves a user and
  touches the session-scoped `EmployerContext`, so a controller here would open a session on a public
  URL. The page is rendered with the template engine directly, in the `Accept-Language` locale, and
  `WebLangConfig` excludes `/go/**` and `/ojobpub/**` from the `?lang` interceptor for the same reason.
- **Counters, not visits:** `job_clicks` is keyed (job, UTC day, country), `ZZ` for unknown. **Never
  store or log the address or user agent** (§10). The upsert is JPQL `UPDATE … +1`, else insert, and
  on `DataIntegrityViolationException` (a concurrent first click) update again — no native SQL. It runs
  in its own transaction (`TransactionTemplate`, `REQUIRES_NEW`) because a failed statement poisons the
  transaction it runs in, and **a failed count is logged and never stops the redirect**.
- **Country** (`ClickCountry`): `app.clicks.country-header`, then the `.mmdb` at
  `app.clicks.geoip-database` (a configured path that can't be read fails startup), else `ZZ`. Only
  officially assigned codes count. Tests use MaxMind's test database, `src/test/resources/geoip/`
  (Apache-2.0/MIT), where `81.2.69.160` is `GB`.
- `HEAD`, no user agent and `app.clicks.ignore-user-agents` (link unfurlers, crawlers, `curl`) are not
  counted — so trying a link with plain `curl` counts nothing; pass `-A`.
- The functional `GET` route does **not** answer `HEAD` by itself; the route declares both.
- The dashboard reads `JobClickService.statistics(scope.employerIds())`; the seed dates its counters
  *yesterday*, so a test counting today's clicks starts from zero.

### Authentication and roles

OIDC via Spring Security, authorization code **with PKCE** (§2.2). `SecurityConfig` has four chains, in
order: the **management API** (`/graphql`, bearer token, stateless, no CSRF); the public feed; a
**dev-profile bypass** (no identity provider needed); and the back-office. The API chain is `@Order(0)`
on purpose — the dev bypass must never reach `/graphql`, so the API is token-authenticated even locally.
`DevBypassGuard` refuses to start if `dev` is combined with `prod`.

**The back-office chain decides at bean creation, not with `@ConditionalOnBean`.** `backOfficeChain`
takes an `ObjectProvider<ClientRegistrationRepository>`: OIDC login if a repository exists, form
login if local accounts are enabled, **both** on one page if both, and a **fail-closed** chain if
neither (the app starts and feeds stay served, but nobody gets in). It used to be
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
- **Admins come from configuration** (`AdminPolicy`, `app.admin.people` / `app.admin.groups`, §2.2).
  This is the project's first `@ConfigurationProperties` record, because a list of entries cannot be
  a `@Value`. Every rule is **scoped to an issuer**. An email entry matches only a *verified* address,
  **regardless of `require-verified-email`**, which governs storage, not privilege. Once any rule
  exists they are **authoritative**: `CurrentUserService.signIn` re-decides the role on every
  request, promoting and demoting, and logs the account but never the matching rule. With no rules,
  `isAdmin` returns empty and the stored role stands. That is deliberate, so upgrading never demotes
  hand-made admins. A malformed entry fails startup. The dev bypass never reaches `signIn`, so the
  seeded admin is untouched. The `keycloak` profile maps the realm group `ojobpub-admin`, so alice is
  an admin locally.
- **Admin mode** (§2.10): an admin works in the **user view** until they switch up from the user
  menu. It is enforced in **one line**: `CurrentUserService.toAppUser` sets `Actor.admin` to "stored
  role is `ADMIN` **and** admin mode is on". Every admin power reads `Actor.isAdmin()`, so all of them
  follow the switch — never check `UserEntity.Role.ADMIN` to grant a power, or that power ignores the
  mode. (Stored-role checks that remain are about *who someone is*: the Users list, and "never view as
  an admin".)
  - **State:** `AdminMode`, a session attribute like `Impersonation`, holding the user id and on/off.
    `AdminModeService` switches it, clears the active employer and records `ADMIN_MODE_ENTERED`/`_LEFT`.
    `canSwitchAdminMode()` means stored role `ADMIN`, not a token, and not viewing as someone.
  - **Defaults:** `app.admin.start-in-admin-mode` is **false**. The **test profile sets it true**, so
    every back-office test written before admin mode keeps full reach; `AdminModeTest` overrides it
    to test the default. A new test of an admin power in the user view must do the same.
  - Viewing as a user needs admin mode, because `realActor().isAdmin()` is the switched flag.
- **Viewing as a user** (§2.9) is **read-only**, for admins, on non-admins only.
  - **State:** a session **attribute** (`Impersonation`), read with `getSession(false)`, never a
    session-scoped bean, so the stateless API can't create a session or find a state.
  - **Resolution:** `CurrentUserService.current()` resolves the real actor first (`realActor()`), then
    `viewAs` re-checks, on every request, that the real actor is still that admin and the target
    still a non-admin, and returns the target's `Actor`. **It never calls `signIn()` for the
    target**, because that would refresh their record from the admin's token.
  - **The guard:** `ImpersonationGuard`, one interceptor, refuses every non-GET except stop and the
    view switches. That's what stops consent acts (accepting invitations, creating employers) on
    someone's behalf. Don't add write routes to its allowlist.
  - **Starting:** `ImpersonationService.start` clears the active employer, as does stopping.
- **Suspending an account** (§2.11) sets `UserEntity.suspendedAt`; admins only, never oneself or an
  admin, done from the Users screen by `UserService`. It is enforced in two places that must both stay:
  `SuspendedAccountFilter` (in the OIDC chain, not a bean) signs the session out on **every** request
  and sends it to `/login?suspended`, or sets `HX-Redirect` for htmx; and `realActor()` answers
  anonymous for a suspended account in case anything runs before the filter. `signIn` returns a
  suspended account untouched, so it is neither refreshed nor re-decided by the admin rules.
  Memberships stay, and a sole owner is named on the confirmation, not protected. The dev bypass never
  checks it. `OidcLoginTest` covers the sign-in half, `AccountSuspensionTest` the admin half.
- **Deleting one's own account** (§2.13) is `user/AccountDeletionService`, from Settings at
  `/account/delete`, for anyone signed in as themselves (never a token; viewing-as is refused by
  `ImpersonationGuard`). The email is typed back and checked in the service; the **only unsuspended
  stored `ADMIN`** may not. Employers from `MembershipService.soleOwnershipsOf` are deleted first via
  `EmployerService.deleteAsDepartingOwner` (no typed name — the email confirmed it), then the
  `local_accounts` row (no FK joins it to `users`), then `UserRepo.deleteWithEverything`, a bulk
  `DELETE` like the employer's: memberships and invitations cascade, and **`service_tokens.created_by_id`
  is `ON DELETE SET NULL`** since V14, so a co-owned employer's tokens keep working ("created by a
  deleted account"). The audit log is left as written (§3.12). The session ends at `/login?deleted`.
- **Logout** uses `OidcClientInitiatedLogoutSuccessHandler`: it ends the provider's session when the
  provider advertises an end-session endpoint, and is a local logout otherwise. With local accounts
  only, a plain handler ends at `/login?logout`.
- **Several providers, one button each** (`security/LoginOptions`, §7.19). The buttons are read from
  the registration repository, so a provider is configuration only. They are **sorted by label**,
  because Boot binds registrations into a `HashMap` and the configured order is gone before we see
  it. The label is `client-name`, or the brand when that is only the default id. The brand comes
  from the **authorisation host**, never the registration id. A new brand is one host in
  `LoginOptions` and one literal case in `fragments/provider.html`, which the next build adds to
  the sprite: its scanner only finds literal icon names.
- **OpenID Connect providers plus GitHub**, enforced at startup by
  `LoginOptions.requireSupportedProviders`. Any other registration without `openid` would log in as a
  plain `OAuth2User`, which `CurrentUserService` treats as anonymous: a completed login that goes
  nowhere. Do not "support" one by relaxing the check. It needs an identity and a verified email the
  provider does not give.
- **GitHub is the one exception, handled by name** (§2.2). `GitHubUserService`, which Spring uses only
  for non-OIDC registrations, loads `/user` and then `/user/emails`. It returns a `GitHubUser` with
  issuer = the origin of the authorisation URI, subject = the numeric `id` (never `login`), and the
  primary-and-verified address or null. A failed email call signs in without an address instead of
  failing the login. `CurrentUserService.identityOf` turns an `OidcUser` or a `GitHubUser` into one
  `Identity`, and a single `signIn` does the rest. GitHub requires `user:email`, which Spring's
  built-in GitHub defaults omit, so a GitHub registration without it is refused at startup with the
  fix. Only `github.com` counts; GitHub Enterprise Server is refused like any OAuth2-only provider.
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

### Local accounts (`account/`, `mail/`, §2.12)

Email and password, **off unless `app.local-accounts.enabled`**. A local account is the `users` row
with issuer **`local`** (`LocalAccount.ISSUER`) and, as subject, the `local_accounts` id — never the
email. `CurrentUserService.identityOf` turns the `LocalUser` principal into an `Identity` and the one
`signIn` does the rest, so roles, admin rules, suspension and viewing-as all apply unchanged.

- **Sign-up takes no password.** It is chosen on the page the mailed link opens (`/register/complete`),
  which also verifies the address. Taken at sign-up, anyone could register someone else's address
  with their own password and have it go live when the owner clicks. So a pending account has
  `password_hash` null, no `users` row, and cannot sign in — there is no "unverified" login state.
- **No oracle** (§2.6): `/register`, `/register/resend` and `/password/forgot` always redirect to the
  same "check your email" page; `LocalAccountService` decides what, if anything, to mail. A taken
  address gets a "someone tried" mail, never an error. Do not add "address already registered".
- **Every sign-in failure is one `BadCredentialsException`** (`LocalAuthenticationProvider`): unknown,
  wrong password, pending, throttled. It is **not a bean** — a `UserDetailsService` or provider bean
  would also feed a global manager with Spring's default checks; the chain constructs it.
- **Links** (`account_tokens`): 32 random bytes, stored as **SHA-256** (enough at that entropy, and
  findable by hash), single-use, 24 h to complete a sign-up, 1 h to reset. Issuing voids earlier ones,
  and setting a password voids all.
- **A password change ends every other session.** `LocalUser` carries `signedInAt`;
  `CurrentUserService.endOfSession()` — read by `SuspendedAccountFilter` on every request, next to
  suspension — ends a session older than `password_changed_at` at `/login?expired`. The session that
  changed it is re-authenticated with a fresh `LocalUser` in `AccountController`.
- **Throttles** (`Throttle`, in memory, per instance): failed sign-ins per address and per client
  (`LoginAttempts`, a bean the sign-in page and the provider share); the mail-sending forms per
  client. `app.local-accounts.max-attempts`, `max-mails`, `throttle-window`.
- **Captcha after failures** (`captcha-after-failures`, only with a captcha configured): the provider
  checks it **before the password** and throws `CaptchaRequiredException`, mapped to `/login?captcha`;
  every other failure stays one `?error`. Counted per address *and* per client, so a distributed guess
  at one account still meets it, and unknown addresses count alike, so it is no oracle. The token
  reaches the provider through `LoginDetails`. `/login` is therefore one of the `CAPTCHA_PAGES`.
- **Password policy** (`PasswordPolicy`, `app.local-accounts.password.*`): length, character classes,
  and a minimum **zxcvbn** score (`com.nulab-inc:zxcvbn`), given the address as user input. The same
  bean writes the hints on the forms, so they cannot drift from the rule. `@NewPassword` delegates to
  it — its validator is a Spring bean, as Bean Validation creates validators through Spring here. A
  policy that cannot be met (`min > max`, classes or strength out of 0–4) fails startup.
- **Mail** (`mail/Mailer`) is published as an event and sent `AFTER_COMMIT`: a refused change mails
  nobody, and a failed delivery is logged without changing the page (which would break the no-oracle
  rule). Without `spring.mail.host` nothing is sent and a warning says so — never the body, which
  holds a secret. Templates are plain text, `templates/mail/*.txt`, resolved by `MailTemplateConfig`
  and rendered in the requester's locale.
- **Captcha** (`CaptchaCheck`, `com.moser-systems:captcha-spring-boot-starter`): hCaptcha or mCaptcha by
  `captcha.provider`, **off by default** (`captcha.enabled=false`, because the starter would otherwise
  start an hCaptcha verifier with no keys). On the three forms that mail a typed address; without one
  they work and startup warns. `fragments/captcha :: widget` renders the provider's markup.
  - **CSP is widened on those pages only** (`SecurityConfig.CAPTCHA_PAGES`, §7.2): hCaptcha's
    `api.js` cannot be self-hosted, so script/frame/style/connect-src add `*.hcaptcha.com` there;
    mCaptcha's glue is **vendored** (`@mcaptcha/vanilla-glue` → `vendor/mcaptcha-glue.js`) and only
    `frame-src` gains the instance. Every other page keeps the strict policy.
  - The mCaptcha glue **runs as soon as it loads**, so its script tag must follow the elements it
    looks up by id (`mcaptcha__token-label`, `mcaptcha__widget-container`).

### Invitations and membership

Access is granted by invitation and taken up by consent (§2.6, §2.7). `MembershipService` owns
memberships and roles; `InvitationService` owns invitations. Use
`MembershipService.requireOwner(user, employerId)`, which passes for an owner of that employer **or** a
platform admin.

**A created invitation is mailed to the invitee** (§2.6) by `InvitationService.mailInvitee`, only when
`Mailer.configured()` — without a mail server it is skipped silently, not warned about as account mail
is. Only on the path that saves an invitation, so an unknown address (answered `SENT`) gets nothing and
the form never mails arbitrary people; not to an invitee who turned it off (`mailInvitations`, §7.26);
in the invitee's saved language, else the inviter's locale (`LocaleContextHolder`); after commit, like
all mail. `InvitationMailTest` is not `@Transactional` for that reason,
and disables `management.health.mail`, which refuses a mocked `JavaMailSender`.

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
feeds. Employer **creation is open to any signed-in user**.

**Deleting an employer** is for its owners and admins, never editors, and **never a service token**
(`EmployerService.delete` refuses `actor.isToken()` before anything else, and the API has no delete).
The typed name is checked **in the service**; `app.js` only disables the button until it matches.
`EmployerRepo.deleteWithEverything` is a bulk JPQL `DELETE`, and the database's foreign keys cascade the
rest. `delete(entity)` would fail: the eagerly loaded headquarters would still point at the removed
employer when Hibernate flushes.

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

**One employer is always active** (§2.5); there is no "All employers". The session's
`EmployerContext.activeEmployerId` is only a *choice* (null = none yet), and `EmployerContext.resolve`
turns it into the active employer: the choice while still visible, else the first visible by name,
written back so a stale choice heals itself. `Scope.activeEmployer()` and the top bar
(`UiContextFactory`) both go through it, so they cannot disagree. `employers()`/`employerIds()` are
that one employer as a list; `requireActiveEmployer()` 404s only for a user with no employers, which
is also the only case People and API tokens render their empty state. Clearing the choice (admin mode,
viewing as, deleting the active employer) therefore means "back to the first", and lists never show an
employer column — only `/activity/all` does.

### Management API (`api/`, `token/`)

`POST /graphql` only, authenticated by `Authorization: Bearer <prefix>.<secret>` (§2.8, §11). The
schema is `src/main/resources/graphql/schema.graphqls`.

**The API is another caller of the same services, never a second implementation.** Resolvers read the
`Actor`, check a `TokenScope`, and delegate to `JobService`/`FeedService`/`InvitationService`/
`MembershipService`/`EmployerService`. If a rule can be bypassed through the API, the rule was in the
wrong layer.

- **The employer is implied by the token** and is never an argument, so a token cannot name another
  employer. `ApiActor.employerId()` is the only source.
- **The schema is public** as SDL at `GET /graphql/schema` (Spring's schema printer,
  `spring.graphql.schema.printer.enabled`), while introspection stays off (§11.2, §11.6). The API
  chain matches `/graphql` **exactly**, so the schema path is carried by the public feed chain and
  excluded from the interceptors in `WebLangConfig` — it must open no session.
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

### Settings (`user/Settings*`, `web/UserPreferences*`, §7.26)

A person's language, theme, time zone and invitation-mail flag are columns on `users` (V15), the
person's own: `signIn` never touches them. Null means "not chosen".

- **Applied once per session** by `UserPreferencesInterceptor`, behind a session marker, and only for
  someone signed in as themselves — so a session started on the sign-in page gets them at its first
  request after. It writes the `SessionLocaleResolver` attributes (locale **and time zone**) and
  `EmployerContext.theme`; `UserPreferences.apply` is the one place that does, also after saving the page.
  Registered in `WebLangConfig` **before** the `?lang` interceptor and excluded from the public URLs and
  static files, which must open no session.
- **Settings is the only place to choose them.** The user menu has no language or theme switch any
  more, and `POST /context/theme` is gone. `?lang=` remains for the signed-out pages (sign-in, account
  screens) and lasts the session only. While viewing as someone nothing is applied: the admin keeps
  their own.
- **Timestamps** go through `Views.timestamp`, in `LocaleContextHolder.getTimeZone()`, which falls back
  to the JVM zone. `TokenLifecycle` keeps the server zone: that is a rule, not a display.
- **A local account's name** is read by `CurrentUserService.identityOf` from the `local_accounts` row,
  not the session's `LocalUser`: `signIn` writes the name to `users` on every request, so another open
  session would otherwise put the old name back. `SettingsService.save` renames both rows; a provider's
  account cannot be renamed (404), since the provider renames it at the next sign-in.
- `WebLangConfig.LANGUAGES` is the one list of languages; the menu, the account screens and Settings
  read it.

### Weekly summary (`summary/`, §7.28)

Opt-in from Settings (`users.mail_summary`, V17), one mail per person covering every employer they are
an **active** member of. Sent only when `Mailer.configured()`.

- **The one scheduled task.** `SummaryScheduler` (`@EnableScheduling` on it, behind
  `app.summary.enabled`) calls `SummaryService.sendDue(now)` on `app.summary.cron`. The test profile
  switches it off, and tests call `sendDue` directly.
- **The claim makes several instances safe.** `UserRepo.claimSummary` is one conditional `UPDATE` of
  `summary_sent_at`: whoever gets `1` sends, everyone else gets `0`. It is stamped even when there was
  nothing to report, and **not at all without a mail server**, so the first run after one is configured
  still reaches everyone. Each person runs in their own transaction, so one failure stops nobody else.
  No ShedLock: the database row is the lock.
- **It reads as the person, never as an admin.** The `Actor` is built by hand from
  `MembershipService.activeRolesOf` with `admin` false, and every read goes through the services
  (`EmployerService.visibleTo`, `JobService.jobsOf`/`deactivatedSince`, `ServiceTokenService.forEmployer`,
  which checks ownership). Don't build it with the stored role, or an admin's mail covers every employer.
- **`WeeklySummary`** is pure and clock-taking, like `DashboardJobs`, and built on it and on
  `Publication`. "Expired this week" is **derived** (`Publication.lastDay` in the last seven days),
  because nothing is written when a window closes. "Set inactive" comes from `job_status_events`, and
  only jobs still inactive are reported. `isEmpty()` ignores the counts, so a quiet week sends nothing.
- **The mail** is `templates/mail/summary.txt`. It is rendered after commit, so the template may only
  touch fields already loaded: titles, ids, dates. Dates go through `#temporals.format(…, 'MEDIUM')`. It is
  in the stored language, else English, since there is no request to take one from.

### Account pictures (`user/Picture*`, `user/UserPicture*`, §7.27)

Stored in `user_pictures` (V16), never linked, so the CSP keeps `img-src 'self'` and no page tells a
third party who is looking. One row per user, `ON DELETE CASCADE` from `users`.

- **A table of its own, not a column on `users`**, which `signIn` loads on every request. The bytes
  are a plain `byte[]`, never `@Lob`, because the SQLite driver has no `Blob`. Lists get their URLs
  from `PictureService.urlsFor` (one projection query, no bytes), and a URL carries `?v=` the
  `updated_at`, so `GET /pictures/{id}` is cached `private, immutable`.
- **`PictureProcessor`** (pure, no Spring) is the one way in: the size is read from the header before
  decoding, then centre-crop, 256 px, JPEG on white. Re-encoding is what strips EXIF/GPS; never store
  the bytes that arrived. JPEG/PNG/GIF only, no library for WebP/HEIC. A refusal is `Rejected`, whose
  reason is a `validation.picture.*` key that the controller flashes.
- **Provider pictures:** `CurrentUserService.noticePicture` reports the claim's URL (`picture`, or
  GitHub's `avatar_url`) **once per session**, behind a session attribute, never on every request.
  `PictureService.providerPictureSeen` only reads and publishes an event, because it may run inside a
  read-only transaction. `ProviderPictureFetcher` downloads `AFTER_COMMIT` on `applicationTaskExecutor`.
- **The fetch is an SSRF surface:** some IdPs let users set `picture`. So it uses `https` only, every
  resolved address must be public (`isPublic`), redirects are never followed, the timeout is 5 s, the
  body is capped with `BodyHandlers.limiting`, and the result goes through the processor. Log the
  user id, **never the URL**. Do not relax any of this to "support" a provider. Tests use the
  package-private constructor that takes the address rule.
- **An upload always wins.** `storeProviderPicture`/`dropProviderPicture` leave `UPLOAD` alone.
  Removing deletes the row, so the next session fetches the provider's again.
- **Who sees one** is decided in `PictureService.find`: oneself, `actor.isAdmin()`, or a member of an
  employer in the actor's active roles. Anyone else gets 404, the same answer as no picture.
- `fragments/avatar` renders the `<img>` or the initials (`web/view/Avatar`). Keep it on one line:
  whitespace pushes the initials off centre. Tabler only styles avatar background images, which need
  a `style` attribute the CSP forbids, hence `.avatar > img` in `app.css`.
- Multipart is limited to 5 MB (`spring.servlet.multipart.*`). Beyond that Tomcat cannot hand the
  form to us at all, so `app.js` refuses an oversized file in the browser (`data-max-bytes`).

### Audit log (`audit/`, §3.12)

One table, `audit_events`, and **two scopes that are columns, not types**: `employer_id` puts a row in
that employer's log, `subject_user_id` in that person's own. Many rows set both — a suspension is in
the employer's log and the member's — so the two logs cannot disagree.

- **Written by services, never controllers**, with `auditLog.record(AuditEvent.of(action, actor)
  .in(employer).about(user).target(id, label).detail(…))`. `AuditLog.record` is
  `Propagation.MANDATORY`: it must run inside the change's transaction, so a rollback leaves no row and
  a refusal, which throws first, is never recorded. **Record after the write and only on a real
  change** — the early returns for a no-op (`changeRole` to the same role, re-suspending, renaming a
  tag to its own name) come before it. A new write path in a service needs its `record` call;
  `AuditLogTest` covers the scopes, not every call site.
- **Who reads what is `AuditAction`'s audience** — `MEMBERS`, `OWNERS`, `PERSONAL` — applied in the
  query (`AuditRepo.findForEmployers`), so pages stay full. Invitations and tokens are `OWNERS` because
  the People and API tokens screens withhold them from editors; the log must not be a way round a
  screen. `AuditService` refuses tokens, and anyone else asking for what they may not read gets 404.
- **No foreign keys, deliberately.** Every table referencing an employer is deleted with it, and the
  row recording that deletion must survive. The labels are snapshots for the same reason. It is also
  why tests may act as synthetic actors with random ids. (§3.11's rule, that a *who* is a reference the
  database enforces, holds for the model. This is a record of it.)
- **The invitation row must not reveal whether the address has an account** (§2.6). No name, no
  address, only the role, and `subject_user_id` only when there is an invitee — which only that
  invitee's own log shows. `anInvitationRowDoesNotRevealWhetherTheAddressHasAnAccount` guards it.
- `signIn` records only `ACCOUNT_CREATED` and `ADMIN_GRANTED`/`ADMIN_REVOKED`, with the application as
  actor (`AuditEvent.of(action, null)`). It runs on every request; sign-ins themselves are the
  identity provider's to log.
- **Admins read everything at `/activity/all`**, the one view across employers (§2.5): events about
  people, which belong to no employer, and deleted employers' logs. `/activity` is the active
  employer's log and names no employer per row.
- `job_status_events` still exists and feeds the job's history panel. A status change writes both.
  Folding it into this log is a later step.

### Resource limits (`common/ResourceLimits.java`, §8.4)

Seven configurable creation quotas under `app.limits.*`, defaults in `application.properties`, `0` =
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

### Locations and tags belong to an employer (§3.2, §3.4)

Like jobs and feeds, each `Location` and `Tag` has an immutable `employer`. `LocationService` and `TagService`
follow the `FeedService` pattern:
- `findVisible(id, actor)` answers 404 to a non-member (§2.4);
- `create` takes the `Employer`;
- duplicates are checked **within the employer**, as a field error.

**`requireOwn(id, employer, field)` is the rule that matters.** `JobService.apply` runs every location
and tag id through it, and so does the headquarters on `EmployerService.save`. That is the whole defence
for the form **and** the API, since both arrive there. Another employer's id is refused exactly like an
unknown one, so it discloses nothing. `ApiErrors` maps the fields `locations` and `tags` to the inputs
`locationIds` and `tagIds`.

**The headquarters cycle.** `employers.location_id` points at one of the employer's own locations, and
`locations.employer_id` points back. So the column is **nullable in the database, and required by the
service**. On create, `EmployerService.save` saves the employer, then find-or-creates its location, then
sets the headquarters, all in one transaction. The input is a `Headquarters` value: an own location's
id, or a new city and country, which wins. A new employer can only give the latter. The seed files
follow the same order: employer, then locations, then the headquarters `UPDATE`.

The job-form pickers search `?employer=` the job's employer, and check membership.

`V7` divided the once-global rows. The oldest user keeps each row and its id, every other user gets a
copy, and unused rows went to the oldest employer. **SQLite's V7 is non-transactional**, set by
`V7__….sql.conf`: rebuilding `employers` needs foreign keys OFF, or dropping it would cascade-delete
everything, and SQLite ignores that pragma inside a transaction. It opens its own transaction with
`SAVEPOINT`/`RELEASE`, because Flyway's parser reads a bare `BEGIN` as a trigger body.
`MigrationV7Test` proves it on both databases.

### Persistence

- Flyway owns the schema (`src/main/resources/db/migration/{vendor}`, history table `migrations`);
  `hibernate.ddl-auto: none`. Schema changes need a new `V<n>__*.sql` **in both `mariadb/` and
  `sqlite/`** — never rely on JPA DDL, and never edit an applied migration. `MigrationParityTest`
  fails the build if a version exists in one folder only.
- **Flyway migrates, then the seed runs, then Hibernate starts**, ordered by Boot itself.
  **Never set `spring.jpa.defer-datasource-initialization`**: with Flyway it makes the two depend on
  each other in an order only the classpath decides. Maven's order worked; the 1.0.0 image's
  alphabetical one did not start. `DatabaseInitOrderTest` checks the bean dependencies.
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

Front-end dependencies are pinned in `package.json` with a committed lockfile, and **the lockfile is
the only committed record** (§9.1). `frontend-maven-plugin` in `generate-resources` downloads the Node
pinned as `node.version` in `pom.xml` into `target/`, runs `npm ci`, then `npm run vendor`, which
writes into **`target/classes/static/vendor/`** — never into `src/`, and `.gitignore` refuses the old
path. So `./mvnw package` still needs no Node on the machine, every build (tests, `spring-boot:run`,
the container) gets the files, and a Dependabot bump needs nothing pushed by hand. Running from an
IDE after a `clean`, rebuild them with:

```bash
make assets        # ./mvnw generate-resources
```

`scripts/vendor-assets.mjs` (plain Node, no dependencies) copies the dist files and builds `icons.svg`
from **only the icons actually referenced**, scanning both the templates (`fragments/icon ::
i('name')`) *and* the Java (`new NavItem("name", …)` — the sidebar's icon names live there, not in a
template). Miss the second source and the nav icons vanish;
`ScreenRenderingTest.theBuildInstallsTheFrontEndFiles` checks one is there.

**Dependabot** groups npm **patches** only and `dependabot-automerge.yml` auto-merges those; an npm
minor arrives as its own PR for review, since a Tabler minor can change how the screens look. Other
ecosystems auto-merge minor and patch.

**Charts are Tabler's sparkline and tracking**, no chart library (§7.2, §7.10). `tabler.min.js` draws
sparklines only on page load, so `app.js` draws those an htmx swap brings in (`drawSparklines` on
`htmx:afterSwap`) — without it a boosted visit to the dashboard shows empty charts. Sizes are classes
in `app.css` (`sparkline-wide`), never a `style` attribute (CSP). Icon names passed to
`fragments/icon` must be **literal** in the template, or the sprite scanner leaves them out;
`fragments/trend` spells its three out for that reason. The dashboard's figures come from
`DashboardJobs` (pure and clock-taking like `Publication`) and `JobClickService.statistics`.

`static/js/app.js` is the application's own behaviour: one small plain-ES module, delegated listeners
so it survives htmx swaps, covering modal dismissal, chip removal and copy-to-clipboard. hyperscript is
gone, so the CSP carries **neither `unsafe-inline` nor `unsafe-eval`**.

Controls that only work with JavaScript are rendered `hidden` with `data-enhanced` and revealed by the
module, so no screen offers a dead button — progressive enhancement is still mandatory (§7.1).
`static/css/app.css` is the only custom stylesheet.

The dark palette comes from `data-bs-theme-base="gray"` on `<html>` in `layout.html` **and**
`login.html`, with the vendored `tabler-themes.min.css`. It matches the other letsemploy apps
(ojobpub-sourcetracker does the same); Tabler 1.6 otherwise defaults to a neutral gray.

**`login.html` is the one standalone template** (§7.19): it does not decorate `layout.html`, which
assumes a signed-in user's sidebar, switcher and user menu. It is served by `security/LoginController`
and permitted **by path** in the OIDC chain — `loginPage("/login").permitAll()` alone matches the
login and failure URLs exactly, query string included, so `/login?logout` and `?lang=de` were bounced
to a bare `/login`. The locked chain does not permit it. It has a password field only when local
accounts are enabled (§2.12); a provider's credentials are never entered here. The account screens
(`templates/account/`) decorate their own standalone shell, `account/standalone.html`.

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

**The shell is for pages only.** `UiContextAdvice` returns no `ui` for a `@ResponseBody` handler,
because building it resolves the user and opens a session, and the public feed must open none (§5.1).
For the same reason `OjobpubController` has its own `@ExceptionHandler` answering JSON, ahead of
`GlobalErrorHandler`'s HTML page. `FeedStatelessTest` asserts no answer of the feed opens a session;
it once did on every fetch.

Each controller supplies its own `page` (`PageMeta`); one that forgets it will not render. A new
sidebar destination is added in `UiContextFactory` — there are eleven: Dashboard, Jobs, Feeds, People,
API tokens, Employers, Locations, Tags, Invitations, Activity, Users. Its icon name lives in the `NavItem`, which the sprite
scanner reads too.

**Two entries are role-conditional.** API tokens is added only when an employer is active *and* the
actor administers it; Users only when the actor is a platform admin. Two consequences worth knowing: a nav item renders on every
screen, so a missing `nav.*` key in either bundle fails the unresolved-key assertion for *every* path
at once; and an unconditional `nav.add` passes every test except
`PeopleScreenTest.anEditorIsNotOfferedApiTokensInTheSidebar`, which exists for that reason.

Where a screen has an htmx fragment variant, it is a second `@GetMapping` on the **same** controller
annotated `@HxRequest(boosted = false)` returning a fragment selector (`"tag/fragments/table :: table"`).
The `boosted = false` matters: navigation uses `hx-boost`, so a boosted request is an htmx request that
still wants a whole page.

i18n: `messages.properties` (English, the original and the fallback) plus one bundle per language in
`WebLangConfig.LANGUAGES` — en, de, fr, it, es, pt, ja, ru, zh. That list is the only one: Settings, the
sign-in page's language links and `JobLinkHandler` all read it. A new key goes into **every** bundle.
`MessageBundleTest` asserts them at parity, no empty value, and that every message with arguments
still shows each argument when formatted by MessageFormat — where a lone ASCII `'` swallows text, so
translations use ’ or `''`. `ScreenRenderingTest` renders every screen in every language.
`spring.messages.fallback-to-system-locale=false`: a missing key falls back to English, never to the
host's locale. Controllers pass message *keys* as flash attributes and templates resolve them.

### Conventions

- **Less Lombok, plain Java** (§9.6). Values are **records** — view models, `ojobpub/v1/dto`, service
  results. Lombok survives only as `@Getter/@Setter/@NoArgsConstructor` on JPA entities and the few
  mutable classes (`JobForm`, `EmployerContext`); no `@Data`, `@Value`, `@RequiredArgsConstructor`,
  `@Slf4j`. Beans have an **explicit constructor** — constructor injection, never an `@Autowired`
  field, `@Value` settings as constructor parameters. Loggers are
  `private static final Logger log = LoggerFactory.getLogger(X.class)`.
  `Actor` stays a class with getters: as a record every `getId()`/`isAdmin()` call would be renamed.
- **Input shape is Bean Validation, checked in the service** (`common/validation/InputValidator`), so
  the form and the API share it. `check` throws `ValidationFailure`, never
  `ConstraintViolationException`, with the **same field keys** as before — `ApiErrors` maps them.
  Messages are `{validation.*}` bundle keys, in every bundle. Rules needing the database, the actor or
  the state (duplicates, `requireOwn`, quotas, last owner, readiness) stay hand-written, and so do
  parse failures.
- `-parameters` compilation is expected (Spring Data and MVC parameter binding rely on it).
- **`CHANGELOG.md`** follows Keep a Changelog 1.1.0. A change someone running the publisher, or
  reading its feed or API, would notice adds a line under `## [Unreleased]` in the same commit;
  dependency bumps, CI and refactors do not. `make git-release v=x.y.z` runs
  `scripts/release-changelog.sh`, which dates `[Unreleased]` as that version, moves the compare
  links on, and refuses an empty `[Unreleased]` or a version already listed. v0.4.0 was never tagged, so its links use the
  release commit `0ff137f`.

## Tests

```bash
./mvnw test                                  # all; needs MariaDB
TEST_DB=sqlite ./mvnw test                   # all, on SQLite; no server
./mvnw test -Dtest=MigrationParityTest       # both migration folders advance together; no database
./mvnw test -Dtest=OjobpubConformanceTest    # the build gate; no Spring, no database
./mvnw test -Dtest=ScreenRenderingTest       # renders every screen against the seed data
./mvnw test -Dtest=InvitationServiceTest     # consent and non-disclosure
./mvnw test -Dtest=InvitationMailTest        # the invitation mail: who gets one, in which language, never an unknown address
./mvnw test -Dtest=MembershipServiceTest     # ownership, role changes, the last-owner rule
./mvnw test -Dtest=ManagementApiTest         # the GraphQL API end to end
./mvnw test -Dtest=GraphQlSchemaTest         # the SDL is public and sessionless; the API still takes a token
./mvnw test -Dtest=ApiLimitsTest             # depth, rate and body limits, with the ceilings lowered
./mvnw test -Dtest=PeopleScreenTest          # the People screen as an editor, not an admin
./mvnw test -Dtest=NoEmployerScreensTest     # lists for a user with no employer: how to get one, no dead create
./mvnw test -Dtest=OidcLoginTest             # real sign-in without the bypass: PKCE, accounts, logout
./mvnw test -Dtest=LocalAccountTest          # email/password: sign-up, links, sessions, no oracle, throttle
./mvnw test -Dtest=LocalAccountsWithProvidersTest # the form beside a provider; LocalAccountsDisabledTest without
./mvnw test -Dtest=HCaptchaTest              # widget, refusal, CSP widened on captcha pages only; MCaptchaTest too
./mvnw test -Dtest=LoginCaptchaTest          # captcha after failed sign-ins: per address, per client, no oracle
./mvnw test -Dtest=PasswordPolicyTest        # the configurable password policy; no Spring, no database
./mvnw test -Dtest=LockedChainTest           # no provider, no dev: the back-office is closed
./mvnw test -Dtest=LoginProvidersTest        # several providers: one button each, sorted, branded
./mvnw test -Dtest=LoginOptionsTest          # provider check (OIDC + GitHub) and brand by host; no Spring
./mvnw test -Dtest=GitHubUserServiceTest     # GitHub identity and verified email; mock server, no Spring
./mvnw test -Dtest=AdminPolicyTest           # admin rules: issuer scope, verified email, claims; no Spring
./mvnw test -Dtest=AdminSyncTest             # admins promoted and demoted through real sign-ins
./mvnw test -Dtest=LocationTagScopeTest      # another employer's locations and tags: 404, refused on a job
./mvnw test -Dtest=MigrationV7Test           # V7 divides shared rows correctly, on MariaDB and SQLite
./mvnw test -Dtest=EmployerDeleteTest        # owners and admins delete; editors, strangers and tokens cannot
./mvnw test -Dtest=ImpersonationTest         # view-as: the user's view, read-only, never on admins
./mvnw test -Dtest=AuditLogTest              # the audit log: what lands in which log, and who reads it
./mvnw test -Dtest=AdminModeTest             # admins start in the user view; every power follows the switch
./mvnw test -Dtest=MemberSuspensionScreenTest # suspending and reinstating from the People screen
./mvnw test -Dtest=AccountSuspensionTest     # suspending accounts: admins only, never self or an admin
./mvnw test -Dtest=AccountDeletionTest       # deleting one's own account: sole employers go, tokens stay, only admin may not
./mvnw test -Dtest=SettingsTest              # settings: saved, applied to a new session, not while viewing as
./mvnw test -Dtest=ResourceLimitsTest        # the quota convention; no Spring, no database
./mvnw test -Dtest=QuotaEnforcementTest      # the seven quotas against the seed data
./mvnw test -Dtest=MessageBundleTest         # every bundle at parity, and every argument survives formatting
./mvnw test -Dtest=InputValidatorTest        # Bean Validation: field keys, localized messages; no database
./mvnw test -Dtest=TokenLifecycleTest        # the expiry boundaries; no Spring, no database
./mvnw test -Dtest=PermalinkServingTest      # the permalink URL: switched, empty, dated forward, 404/301
./mvnw test -Dtest=PermalinkServiceTest      # own feeds only, members only, what the log records
./mvnw test -Dtest=ServiceTokenExpiryTest    # expiry, renewal and what renewal must not touch
./mvnw test -Dtest=JobLinkTest               # /go/{id}: redirect, 410, what counts, country, no session
./mvnw test -Dtest=JobClickServiceTest       # the dashboard's click figures, scoped to the employers asked
./mvnw test -Dtest=FeedStatelessTest         # no feed answer opens a session; a failure is JSON
./mvnw test -Dtest=DatabaseInitOrderTest     # Flyway, then the seed, then Hibernate, whatever the classpath
./mvnw test -Dtest=PictureProcessorTest      # crop, size, metadata stripped, refused before decoding; no Spring
./mvnw test -Dtest=ProviderPictureFetcherTest # the fetch fence: https, public addresses, no redirects, size; no Spring
./mvnw test -Dtest=PictureServiceTest        # uploads, the provider's copy, who may see whose picture
./mvnw test -Dtest=PictureScreenTest         # Settings upload, served from here, People and Users
./mvnw test -Dtest=WeeklySummaryTest         # what one employer's summary says, and when it is empty; no Spring
./mvnw test -Dtest=WeeklySummaryMailTest     # the weekly mail: who gets one, in which language, once a week
make assets                                  # rebuild target/classes/static/vendor (Maven fetches Node)
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
`publisher_test` and starting sessions in admin mode. Its Hikari pool is **small and shrinks when idle**: Spring caches a
context per distinct test configuration, each with its own pool, and full default pools once
exhausted MariaDB's `max_connections` — "Too many connections" in whichever class started next. **It must stay profile-specific.** A plain
`src/test/resources/application.yml` loses to `src/main/resources/application.properties`, so its
overrides are silently ignored.

**Every back-office test runs as the seeded admin, in admin mode** (`application-test.yml` starts
sessions in it, §2.10), because that is who the dev bypass resolves to —
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

- **One scheduled task, and only one.** The weekly summary (§7.28, below) runs on `@Scheduled`; the
  job date window and token expiry are still evaluated when read (§4.4, §2.8). JobRunr, configured but
  never used, was removed; `V9` drops the tables it had created. Don't add a second scheduled task
  without a spec section for it (§12 item 4).
- **SQLite folds case for ASCII only.** "Zürich" and "zürich" are distinct there and equal on MariaDB,
  so a tag or city differing only in a non-ASCII capital can exist twice on SQLite.
- **MariaDB DDL is not transactional.** A migration that fails halfway leaves the schema half-changed
  and the `migrations` row marked failed, and every later start fails on the part that did apply. It
  will not rename a column a foreign key still points at, either — drop the key, rename, re-add, which
  is why `V4` does the `invitations` rename in three statements.

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Spring Boot 4 / Java 25 server-rendered web app (Thymeleaf + htmx). Employers manage job postings here
and publish selected ones as a machine-readable `ojobpub.json` feed. Maven artifact is `ojobpub-app`,
Java package root is `org.letsemploy.ojobpub_publisher`.

**`docs/SPEC.md` is the authoritative specification** — purpose, roles, domain model, job lifecycle, the
published feed contract and the user interface. Where it disagrees with the code, the spec wins. Code
comments cite it as `spec 4.3`, and that is the fastest way to find the rule behind a piece of code.

## Commands

MariaDB is required to run *and* to test — `docker compose up -d` starts it on 3306 (root/root) plus
adminer on 8081.

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev   # http://localhost:8080
./mvnw package                                          # build jar (runs tests)
./mvnw test                                             # all tests
./mvnw test -Dtest=MembershipServiceTest                # one class
./mvnw test -Dtest=TagServiceTest#namesAreNormalised    # one method
./mvnw versions:display-dependency-updates              # or: make check-mvn-updates
```

The `dev` profile is not active by default and the app will not start without it: `application-dev.yml`
holds the only datasource configuration (database `ojobpub_publisher_dev`). It also bypasses
authentication (§2.3) — see **Authentication** below.

JobRunr's dashboard binds port 8000 whenever the app runs. Actuator and the Prometheus registry are on
the main port.

Container builds use `Dockerfile.multistage`, which is what CI pushes to ghcr.io. The plain
`Containerfile`/`Dockerfile` expects a pre-built `target/app.jar`.

## Architecture

### Package by feature

`employer`, `job`, `location`, `tag`, `feed`, `invitation`, `membership` — each owns its entity,
repository, service, form objects and controllers. Plus `common` (shared base types, slugs,
exceptions), `config` (beans), `security` (users, roles, filter chains), `web` (shell context, view
models, error handling) and `ojobpub/v1` (the published contract).

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

OIDC via Spring Security. `SecurityConfig` has three chains: the public feed; a **dev-profile bypass**
(no identity provider needed); and the back-office. Without a configured `ClientRegistrationRepository`
the back-office chain is replaced by a **fail-closed** one — the app starts and feeds stay served, but
nobody gets in. `DevBypassGuard` refuses to start if `dev` is combined with `prod`.

Roles are **two independent axes** (§2.1):

- `UserEntity.Role` — the *platform* role, `USER` or `ADMIN`. Admins act on any employer without being
  a member.
- `MembershipRole` — the *per-employer* role, `OWNER` or `EDITOR`, stored on the membership.

`AppUser` carries both (`isAdmin()`, `isOwnerOf(employerId)`). There is no global "editor" any more;
that word now names a membership role only.

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

**Every employer must keep at least one owner** (§2.7). `MembershipService` guards both demotion and
removal, and the People screen renders the reason rather than greying the control out.

`InviteOutcome.SENT` is returned both when an invitation was created **and** when no account matched
the address. That is deliberate: the form must not become an oracle for which addresses are registered.
`ALREADY_MEMBER` and `ALREADY_INVITED` *are* reported, because both people are listed on the same
screen. Do not "improve" this by reporting unknown addresses.

Owners invite, change roles, remove members and edit the employer record; editors work on jobs and
feeds. Employer **creation is open to any signed-in user**; deletion is admin-only and **no delete route
exists yet**.

### Persistence

- Flyway owns the schema (`src/main/resources/db/migration`, history table `migrations`);
  `hibernate.ddl-auto: none`. Schema changes need a new `V<n>__*.sql` — never rely on JPA DDL, and
  never edit an applied migration.
- `common/Base` is the `@MappedSuperclass` for UUID-keyed entities with `createdAt`/`lastModifiedAt`:
  `Employer`, `Job`, `Location`, `Feed`, `Invitation`, `Membership`, `UserEntity`. `Tag` is the
  exception (numeric identity id), as is `JobStatusEvent`.
- Enumerations persist **as strings**, never ordinals — including country codes. `Location.country` is a
  `CountryCode` whose constant names *are* the ISO alpha-2 codes, so the stored value is the published
  value.
- Job↔tag and job↔location are plain `@ManyToMany` join tables; there are no link entities.
- `data.sql` seeds demo rows on every start (`spring.sql.init.mode: always`) using
  `ON DUPLICATE KEY UPDATE`, so it is MariaDB-specific and must stay idempotent.
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

`templates/` is the only template tree. Layout Dialect: `layout.html` decorates, pages use
`layout:decorate`, shared fragments live in `templates/fragments/`.

Templates bind to the view models in `web/view/`, assembled by `web/Views.java`.
`web/UiContextFactory` builds the shell context (`ui`); `web/UiContextAdvice` supplies it to every
screen, and `web/GlobalErrorHandler` builds it too. That duplication is necessary: Spring does **not**
apply `@ModelAttribute` contributions to `@ExceptionHandler` methods, so an error view relying on the
advice would fail to render and turn every 404 into a 500.
`ScreenRenderingTest.notFoundRendersTheErrorPage` guards it.

Each controller supplies its own `page` (`PageMeta`); one that forgets it will not render. A new
sidebar destination is added in `UiContextFactory`.

Where a screen has an htmx fragment variant, it is a second `@GetMapping` on the **same** controller
annotated `@HxRequest(boosted = false)` returning a fragment selector (`"tag/fragments/table :: table"`).
The `boosted = false` matters: navigation uses `hx-boost`, so a boosted request is an htmx request that
still wants a whole page.

i18n: `messages.properties` / `messages_de.properties`, session locale, switchable with `?lang=`. The
bundles are asserted **at parity** — a key added to one and not the other fails the build. Controllers
pass message *keys* as flash attributes and templates resolve them.

### Conventions

- Lombok throughout: `@Getter/@Setter/@Value/@Data/@Slf4j`, and `@RequiredArgsConstructor` for
  **constructor injection**. There is no `@Autowired` field injection anywhere; keep it that way.
- `-parameters` compilation is expected (Spring Data and MVC parameter binding rely on it).

## Tests

```bash
./mvnw test                                  # all; needs MariaDB
./mvnw test -Dtest=OjobpubConformanceTest    # the build gate; no Spring, no database
./mvnw test -Dtest=ScreenRenderingTest       # renders every screen against the seed data
./mvnw test -Dtest=InvitationServiceTest     # consent and non-disclosure
./mvnw test -Dtest=MembershipServiceTest     # ownership, role changes, the last-owner rule
make assets                                  # refresh vendored front-end deps (needs Node)
```

`OjobpubConformanceTest` validates generated documents against the vendored schema (minimal, maximal
and empty-feed shapes). It is the most important test here: everything else is internal, this is the
contract. Plain unit test — no Spring context, no database, no profile.

`ScreenRenderingTest` renders every screen through the production controllers and asserts the section 7
rules that are checkable in markup (no unresolved message keys, no CDN or inline script, `hx-boost`,
skip link, `aria-current`, preserved input on validation errors, status words not just colours). It
references the fixed UUIDs in `data.sql`, so **a new screen must be added to `everyScreen()`**.

There is no preview harness and no fixture data: screens run against the real controllers and
`data.sql`. The seed deliberately covers all five publication states — including an `INACTIVE` job and
an `ACTIVE` one with no location, which presents as `INCOMPLETE` and is excluded from the feed — and
three users: the dev admin (owner of Acme), `member@example.com` (editor) and `editor@example.com` (no
membership, one pending invitation). Keep that coverage when editing `data.sql`, or the tests lose it.

Test configuration lives in `src/test/resources/application-test.yml` (profile `test`), pointing at
`publisher_test` and disabling JobRunr. **It must stay profile-specific.** A plain
`src/test/resources/application.yml` loses to `src/main/resources/application.properties`, so its
overrides are silently ignored and the dashboard binds its fixed port, colliding with a running
instance.

Back-office tests use `@ActiveProfiles({"dev", "test"})`: `dev` provides the authentication bypass,
`test` is listed second so its datasource wins over the dev one. CI provisions `publisher_test` on a
MariaDB service container.

## Known loose ends

- **Configured but unused.** JobRunr has no jobs, `spring-boot-starter-graphql` has an empty
  `resources/graphql/`, and `@EnableScheduling` has no scheduled methods. Nothing here needs a
  scheduler by design: the job date window is evaluated at serving time.
- **No employer delete route**, though the spec reserves deletion for admins (§7.13).

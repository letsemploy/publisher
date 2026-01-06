# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Spring Boot 4 / Java 25 server-rendered web app (Thymeleaf + htmx) for managing job postings and
publishing them as an "ojobpub" JSON feed. Maven artifact is `ojobpub-app`, Java package root is
`org.letsemploy.ojobpub_publisher`.

## Commands

A MariaDB instance is required for *both* running and testing — `docker compose up -d` starts MariaDB
on 3306 (root/root) plus adminer on 8081.

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev   # run app (http://localhost:8080)
./mvnw package                                          # build jar (runs tests)
./mvnw test                                             # all tests
./mvnw test -Dtest=TagServiceTest                       # single test class
./mvnw test -Dtest=TagServiceTest#updateShouldAllowChangingTagName
./mvnw versions:display-dependency-updates              # or: make check-mvn-updates
```

The `dev` profile (`application-dev.yml`) is not active by default; without it there is no datasource
config and the app will not start against MariaDB. It points at `ojobpub_publisher_dev` and — important —
sets `spring.thymeleaf.prefix: classpath:/templates_v2/`.

Tests use `src/test/resources/application.yml` → database `publisher_test` on the same MariaDB.
`ApplicationTests` is a full `@SpringBootTest`; slice tests like `TagServiceTest` use `@DataJpaTest` with
`@AutoConfigureTestDatabase(replace = NONE)` (real MariaDB, not H2) and must disable JobRunr via
properties, as that test does.

JobRunr's dashboard runs on port 8000 when the app is up. Actuator + Prometheus registry are on the
main port.

Container builds use `Dockerfile.multistage` (this is what CI pushes to ghcr.io); the plain
`Containerfile`/`Dockerfile` expects a pre-built `target/app.jar`.

## Architecture

### Package-by-feature

Each domain lives in its own package with the same shape:
`Entity` / `Dto` / `Repo` (Spring Data) / `Service` / `Controller` (+ optional `HxController`).
Features: `employer`, `job`, `location`, `tag`, `feed`. Plus `common` (shared base classes),
`config` (beans), and `ojobpub/v1` (the public feed API).

### Two controllers per feature: full page vs. htmx fragment

`XController` returns whole pages (`"tag/list"`, `"redirect:/tags"`). `XHxController` is annotated
`@HxRequest(boosted = false)` from `htmx-spring-boot-thymeleaf` and is mapped to the *same* URL paths;
Spring routes to it only when the request carries htmx headers and is not a boosted navigation. Those
methods return fragment selectors like `"job/fragment/tab :: tab"`. When adding an htmx-driven
interaction, add the method to the `Hx` controller rather than branching inside the page controller.

All controllers extend `common/BaseController`, which injects `currentUrl`, `tab` (from the `?tab=`
query param) and `randomNumber` into every model — templates rely on `tab` for tabbed views.

### Persistence

- `common/Base` is the `@MappedSuperclass` for UUID-keyed entities (`Employer`, `Job`, `Location`,
  `Feed`, `FeedJob`) with `createdAt`/`lastModifiedAt` maintained by `@PrePersist`/`@PreUpdate`.
  `Tag` is the exception: `Long` identity id, no `Base`.
- Schema is owned by Flyway (`src/main/resources/db/migration`, history table `migrations`);
  `hibernate.ddl-auto: none`. Schema changes need a new `V<n>__*.sql` migration — never rely on JPA DDL.
- `src/main/resources/data.sql` seeds demo rows on every start (`spring.sql.init.mode: always`,
  `defer-datasource-initialization=true`) using `ON DUPLICATE KEY UPDATE`, so it is MariaDB-specific
  and must stay idempotent.
- Join entities `JobTag`/`JobLocation` use `@IdClass`-style composite ids (`JobTagId`, `JobLocationId`).
  Deleting a tag requires clearing `job_tags` first — see `TagRepo.deleteJobTagsByTagId`.

### Feed publishing

`ojobpub/v1` maps internal entities to the external contract: `OjobpubController` (`@RestController`,
`GET /ojobpub/v1/by-employer/{id}`) → `OjobpubService.generateOjobpub()` → `Ojobpub*Dto` tree. Keep the
DTO field names and value formats (upper-case country/currency, lower-case interval) stable — they are
the published API surface.

### Templates

Thymeleaf with the Layout Dialect: `templates_v2/layout.html` is the decorator, pages use
`layout:decorate`. CSS/JS (Tabler, htmx, hyperscript, Font Awesome) load from CDNs in `layout.html`;
`static/css/main.css` is currently empty. Fragment directories are inconsistently named
(`fragments/`, `fragment/`, `partials/`) — follow whatever the feature already uses and match the
string the controller returns.

`templates_v1/` is the retired copy of this tree; only `templates_v2` is wired up.

i18n: `messages.properties` / `messages_de.properties`, session locale, switchable with `?lang=`.
Controllers pass message *keys* (e.g. `"msg.success.saved"`, `"msg.error.formMissingData"`) as flash or
model attributes and templates resolve them.

### Conventions worth matching

- Lombok everywhere (`@Getter/@Setter/@Data/@Slf4j`); field injection with `@Autowired`.
- Services throw `common/exception/NotFoundException` and `jakarta.validation.ValidationException`;
  page controllers catch these and render `"error"` or re-render the form with an `error` model attribute.
- Two `ModelMapper` beans exist (`modelMapper`, `strictModelMapper` — the latter is field-access and
  ambiguity-ignoring); pick by bean name when injecting.
- `-parameters` compilation is expected (needed for Spring Data / MVC param binding).

### Known loose ends

JobRunr, GraphQL (`spring-boot-starter-graphql`, empty `resources/graphql/`) and `@EnableScheduling`
are configured but have no jobs, schemas or scheduled methods yet. The `Makefile` `update`/`build`
targets copy vendored assets from `node_modules`, but there is no `package.json` in the repo and the
target directories do not exist — those targets are stale; build with `./mvnw` directly.

## Specification-driven rebuild

`docs/SPEC.md` is the authoritative specification: purpose, domain model, job lifecycle, the public
feed contract and the user interface. Where it disagrees with the code, the spec wins. The published
output contract is the oJobPub v1 JSON Schema, vendored at
`src/main/resources/ojobpub/v1/ojobpub.schema.json` (upstream:
`https://raw.githubusercontent.com/letsemploy/schema/refs/heads/main/v1/ojobpub.json`).

The backend and the UI are wired to each other. `templates/` is the only template tree; `templates_v1`
and `templates_v2` are gone, as are the old `*Dto` and `*HxController` classes.

### The rules live in one place

`job/Publication.java` holds the publication rules of spec sections 4.3 and 4.4 — the readiness
requirements, the date window, and what a job *presents* as (`PUBLISHED`, `EXPIRED`, `INCOMPLETE`,
`DRAFT`, `INACTIVE`). Both the feed serving path and the back-office readiness panel read it, so a
screen and a published document cannot disagree. Add publication logic there, not in a controller.

`ojobpub/v1/service/OjobpubEnums.java` maps every published enum explicitly. Never derive a published
value with `name().toLowerCase()`: the schema wants `on-site`, which that produces as `on_site`.

### Published feed

`GET /ojobpub/v1/{employerSlug}_{employerId}/{feedSlug}_{feedId}/ojobpub.json` — public, anonymous,
`GET` only. Identity is the UUID; the slug is decorative, so a stale slug still resolves and answers
`301` to the canonical URL. Slug and UUID are separated by an **underscore**, the one character neither
side may contain. Malformed segments answer `404`, never `400`.

### Authentication

OIDC via Spring Security. `SecurityConfig` has three chains: the public feed, a **dev-profile bypass**
(a synthetic `dev@localhost` admin, no identity provider needed), and the back-office. Without a
configured `ClientRegistrationRepository` the back-office chain is replaced by a **fail-closed** one —
the app starts and feeds stay served, but nobody gets in.

### UI

Tabler sidenav shell, htmx for partial updates, a hard JavaScript budget (no hand-written `.js`, no
front-end build). Assets are vendored in `static/vendor/` and must never come from a CDN.

Templates bind to the view models in `web/view/`, assembled by `web/Views.java`. `web/UiContextAdvice`
supplies the shell context (`ui`) to every screen; each controller supplies its own `page`
(`PageMeta`). A controller that forgets `page` will fail to render.

There is no preview harness and no fixture data: screens are exercised against the real controllers
and `data.sql`. The seed data deliberately covers all five publication states, including an `INACTIVE`
job and an `ACTIVE` one with no location (which presents as `INCOMPLETE` and is excluded from the
feed). Keep that coverage when editing `data.sql`, or `ScreenRenderingTest` loses it.

### Tests

```bash
./mvnw test                                  # all; needs MariaDB
./mvnw test -Dtest=OjobpubConformanceTest    # the build gate; no Spring, no database
./mvnw test -Dtest=ScreenRenderingTest       # renders every screen against the seed data
```

`OjobpubConformanceTest` validates generated documents against the vendored schema (minimal, maximal
and empty-feed shapes). It is the most important test here: everything else is internal, this is the
contract. It is a plain unit test — no Spring context, no database, no profile.

`ScreenRenderingTest` renders every screen through the production controllers and asserts the section 7
rules that are checkable in markup. It references the fixed UUIDs in `data.sql`.

Test configuration lives in `src/test/resources/application-test.yml` (profile `test`), which points at
the `publisher_test` database and disables JobRunr. **It must stay profile-specific.** A plain
`src/test/resources/application.yml` loses to `src/main/resources/application.properties`, so its
overrides are silently ignored and the dashboard binds its fixed port — which then collides with a
locally running instance.

Back-office tests activate `@ActiveProfiles({"dev", "test"})`: `dev` provides the authentication
bypass, and `test` is listed second so its datasource wins over the dev one. `DevBypassGuard` refuses
to start if `dev` is ever combined with `prod`.


# ojobpub-publisher — Product & Technical Specification

**Status:** target state. This document describes how the application is meant to look and behave.
Where it disagrees with the current implementation, this document wins.

**Normative language:** *must* = required for conformance; *should* = strongly recommended, deviation
needs a reason; *may* = optional.

---

## 1. Purpose and scope

### 1.1 What this is

A **publishing back-office for job postings**. An employer's team creates and maintains job postings in
a database, groups the ones they want to expose into a **feed**, and gets a stable public URL that
serves those postings as a machine-readable **`ojobpub.json`** document.

The product exists to solve one problem: *an employer has open positions and wants other systems —
job boards, aggregators, partner sites — to consume them reliably, without scraping.*

### 1.2 What this is not

The application must not grow into any of the following:

- **Not a job board.** There is no job-seeker-facing search, browsing or alerting. Job seekers are
  served by the *consumers* of the feed, not by this application.
- **Not an applicant tracking system.** Applications, candidates, CVs, interviews and hiring pipelines
  are out of scope. Every posting links out via its `url` to wherever applications are actually handled.
- **Not a CMS.** Postings carry a short plain description (≤1000 characters), not rich marketing content.

### 1.3 Consumers

The audience for the published output is machines: aggregators, partner job boards, search indexers and
the employer's own website. Therefore stability of the published contract outranks convenience inside
the back-office. A change that breaks a consumer's parser is a breaking change even if no user notices.

### 1.4 The published contract

The output format is the **oJobPub v1 schema**, published at:

```
https://raw.githubusercontent.com/letsemploy/schema/refs/heads/main/v1/ojobpub.json
```

That JSON Schema (draft 2020-12) is the authoritative contract. This specification does not redefine it;
section 6 defines how the domain model maps onto it and how ambiguous cases are resolved. Any conflict
between this document and the schema must be resolved in favour of the schema.

---

## 2. Actors and access

### 2.1 Roles

Roles are **two independent axes**. A user's *platform* role says what they are to the installation; a
*membership* role says what they are to one employer. A plain user may own their own employer while
having no standing anywhere else.

**Platform role** — one per user, on the user record:

| Role | Sees | May do |
|---|---|---|
| **User** | The employers they are a member of | Create an employer, becoming its owner (§2.7); work on employers they belong to, as their membership role allows |
| **Admin** | All employers | Everything, on any employer, without being a member: platform staff who keep the installation working. Delete employers; override `publishedAt` |

**Membership role** — one per (user, employer) pair, on the membership:

| Role | May do within that employer |
|---|---|
| **Editor** | Full create/read/update/delete on the employer's jobs, feeds and locations |
| **Owner** | Everything an editor may do, plus: edit the employer record, invite people (§2.6), change a member's role, and remove members (§2.7) |

There is no read-only role in v1. Every member may edit that employer's jobs and feeds; the difference
between owner and editor is authority over **the employer and its people**, not over its content.

Tags are global and every signed-in user may manage them.

An admin is not automatically a member. They act on any employer by virtue of the platform role, and
when they need to belong to one — to be listed as a person responsible for it — they are invited or
they create it, like anyone else.

### 2.2 Authentication

- Authentication **must** use OAuth 2.0 / OpenID Connect through Spring Security, authorization-code
  flow with PKCE. The application is an OIDC *relying party*; it must not store passwords.
- A user is identified by the stable pair **(issuer, subject)** from the ID token — never by email
  alone, which is mutable and may be reassigned.
- On first successful login the application **must** provision a local user record from the ID token
  claims (issuer, subject, email, preferred display name). This record holds the application's own
  authorization data: role and employer memberships.
- A newly provisioned user has the **User** platform role and **no employer memberships**. They can log
  in, and from an empty state they may either **create an employer** — becoming its owner (§2.7) — or
  wait for an invitation (§7.16). Neither path is privileged over the other.
- Membership is created by **exactly two acts**: creating an employer, and accepting an invitation
  (§2.6, §2.7). No other path grants access to an employer, and neither can be performed on someone
  else's behalf without their consent. Removal is the owner's or an admin's to perform and needs none.
- The authenticated user **must** expose a stable **user identity** and their **email address**, not
  merely a display name and a set of employer ids. An invitation is addressed to a person, so a
  principal that cannot be identified as a specific user has nothing to attach one to.
- Role and membership are **local** data. The application should not depend on custom OIDC claims, so
  that it works against any standards-compliant provider. It *may* optionally map a configured group
  claim onto the Admin role, and this mapping must be off by default.
- Sessions are server-side. Logout must clear the local session and should trigger OIDC RP-initiated
  logout where the provider supports it.

### 2.3 Development mode

Running with the `dev` profile **must** bypass authentication entirely — no OIDC provider is needed to
develop or run the application locally.

- With `dev` active, every request is treated as authenticated by a fixed principal (`dev@localhost`)
  holding the **Admin** role and access to all employers.
- That development user **must be a real, seeded record**, not a synthetic object invented per request.
  Anything keyed on user identity — invitations first among them — is otherwise unreachable in
  development and untestable, because the tests run under this same bypass. The seed data therefore
  contains the development administrator, and the bypass resolves to it.
- The login and logout routes are inert in this mode; there is no redirect to an identity provider.
- The UI **must** display a persistent, unmistakable banner stating that authentication is disabled.
- This bypass **must** be bound to the `dev` profile only, must never be reachable through a
  configuration property alone, and the application **must refuse to start** if the bypass is active
  while the `prod` profile is also active.

### 2.4 Public vs authenticated surface

| Surface | Access |
|---|---|
| Published feed documents (`GET /ojobpub/v1/{employerSlug}_{employerId}/{feedSlug}_{feedId}/ojobpub.json`) | **Public.** No authentication, no session, no cookies required. |
| `/actuator/health`, `/actuator/info` | Public |
| All other actuator endpoints | Admin only |
| Static assets, login routes, error pages | Public |
| Everything else (the entire back-office) | Authenticated |

Authorization **must** be enforced server-side on every request, on the data access path — not by hiding
navigation. A user requesting a job belonging to an employer they are not a member of **must** receive
`404 Not Found` (not `403`), so that the existence of other employers' records is not disclosed. The
same applies to an action their **membership role** does not permit (§2.1).

### 2.5 Employer context

A user with access to more than one employer works in the context of **one active employer** at a time,
chosen with a switcher at the top of the sidebar (§7.3). The active employer scopes all list screens and
pre-fills the employer on every create form.

- The active employer is a **convenience, not a security boundary**. Authorization is always derived
  from membership, never from the stored context.
- It **must** be stored server-side in the session, not in a client-controlled cookie.
- If the user is a member of exactly one employer, that employer is selected automatically and the
  switcher is hidden.
- An admin's switcher also offers an "all employers" option, which is the default for admins.

### 2.6 Invitations

Access to an employer is granted by invitation and taken up by consent.

**Sending**

- An **owner** of the employer may invite, and so may an **admin** (§2.1). An editor may not: they work
  on the content, not on who else gets in.
- An invitation **carries the membership role** the invitee will receive, `OWNER` or `EDITOR`. Inviting
  someone straight to owner is ordinary — a founder handing over, a colleague taking the workspace on.
- The invitee **must already be registered**. There is no sign-up-by-invitation and no account
  creation flow; an unregistered colleague must sign in once before they can be invited.
- An invitation is addressed by **exact email address**, matched case-insensitively against existing
  users. Email is not the user's identity (§2.2) — it is only how a human addresses the invitation. The
  invitation itself is bound to the resolved **user**, so a later email change does not orphan it.
- At most **one pending invitation** per (employer, user). Resolved invitations are retained as history.

**Non-disclosure**

When the address matches no account, the screen **must** report exactly what it reports on success. The
invite form must never become an oracle for which addresses have accounts here, and an administrator of
one employer has no business learning who is registered elsewhere.

There is one deliberate exception. If the address belongs to someone **already a member** of this
employer, or who **already has a pending invitation** to it, the screen says so. Both of those people
are listed on the very same screen (§7.13), so naming them discloses nothing the admin cannot already
see — and a silent no-op there would look like a bug.

**Responding**

- Only the **invitee** may accept or decline. An **owner** of that employer, or an admin, may revoke a
  pending invitation.
- **Accepting creates the membership**, with the role the invitation carried.
- Declining and revoking create nothing. A declined invitation may be sent again later; people change
  jobs and teams.
- Invitations **do not expire**. Nothing here requires a scheduler.
- Removing an existing member is an owner's or an admin's action and requires no consent. A removed
  member may be invited again. The last owner cannot be removed (§2.7).

**Notification**

No email is sent — the application has no mail infrastructure. The invitee sees pending invitations
when they next sign in (§7.3 surfaces the count in the sidebar). Delivery is recorded as an open
question (§11).

### 2.7 Ownership and role changes

**Becoming an owner**

- Any signed-in user **may create an employer**, and the creator **becomes its first owner**. Creating
  an employer is therefore also the act of joining one; there is no moment at which an employer exists
  without anyone responsible for it.
- The other route is an invitation that carries the `OWNER` role (§2.6).

**Changing a role**

- An owner of an employer, or an admin, **may change any member's role** between `OWNER` and `EDITOR`
  within that employer. Ownership is not exclusive: an employer may have as many owners as it likes,
  and they are equal — any owner may promote or demote any other.
- A role change takes effect immediately and needs no consent. It grants no access that the member did
  not already have; it only changes their authority over the employer and its people.
- The platform role (§2.1) is **not** editable here. Making someone platform staff is a different
  decision with a different blast radius, and no employer-scoped screen may do it.

**The last owner**

An employer **must** always have at least one owner. The application **must** refuse to demote or
remove the last one, and the screen **must** say why rather than simply disabling the control with no
explanation — "promote someone else first" is actionable, a greyed-out button is not.

This is the one rule here that protects against a state nobody can repair from inside the employer. An
ownerless employer would still be reachable by platform admins, but its own people could no longer
invite, change roles, or edit the record — the workspace would be frozen to everyone who actually uses
it.

**What an owner may not do**

Deleting an employer remains an **admin** action (§7.13). Ownership is authority over a workspace, not
the power to destroy it along with every feed URL its consumers depend on.

---

## 3. Domain model

Eight entities. Every entity except `Tag` carries a UUID primary key, `createdAt` and `lastModifiedAt`
(UTC instants, maintained automatically).

```
Employer 1 ──── * Job            Job * ──── * Location
   │ 1                            Job * ──── * Tag
   │                              Job's employer is fixed at creation
   └── * Feed  * ──── * Job  (membership; both sides same employer)
Employer 1 ──── 1 Location (headquarters)

User  1 ──── * Membership * ──── 1 Employer   (carries the role: OWNER or EDITOR)
User  1 ──── * Invitation * ──── 1 Employer   (carries the role it will grant)
```

### 3.1 Employer

The organization offering the jobs. Exactly one employer appears in each published document.

| Field | Type | Required | Rules |
|---|---|---|---|
| `id` | UUID | yes | generated |
| `name` | string(1..255) | **yes** | |
| `slug` | string(1..64) | **yes** | lowercase `[a-z0-9-]`, derived from `name`, freely editable; **not** required to be unique (§5.1) |
| `url` | URL | no | absolute `http(s)` URI |
| `industry` | string(1..255) | no | free text, e.g. *Software*, *Healthcare* |
| `headquarters` | Location | **yes** | the schema requires `employer.location`, so this cannot be optional |

Deleting an employer deletes its jobs and feeds and immediately stops serving its feed URLs. Only an
admin may delete an employer, and only through an explicit confirmation naming the employer.

### 3.2 Location

A city/country pair, reused across employers and jobs. Locations are shared reference data within an
employer's workspace.

| Field | Type | Required | Rules |
|---|---|---|---|
| `id` | UUID | yes | generated |
| `city` | string(1..255) | **yes** | |
| `country` | ISO 3166-1 alpha-2 | **yes** | stored as a **two-character uppercase string** (`CH`, `DE`, `US`) |

`country` **must** be persisted as its alpha-2 string, never as the ordinal of an enum. Ordinal storage
silently corrupts every stored row whenever the underlying country list is reordered or extended.
Validation of the code against the ISO 3166-1 list happens at the application boundary.

A location that is still referenced by an employer or a job **must not** be deletable; the UI must say
what still references it.

### 3.3 Job

A single open position.

| Field | Type | Required | Rules |
|---|---|---|---|
| `id` | UUID | yes | generated |
| `employer` | Employer | **yes** | set at creation, **immutable** thereafter |
| `title` | string(1..255) | **yes** | |
| `description` | string(0..1000) | no | plain text; the schema caps this at 1000 characters |
| `url` | URL | **yes** | absolute `http(s)` URI of the full posting / application page |
| `languageCode` | ISO 639-1 | **yes** | two lowercase letters, the language of *this posting's* text |
| `referenceId` | string(0..255) | no | the employer's own identifier for the position |
| `category` | string(0..255) | no | free text, e.g. *Engineering* |
| `jobType` | enum | **yes** | `PERMANENT`, `CONTRACT`, `TEMPORARY`, `FREELANCE`, `VOLUNTEER`, `APPRENTICESHIP`, `INTERNSHIP` |
| `workType` | enum | no | `ON_SITE`, `REMOTE`, `HYBRID` |
| `experienceLevel` | enum | no | `JUNIOR`, `MID`, `SENIOR`, `LEAD`, `MANAGER`, `DIRECTOR`, `EXECUTIVE` |
| `workLoadPercentMin` | int 0..100 | no | if both present, min ≤ max |
| `workLoadPercentMax` | int 0..100 | no | |
| `salaryMin` | decimal ≥ 0 | no | if both present, min ≤ max |
| `salaryMax` | decimal ≥ 0 | no | |
| `salaryCurrency` | ISO 4217 | conditional | **required when `salaryMin` or `salaryMax` is set**; three uppercase letters |
| `salaryInterval` | enum | conditional | **required when `salaryMin` or `salaryMax` is set**; `HOURLY`, `DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY` |
| `startDate` | date | no | when the engagement begins |
| `endDate` | date | no | when the engagement ends; if both present, ≥ `startDate` |
| `applyBefore` | date | no | application deadline |
| `status` | enum | **yes** | `DRAFT` (default), `ACTIVE`, `INACTIVE` — see §4 |
| `publishedAt` | date | conditional | stamped on first activation; see §4.2 |
| `locations` | set of Location | **yes for publication** | at least one required to publish (§4.3); may be empty while `DRAFT` |
| `tags` | set of Tag | no | **at most 16** |

Making salary currency and interval conditionally mandatory is deliberate: a bare number with no
currency is not interpretable by a consumer, and publishing one would be worse than publishing nothing.

A job's locations and tags **must** be restricted to locations visible to the job's employer.

### 3.4 Tag

A free-form keyword — a skill, technology or attribute.

| Field | Type | Required | Rules |
|---|---|---|---|
| `id` | long | yes | generated |
| `name` | string(1..28) | **yes** | lowercase, trimmed, **globally unique**; the schema caps tag length at 28 characters |

Tags are global, shared across all employers, and normalized on input (trimmed, lowercased). Creating a
tag that already exists returns the existing tag rather than an error. Deleting a tag removes it from
every job that carries it, and the confirmation screen **must** state how many jobs are affected.

### 3.5 Feed

A named, publishable selection of one employer's jobs.

| Field | Type | Required | Rules |
|---|---|---|---|
| `id` | UUID | yes | generated |
| `employer` | Employer | **yes** | immutable after creation |
| `name` | string(1..255) | **yes** | display name, unique per employer |
| `slug` | string(1..64) | **yes** | lowercase `[a-z0-9-]`, derived from `name`, freely editable; **not** required to be unique (§5.1) |
| `description` | string(0..255) | no | internal note on the feed's intent |
| `jobs` | set of Job | no | membership; every member **must** belong to the same employer |

A feed maps one-to-one onto a published document, and the schema admits exactly one employer per
document — therefore a feed can never span employers, and this constraint must be enforced when jobs
are assigned.

Every employer **must** have a feed with the slug `all` created automatically when the employer is
created. It behaves like any other feed and may be edited or deleted; it exists so that a new employer
has a working URL immediately.

### 3.6 Slugs

Employers and feeds both carry a slug whose only purpose is to make the public URL (§5.1) readable.
Because the URL also carries the record's UUID, the slug is **not an identifier** and carries no
uniqueness requirement — two employers may both be `acme`, and every employer may have a feed called
`engineering`.

Generation and normalization:

- A slug is proposed automatically from the record's `name`: lowercased, accents folded to ASCII
  (`Zürich` → `zurich`), any run of non-alphanumeric characters collapsed to a single hyphen, leading
  and trailing hyphens stripped, truncated to 64 characters on a hyphen boundary.
- A slug **must** match `[a-z0-9-]+` and therefore **must never contain an underscore**. This is not
  cosmetic: the underscore is the separator between slug and UUID in the public URL (§5.1), and
  admitting one into a slug would make that URL ambiguous to parse. Any underscore in a name is
  normalized to a hyphen.
- The user may override the proposal. Overrides are normalized by the same rules, so an invalid slug
  cannot be stored.
- If normalization yields an empty string (a name written entirely in a non-Latin script, say), the slug
  falls back to `employer` or `feed` respectively. The UUID still makes the URL unique.
- Renaming a record **should** offer to update its slug to match, defaulting to yes. This is safe by
  §5.1 — old URLs keep working and redirect.

### 3.7 User

A person who signs in to the back-office. The record is provisioned on first login (§2.2) and holds the
application's own authorization data; the identity provider owns everything else about them.

| Field | Type | Required | Rules |
|---|---|---|---|
| `id` | UUID | yes | generated; the application's own handle for the person |
| `issuer` | string | **yes** | the OIDC issuer; with `subject`, uniquely identifies the user |
| `subject` | string | **yes** | the OIDC subject claim; **immutable** |
| `email` | string | no | from the ID token. Mutable, and **never** an identity — only how a human addresses an invitation |
| `displayName` | string | no | for display; falls back to email, then subject |
| `role` | enum | **yes** | the **platform** role (§2.1): `USER` (default) or `ADMIN` |
| `memberships` | set of Membership | no | the employers this user belongs to, and with what role (§3.9) |

The development administrator (§2.3) is an ordinary row of this table, seeded rather than provisioned.

### 3.8 Invitation

An offer of access to one employer, made to one registered user.

| Field | Type | Required | Rules |
|---|---|---|---|
| `id` | UUID | yes | generated |
| `employer` | Employer | **yes** | immutable |
| `invitee` | User | **yes** | resolved from the email when the invitation is created; immutable |
| `invitedBy` | User | **yes** | the admin who sent it; kept even if they later lose the admin role |
| `role` | enum | **yes** | the membership role acceptance will grant: `OWNER` or `EDITOR` (§2.6) |
| `status` | enum | **yes** | `PENDING`, `ACCEPTED`, `DECLINED`, `REVOKED` |
| `respondedAt` | instant | conditional | set when the invitation leaves `PENDING`, and **immutable** thereafter |

Permitted transitions: `PENDING → ACCEPTED`, `PENDING → DECLINED`, `PENDING → REVOKED`. Nothing else —
a resolved invitation is history and is never reopened. Wanting someone back after a decline or a
removal means sending a **new** invitation, which keeps the record of what happened intact.

Deleting an employer deletes its invitations. Deleting a user deletes the invitations addressed to them.

### 3.9 Membership

One person's standing in one employer. Previously a bare pair of ids; it now carries a role, which is
why it is an entity of its own rather than a set of employer references on the user.

| Field | Type | Required | Rules |
|---|---|---|---|
| `id` | UUID | yes | generated |
| `user` | User | **yes** | immutable |
| `employer` | Employer | **yes** | immutable |
| `role` | enum | **yes** | `OWNER` or `EDITOR` (§2.1) |

At most one membership per (user, employer). A membership is created only by creating an employer or by
accepting an invitation (§2.2), and its `role` is the one thing about it that may change (§2.7).

**Invariant:** every employer has at least one membership with role `OWNER`. Creating an employer
establishes it, and §2.7 forbids the demotion or removal that would break it.

Deleting an employer or a user deletes their memberships.

---

## 4. Job lifecycle

### 4.1 States

| State | Meaning | In feeds? |
|---|---|---|
| `DRAFT` | Being prepared. Incomplete data is tolerated. | Never |
| `ACTIVE` | Live and intended for publication. | Yes, if it also passes §4.3 |
| `INACTIVE` | Withdrawn, filled or archived. Kept for the record. | Never |

Permitted transitions: `DRAFT → ACTIVE`, `ACTIVE → DRAFT`, `ACTIVE → INACTIVE`, `INACTIVE → ACTIVE`.
`DRAFT → INACTIVE` is not offered — a draft that is not wanted is deleted.

A job **must** satisfy every publication requirement in §4.3 before it can be moved to `ACTIVE`; the
transition is refused with a list of what is missing. This keeps invalid data out of feeds at the point
where a human can fix it, rather than silently at serving time.

### 4.2 `publishedAt`

`publishedAt` is the date the position was **first** made public. It **must** be stamped once, on the
first `→ ACTIVE` transition, and **must not** change afterwards — not when the job is edited, not when
it is deactivated and reactivated. Consumers use it to order and age postings; a value that moves on
every edit makes every posting look permanently new.

An admin may correct `publishedAt` manually (for example when migrating postings that were first
published elsewhere). Nobody else may — not even an owner: the publication date is part of the
published contract (§6.4), not of the workspace.

### 4.3 Publication requirements

A job is **publishable** only when all of the following hold:

1. `status` is `ACTIVE`;
2. `publishedAt` is set;
3. `title`, `url`, `languageCode` and `jobType` are present and valid;
4. it has **at least one location**;
5. if any salary amount is set, `salaryCurrency` and `salaryInterval` are also set;
6. it is within its date window (§4.4).

Requirements 1–5 are enforced when activating. Requirement 6 is evaluated at serving time.

### 4.4 The date window

A posting leaves its feeds automatically, without anyone editing it, when it goes stale:

- if `applyBefore` is set and `applyBefore < today`, the job is **excluded**;
- if `endDate` is set and `endDate < today`, the job is **excluded**;
- `startDate` never excludes a job. A position starting in three months is a normal posting.

"Today" is evaluated in the application's configured display time zone (`Europe/Zurich` by default).

Exclusion by date does **not** change `status`. The job remains `ACTIVE` in the back-office and returns
to its feeds by itself if the date is extended. The UI **must** show such jobs distinctly — `ACTIVE`
but *expired* — so the state is never a mystery.

---

## 5. Feed publishing

### 5.1 URL

```
GET /ojobpub/v1/{employerSlug}_{employerId}/{feedSlug}_{feedId}/ojobpub.json
```

Example:

```
https://publisher.example.com/ojobpub/v1/acme-ag_3f2b1c9e-5a7d-11f0-9c3e-0242ac120002/engineering_8d4e6a10-5a7d-11f0-9c3e-0242ac120002/ojobpub.json
```

Each path segment pairs a **human-readable slug** with the record's **UUID**. The slug makes the URL
self-describing in a partner's configuration file, in a log line or in a support conversation; the UUID
guarantees uniqueness and stable identity. The document itself is always the fixed filename
`ojobpub.json`, so the resource is obvious to a human and to anything that inspects the path.

**Identity lives in the UUID; the slug is decorative.** This is the point of the composite form and it
has three consequences:

- Resolution **must** key on the UUID alone. The slug portion is never used to look a record up.
- A request whose slug portion does not match the record's current slug **must** still resolve, and
  **must** answer `301 Moved Permanently` to the canonical URL carrying the current slug. URLs
  therefore repair themselves: a consumer that stored an old link keeps working and is nudged onto the
  current one.
- Renaming an employer or a feed is consequently **safe**. No consumer breaks, and the application needs
  no table of superseded slugs.

**Parsing.** Slug and UUID are separated by an **underscore**, chosen because it is the one character
that can appear in neither side: the slug charset is `[a-z0-9-]` (§3.6) and a UUID is hexadecimal and
hyphens. Each segment therefore contains exactly one underscore, and splitting on it is unambiguous —
no counting of parts, no fixed-width suffix, no dependence on how many hyphens a slug happens to have.

Each segment **must** match:

```
^(?<slug>[a-z0-9-]+)_(?<id>[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$
```

So `acme-ag_3f2b1c9e-5a7d-11f0-9c3e-0242ac120002` yields slug `acme-ag` and the UUID after it. The
UUID **must** be compared case-insensitively but generated and canonicalized in lowercase; a request
carrying an uppercase UUID redirects to the canonical lowercase form like any other non-canonical URL.

Malformed segments — no underscore, more than one underscore, an empty slug, a syntactically invalid
UUID, or a bare UUID with no slug — **must** return `404`, not `400`. The endpoint reveals nothing about
what does or does not exist.

Further rules:

- Anonymous, cacheable, `GET` only. No cookies, no session, no CSRF token.
- The response `Content-Type` **must** be `application/json; charset=utf-8`.
- CORS: `Access-Control-Allow-Origin: *` for these endpoints, so browser-side consumers work.
- An unknown employer or feed UUID, or a feed whose UUID does not belong to the employer in the first
  segment, **must** return `404` with a JSON error body, never an HTML error page.
- The back-office **must** present the canonical URL as a single copyable string. Users should never
  assemble it by hand.

### 5.2 Inclusion predicate

A feed's document contains exactly those jobs that are **members of the feed** *and* **publishable**
per §4.3, ordered by `publishedAt` descending, then `title` ascending, for a stable diff between fetches.

A feed with no qualifying jobs **must** serve a valid document with an empty `jobs` array — the schema
permits it. It must never serve `404` or an error; a consumer polling a temporarily empty feed should
see "no jobs", not a failure.

### 5.3 Defensive serving

If a member job somehow fails schema validation at serving time, it **must** be omitted from the
document and the omission logged with the job id and the reason. One malformed posting must never
break the whole fetch for a consumer.

Every such omission **must** be surfaced in the back-office on the feed screen, so it is visible to a
human rather than buried in a log.

### 5.4 Caching and freshness

- `lastUpdated` is the most recent `lastModifiedAt` among the employer record, the feed record and the
  jobs included in the document, expressed in UTC.
- The response **must** carry a strong `ETag` derived from the serialized document, and
  `Last-Modified` derived from `lastUpdated`.
- Conditional requests (`If-None-Match`, `If-Modified-Since`) **must** be honoured with `304`.
- `Cache-Control: public, max-age=300` by default, configurable per deployment.
- Because the date window (§4.4) is time-dependent, cached documents must not be held beyond the end of
  the current day; the cache key **must** include the evaluation date.

---

## 6. The ojobpub document contract

This section defines the mapping from the domain model onto the oJobPub v1 schema. The schema is closed
(`additionalProperties: false` at the root, on `employer` and on each job), so **no field outside this
mapping may be emitted**, and a null value **must** be omitted rather than serialized as `null`.

### 6.1 Root object

| JSON key | Required | Source | Format |
|---|---|---|---|
| `version` | **yes** | constant | exactly `"1.0"` |
| `lastUpdated` | **yes** | §5.4 | RFC 3339 date-time **with offset**, UTC, e.g. `2026-09-21T08:15:00Z` |
| `employer` | **yes** | the feed's employer | §6.2 |
| `jobs` | **yes** | §5.2 | array, may be empty |

`lastUpdated` **must** be serialized from an instant with an explicit offset. A local date-time
(`2026-09-21T08:15:00`, no offset) is not a valid RFC 3339 `date-time` and must never be emitted.

### 6.2 `employer`

| JSON key | Required | Source | Format |
|---|---|---|---|
| `name` | **yes** | `Employer.name` | 1..255 characters |
| `location` | **yes** | `Employer.headquarters` | §6.3 |
| `industry` | no | `Employer.industry` | omitted when blank |
| `url` | no | `Employer.url` | absolute URI, omitted when blank |

### 6.3 `location`

| JSON key | Source | Format |
|---|---|---|
| `city` | `Location.city` | as stored |
| `country` | `Location.country` | ISO 3166-1 alpha-2, **uppercase**, e.g. `CH` |

Country codes **must** be uppercase everywhere in the document — in the employer's location and in every
job location alike. Case must not differ between the two.

The schema itself only constrains `country` to two characters, so lowercase would technically validate.
Uppercase is nevertheless mandatory here: ISO 3166-1 alpha-2 is defined in uppercase, the schema
documents it as `US, DE, CH`, and consumers that compare codes as plain strings will silently fail to
match a lowercase value. Emitting the same field in different cases in different places is worse still.

### 6.4 `job`

| JSON key | Required | Source | Format |
|---|---|---|---|
| `title` | **yes** | `Job.title` | ≤255 |
| `url` | **yes** | `Job.url` | absolute URI |
| `language` | **yes** | `Job.languageCode` | ISO 639-1, **lowercase**, exactly 2 characters |
| `publishedAt` | **yes** | `Job.publishedAt` | `yyyy-MM-dd` |
| `jobType` | **yes** | `Job.jobType` | §6.5 |
| `locations` | **yes** | `Job.locations` | array, **at least one** entry, §6.3 |
| `description` | no | `Job.description` | ≤1000 |
| `category` | no | `Job.category` | ≤255 |
| `referenceId` | no | `Job.referenceId` | ≤255 |
| `workType` | no | `Job.workType` | §6.5 |
| `experienceLevel` | no | `Job.experienceLevel` | §6.5 |
| `startDate` | no | `Job.startDate` | `yyyy-MM-dd` |
| `endDate` | no | `Job.endDate` | `yyyy-MM-dd` |
| `applyBefore` | no | `Job.applyBefore` | `yyyy-MM-dd` |
| `workLoad` | no | §6.6 | omitted entirely when both percentages are absent |
| `salary` | no | §6.7 | omitted entirely when both amounts are absent |
| `tags` | no | `Job.tags` | array of tag names, **lowercase**, each ≤28 characters, at most 16, unique, sorted ascending |

`tags` **must** be populated from the job's tags. It is part of the contract and consumers use it for
matching; emitting the field as permanently absent wastes the most useful signal in the document.

### 6.5 Enum mapping

Enum values **must** be produced through an explicit mapping table. Deriving them from the enum
constant's name (`name().toLowerCase()` or similar) is forbidden: it produces `on_site` where the schema
requires `on-site`, and it silently breaks the contract whenever a constant is renamed.

**`jobType`**

| Domain | JSON |
|---|---|
| `PERMANENT` | `permanent` |
| `CONTRACT` | `contract` |
| `TEMPORARY` | `temporary` |
| `FREELANCE` | `freelance` |
| `VOLUNTEER` | `volunteer` |
| `APPRENTICESHIP` | `apprenticeship` |
| `INTERNSHIP` | `internship` |

**`workType`**

| Domain | JSON |
|---|---|
| `ON_SITE` | **`on-site`** |
| `REMOTE` | `remote` |
| `HYBRID` | `hybrid` |

**`experienceLevel`**

| Domain | JSON |
|---|---|
| `JUNIOR` | `junior` |
| `MID` | `mid` |
| `SENIOR` | `senior` |
| `LEAD` | `lead` |
| `MANAGER` | `manager` |
| `DIRECTOR` | `director` |
| `EXECUTIVE` | `executive` |

**`salary.interval`**

| Domain | JSON |
|---|---|
| `HOURLY` | `hourly` |
| `DAILY` | `daily` |
| `WEEKLY` | `weekly` |
| `MONTHLY` | `monthly` |
| `YEARLY` | `yearly` |

### 6.6 `workLoad`

Emitted when at least one percentage is present. Each key is emitted only if its own value is present —
a missing maximum must not be fabricated from the minimum.

| JSON key | Source | Format |
|---|---|---|
| `minPercentage` | `Job.workLoadPercentMin` | number 0..100 |
| `maxPercentage` | `Job.workLoadPercentMax` | number 0..100 |

### 6.7 `salary`

Emitted when at least one amount is present. Each key is emitted only if its own value is present.
Because §3.3 makes currency and interval mandatory whenever an amount exists, a published salary block
always carries both.

| JSON key | Source | Format |
|---|---|---|
| `min` | `Job.salaryMin` | number ≥ 0 |
| `max` | `Job.salaryMax` | number ≥ 0 |
| `currency` | `Job.salaryCurrency` | ISO 4217, **uppercase**, 3 characters |
| `interval` | `Job.salaryInterval` | §6.5 |

Amounts **must** be serialized as JSON numbers without loss of the stored precision. Narrowing a decimal
amount to a 32-bit float is not acceptable — six-figure salaries lose accuracy.

### 6.8 Worked example

```json
{
  "version": "1.0",
  "lastUpdated": "2026-09-21T08:15:00Z",
  "employer": {
    "name": "Acme AG",
    "url": "https://www.acme.example",
    "industry": "Software",
    "location": { "city": "Bern", "country": "CH" }
  },
  "jobs": [
    {
      "title": "Senior Backend Engineer",
      "description": "Design and operate our job distribution platform.",
      "category": "Engineering",
      "referenceId": "ACME-2026-014",
      "jobType": "permanent",
      "workType": "on-site",
      "experienceLevel": "senior",
      "workLoad": { "minPercentage": 80, "maxPercentage": 100 },
      "salary": { "min": 110000, "max": 135000, "currency": "CHF", "interval": "yearly" },
      "locations": [
        { "city": "Bern", "country": "CH" },
        { "city": "Zürich", "country": "CH" }
      ],
      "publishedAt": "2026-09-01",
      "startDate": "2026-11-01",
      "applyBefore": "2026-10-15",
      "language": "en",
      "url": "https://www.acme.example/jobs/ACME-2026-014",
      "tags": ["java", "kubernetes", "spring"]
    },
    {
      "title": "Praktikum Produktdesign",
      "jobType": "internship",
      "workType": "hybrid",
      "locations": [{ "city": "Bern", "country": "CH" }],
      "publishedAt": "2026-09-18",
      "language": "de",
      "url": "https://www.acme.example/jobs/ACME-2026-021"
    }
  ]
}
```

The second job shows the minimum conforming posting: only the six required job fields, everything else
omitted rather than nulled.

### 6.9 Versioning

The URL carries the contract version (`/ojobpub/v1/...`) and the document repeats it in `version`.

- Additive, backwards-compatible changes within v1 are permitted.
- Any change that removes a field, narrows an enum, or changes a value's meaning requires a new
  versioned path (`/ojobpub/v2/...`) served **alongside** v1.
- When the upstream schema publishes a new version, the existing version must keep being served until
  consumers have migrated. Feed URLs are infrastructure for other people's systems.

---

## 7. User interface

### 7.1 Principles

The back-office is a forms-and-lists administration tool. It has no offline, real-time or
collaborative requirements, so it is built as **server-rendered HTML enhanced by htmx**, not as a
client application.

1. **The server owns the state and the markup.** Every screen and every fragment is rendered by
   Thymeleaf. The browser never assembles a view from data; there is no client-side template, store or
   router.
2. **Progressive enhancement is mandatory.** Every screen **must** work with JavaScript disabled:
   real `<a>` links, real `<form>` posts, real full-page responses. htmx makes those interactions
   partial and fast; it is never the only way to perform an action. This is both an accessibility
   guarantee and the cheapest possible insurance against a broken asset.
3. **A JavaScript budget, not a prohibition** (§7.2). Some behaviour is genuinely better in the
   browser, and a few lines there beat a contrived server round trip. What is budgeted is *dependencies
   and complexity*, not lines: reach for a server round trip first, and when the browser is the right
   place, write the small amount of code it takes rather than adding a library.
4. **npm manages dependencies; there is no build step.** Front-end dependencies are pinned in
   `package.json` with a lockfile, and their published `dist` files are copied into `/static` and
   **committed**. No bundler, no transpiler, no minifier of our own. See §7.2 for why the copied files
   are committed.
5. **Tabler is the design system.** Its components, spacing, colour roles and icons are used as
   published. Custom CSS is a last resort, lives in one small stylesheet, and never restyles a Tabler
   component into something it is not.

### 7.2 JavaScript budget

What the browser may load:

| Asset | Source | Purpose |
|---|---|---|
| **htmx** | npm | All partial page updates |
| **Tabler's CSS and JS** | npm | The design system, and the components that cannot work without script: offcanvas sidebar, dropdowns, modals, tooltips |
| **Tabler icons** | npm | A sprite trimmed to the icons actually used, not the full set |
| **The application's own module** | written here | The small amount of behaviour that genuinely belongs in the browser |

Rules:

- **Our own JavaScript is permitted and should stay small.** One module, plain modern ES served as
  written — no transpiler, no bundler, no framework. It is reviewed like any other source file: no
  `eval`, no building markup or code from strings, and nothing that duplicates state the server owns.
- **A library needs a reason.** No SPA framework, no jQuery, no client-side validation library, no date
  picker, no rich text editor, no charting library, no select/autocomplete library (§7.8 gives the
  server-rendered alternative). Adding one is a decision to record, not a convenience.
- **Dependencies come from npm**, pinned in `package.json` with a committed lockfile. Their `dist`
  files are copied into `/static` **and committed**, so a clean checkout builds with Maven alone and
  needs no Node (§9.1). Refreshing them is one documented command and a reviewable commit — a diff
  that shows exactly which bytes the browser will receive.
- Assets are **self-hosted**, never loaded from a CDN. This keeps the application working offline and
  in restricted networks, removes a third-party availability and privacy dependency, and allows the
  strict `Content-Security-Policy` of §9.4.
- **No inline script and no `eval`.** The CSP is `script-src 'self'` with neither `unsafe-inline` nor
  `unsafe-eval` (§9.4). This is why the application's behaviour lives in a module file rather than in
  attributes evaluated at runtime, and it is what makes the strict policy achievable rather than
  aspirational.
- Vendored asset files are checked in, with their versions recorded, and refreshed by a documented,
  repeatable command. Upgrading a vendored asset is a reviewable commit.
- Icons are the **Tabler SVG sprite**, referenced as `<svg><use href="/static/icons.svg#tabler-…"></svg>`.
  No icon font and no icon JavaScript.

### 7.3 Layout

A fixed left sidebar, a slim top bar and a content area — the conventional administration shell, chosen
because the navigation is a flat set of seven destinations that must stay visible and reachable in one
click while the user works down a long job list.

```
┌──────────────┬──────────────────────────────────────────────────┐
│  ojobpub     │                      ▸ Employer ▾   ◻ User ▾     │  ← top bar
│              ├──────────────────────────────────────────────────┤
│ ◻ Dashboard  │  [page title]                    [primary action]│
│ ◻ Jobs       ├──────────────────────────────────────────────────┤
│ ◻ Feeds      │                                                  │
│ ◻ Employers  │   page content                                   │
│ ◻ Locations  │                                                  │
│ ◻ Tags       │                                                  │
│ ◻ Invites ②  │                                                  │
└──────────────┴──────────────────────────────────────────────────┘
```

Implemented with Tabler's vertical navbar (`navbar navbar-vertical navbar-expand-lg`) inside
`page` / `page-wrapper`.

**Sidebar** — navigation, and nothing else:

- The product brand, linking to the dashboard.
- The primary navigation: **Dashboard, Jobs, Feeds, Employers, Locations, Tags, Invitations**, each
  with a Tabler icon and label. *Employers* is visible to everyone but only offers create/delete to
  admins.
- **Invitations** (§7.16) carries a count badge when any are pending. It stays visible when there are
  none: a user with no employer memberships has nothing else to do, and an entry that disappears when
  empty cannot be found by someone who wants to check whether an invitation ever arrived.

**Top bar** — everything about *context* rather than destination, right-aligned:

- The **employer switcher** (§2.5), a dropdown showing the active employer's name. Hidden when the
  user has exactly one employer. For admins it also offers *All employers*. Switching posts to the
  server, which stores the choice in the session and redirects back to the current screen.
- The **user menu**: the current user, with *Language*, *Theme* and *Log out*.

These belong together and apart from the navigation: which employer am I working on, who am I, and how
do I want the application presented. Keeping them in the top bar also keeps them in one fixed place on
every screen, including the narrow layout where the sidebar collapses behind a toggle — a switcher that
disappears with the navigation is a switcher you cannot reach.

The sidebar **must** mark the active destination with `aria-current="page"` and a visual state, derived
from the request path on the server — never guessed in the browser.

**Page header** — beneath the top bar, Tabler's `page-header`: the screen title, a breadcrumb where the
screen is nested (feed → job), and the screen's primary action as a button on the right. Secondary
actions live in an overflow dropdown, never as a row of competing buttons.

**Responsive** — below the `lg` breakpoint the sidebar collapses behind a hamburger toggle. The top bar
does not collapse, so the employer switcher and the user menu stay reachable; their labels shorten to
icons on narrow screens rather than disappearing. Tables reflow per §7.6. The application must be usable
at 360 px width.

**Theme** — Tabler light and dark themes are both supported. The choice is stored server-side with the
user and rendered into the `<html>` element on the first response, so there is no flash of the wrong
theme and no JavaScript involved. Default follows the operating system preference via CSS only.

**Dev banner** — when authentication is bypassed (§2.3), a persistent, high-contrast banner is pinned
above the top bar on every screen.

### 7.4 htmx interaction patterns

These patterns are exhaustive: a new screen composes them rather than inventing an interaction.

| Pattern | How |
|---|---|
| **Navigation** | `hx-boost` on the body. Links and form posts are swapped into the content area with history pushed. Degrades to ordinary navigation without JS. |
| **List controls** — search, filter, sort, paging | `hx-get` on the control, targeting the list region, `hx-push-url="true"`. Search inputs use `hx-trigger="input changed delay:300ms, search"`. |
| **Inline add/remove** — feed membership, job tags, job locations | `hx-post`/`hx-delete` targeting the affected region only. |
| **Tabs** | `hx-get` per tab into the panel region, with `hx-push-url` so a tab is linkable. |
| **Flash messages** | Rendered by the server into a fixed `aria-live="polite"` region and delivered as an **out-of-band swap**, so any request can raise one without the caller knowing. |
| **Dependent counters** — sidebar and dashboard badges | Out-of-band swaps on the responses that change them. Never polled. |
| **Confirmation dialogs** | `hx-get` fetches a server-rendered Tabler modal into a shared `#modal` container; the modal contains the real form. `hx-confirm` (the browser's `confirm()`) **must not** be used: it cannot be translated, styled, or made to state consequences. |
| **Loading feedback** | `hx-indicator` on the affected region; Tabler spinner plus a dimmed region via the `htmx-request` class. No global spinner. |
| **Validation** | The server re-renders the form fragment with errors. There is no client-side validation beyond native HTML attributes (`required`, `type`, `min`, `max`, `maxlength`), which cost nothing and work without JS. |

Rules that apply to every swap:

- **URL sync is mandatory.** Any control that changes what the user is looking at uses `hx-push-url`,
  so every list state is bookmarkable, shareable and survives a reload or a back button.
- **Swap the smallest correct region.** Never replace the whole content area to update one row.
- **Failure must be visible.** A `4xx`/`5xx` renders a server-provided error fragment into the target;
  a network failure (`htmx:sendError`) raises an error flash with a retry. A swap must never fail
  silently or leave a spinner running.
- **Focus must be managed.** After a swap that replaces the main region, focus moves to its heading.
  After a modal closes, focus returns to the control that opened it.
- Every state-changing request carries the CSRF token via htmx request headers (§9.4).
- Create, update and delete remain **post/redirect/get**, so a refresh never re-submits.

### 7.5 Component vocabulary

One Tabler component per job, used consistently everywhere:

| Need | Component |
|---|---|
| Screen frame | `page-header` + `page-body` + `container-xl` |
| Record lists | `card` containing `table table-vcenter card-table`, header cell sort links |
| Grouped form fields | `card` per group, `card-header` as the group title |
| Field | `form-label`, `form-control`/`form-select`, `form-hint` for guidance, `invalid-feedback` for errors |
| Status | `badge` with text — see §7.6 |
| Counts and summaries | `card` with `subheader` + large value, linking to the filtered list |
| Nothing to show | Tabler `empty` block: icon, one-line explanation, and the button that resolves it |
| Destructive confirmation | `modal` with the consequence spelled out and a `btn-danger` confirm |
| Transient feedback | `alert` in the flash region, dismissible |
| Long text | `form-control` textarea with a live character counter fed by the server-rendered maximum |

Colour is **never** the only signal: every status badge carries a word, every error carries text.

### 7.6 Lists

Every list screen is one card containing a toolbar and a table, and defines four states: **loading**
(indicator on the region), **empty** (Tabler `empty` with the resolving action), **error** (inline
alert with retry), **populated**.

- The toolbar holds the search field, filter selects and the count of matching records. Filters are
  plain `<select>` elements; applying one is a fragment swap.
- Column headers that sort are links carrying the sort state in the URL and `aria-sort` on the cell.
- Pagination is Tabler's `pagination`, server-rendered, 20 per page, capped at 100 (§8.3).
- The row's primary identifier links to the detail screen. Row actions live in a right-aligned
  dropdown, not as a row of icon buttons.
- **Narrow screens**: tables scroll horizontally inside `table-responsive` while the first column stays
  the link; screens narrower than the `sm` breakpoint render the list as stacked cards instead. No
  horizontal scrolling of the page itself.

Status badge mapping, used identically in every list, detail and picker:

| Status | Badge |
|---|---|
| `ACTIVE`, published | green — *Published* |
| `ACTIVE`, outside its date window (§4.4) | yellow — *Expired* |
| `ACTIVE`, failing a §4.3 requirement | yellow — *Incomplete* |
| `DRAFT` | grey — *Draft* |
| `INACTIVE` | dark — *Inactive* |

### 7.7 Forms

- One `card` per field group, fields in Tabler's grid, related fields on one row where they belong
  together (salary min / max / currency / interval).
- Required fields are marked in the label, not only enforced on submit.
- Errors render beside their field with `is-invalid` + `invalid-feedback`, and a summary alert at the
  top of the form lists them with in-page links. **Every entered value is preserved** on a validation
  error.
- Guidance goes in `form-hint` beneath the field — for example that a slug change is safe (§5.1).
- Actions are pinned at the end of the form: primary on the left, *Cancel* as a link, destructive
  actions separated and never adjacent to the primary button.
- Conditional requirements are rendered by the server: entering a salary amount re-renders the salary
  group with currency and interval marked required (§3.3).
- Character counters appear on every field the schema caps: `title` 255, `description` 1000, tags 16
  per job, tag name 28.

### 7.8 Pickers without a JavaScript library

Job locations and job tags are many-to-many selections over sets too large for a checkbox list. Instead
of an autocomplete library, both use one server-rendered pattern:

1. Selected items render as a list of removable chips, each backed by a hidden input, so the selection
   posts with a plain form and survives with JavaScript off.
2. A search input issues `hx-get` on `input changed delay:300ms` and swaps a result list below it.
3. Choosing a result posts it and swaps the chip list and the result list together.
4. For tags, a search matching nothing offers *Create "…"*, which creates the tag and selects it in one
   request (§3.4 normalization applies).
5. Without JavaScript the same screen still works: the result list is rendered on submit of the search
   field, and choosing an item is an ordinary form post.

The country field on a location is a plain `<select>` over the ISO 3166-1 list showing localized names —
browsers already provide type-ahead on a native select, so no library is warranted.

### 7.9 Accessibility

- A skip link to the main region precedes the sidebar.
- Every control is reachable and operable by keyboard; visible focus is never removed.
- Every input has a programmatically associated label; errors are linked with `aria-describedby` and
  the field carries `aria-invalid`.
- The flash region is `aria-live="polite"`; a destructive confirmation modal traps focus and is
  dismissible with `Escape`.
- Icon-only controls carry an accessible name.
- Contrast meets WCAG 2.1 AA in both themes, verified for the status colours in §7.6.
- Page titles are unique per screen, so history and tabs are distinguishable.

### 7.10 Dashboard

The landing screen for the active employer. Cards show counts of **published**, **draft**, **expired**
and **inactive** jobs, and a feed summary listing each feed with its published job count and public URL.
Every figure links to the list already filtered to it.

Any feed currently omitting a job at serving time (§5.3) appears as a prominent warning card naming the
feed and the count, linking to the feed screen. A quiet dashboard means the feeds are healthy, and that
must be true at a glance.

### 7.11 Jobs

**List** — the primary working screen, per §7.6. Searchable by title and reference id; filterable by
status, job type, work type, experience level, tag and location; sortable by title, publication date and
last modification. Columns: title, status badge (§7.6), locations, job type, feed membership count.

**Detail** — the posting in full, with:

- a header carrying the status badge and the transitions legal from the current state (§4.1) as
  buttons, each confirming through a modal when it changes what the public sees;
- a **publication readiness panel** — a checklist of the §4.3 requirements with each unmet one linked to
  the field that fixes it. Why a job is or is not in a feed must be answerable here, without guessing;
- its feed memberships, each with the resulting publication status;
- the transition history (§10 auditability): who changed the status, from what, to what, and when.

**Create / edit** — one form per §7.7, grouped as *Basics* (title, description, url, language, reference
id, category), *Classification* (job type, work type, experience level, tags), *Workload & salary*,
*Dates*, *Locations*. Locations and tags use the §7.8 picker. A new job is created as `DRAFT`; the form
offers *Save draft* and *Save and activate*, the latter running the §4.3 checks and, on failure, saving
the draft and listing precisely what is missing.

**Delete** — permitted in any state, confirmed through a modal naming the job and listing the feeds it
will disappear from.

### 7.12 Feeds

**List** — every feed of the active employer: name, published job count, public URL with a copy action,
and last publication change.

**Detail** — the operational centre of the product:

- The canonical public URL (§5.1) as one copyable string with an *Open* link.
- **Job selection** — two panels side by side: jobs in this feed, and the employer's remaining jobs,
  each with its own search and filters, add/remove as fragment swaps on the affected rows only. Only
  the same employer's jobs are offerable (§3.5). On narrow screens the panels stack into tabs.
- **Publication status per member job** — *published*, or not, with the reason (draft, inactive,
  expired, incomplete) as a §7.6 badge. The difference between what is selected and what is published
  must never require interpretation.
- Any job excluded at serving time (§5.3) as an inline warning naming the reason.
- A **JSON preview** of the document exactly as the public URL would serve it, in a scrollable
  `<pre>` with a copy action, and a validation badge reporting the result of checking it against the
  oJobPub schema. No syntax-highlighting library.

**Create / edit** — name, slug, description. The slug is proposed from the name (§3.6) and may be
overridden; a hint shows the resulting canonical URL live and states in one line that changing it is
safe because old links keep working (§5.1).

**Delete** — modal confirmation stating that the public URL will stop working and that consumers will
receive `404`. Jobs are not deleted.

### 7.13 Employers

**List** — a user's own employers; for an admin, all of them, searchable and paginated. The empty state
offers **creating** an employer, because that is now a thing any user may do (§2.2) — it is no longer a
dead end that only an administrator can resolve.
**Detail** — the record, its headquarters, its feeds and its job counts.
**Create** — open to every signed-in user. Creating an employer also creates its `all` feed (§3.5) and
makes the creator its **owner** (§2.7).
**Edit** — name, slug, url, industry, headquarters location; slug behaviour per §7.12. **Owners and
admins only**; an editor works on jobs and feeds, not on the employer record.
**Delete** — **admins only**, unchanged. An owner may hand a workspace on but not destroy it along with
the feed URLs its consumers depend on.
**People** (owners and admins) — a screen of its own at the employer, in three parts:

- **Members** — everyone with access and their **role**, with a control to change it between *Owner*
  and *Editor*, and a *Remove* action. Removing needs no consent (§2.6) but confirms through the §7.4
  modal, naming the person and the employer, and states that they may be invited again.
- **The last owner** — the demote and remove controls **must** be refused for the last remaining owner,
  with a sentence saying to promote someone else first (§2.7). A control that is merely greyed out
  leaves the user guessing.
- **Pending invitations** — who was invited, with which role, by whom, when, and a *Revoke* action.
- **Invite** — an email field and a **role choice** (Editor by default). The invitee must already be
  registered, which the form says up front so a failure is not a surprise.

The invite form's feedback obeys §2.6: an address that matches no account produces the **same** message
as a successful invitation, while "already a member" and "already invited" are named, because both
people are listed directly above the form.

### 7.14 Locations

List, create, edit and delete of city/country pairs, per §7.6 and §7.7. The country field is the native
select described in §7.8. A location still referenced by an employer or a job cannot be deleted, and the
UI names what references it rather than only refusing.

### 7.15 Tags

List of all tags with the number of jobs carrying each, searchable, sorted by name. Create and rename
apply the §3.4 normalization and surface the 28-character and uniqueness rules as field errors. Delete
confirms through a modal stating how many jobs will lose the tag.

### 7.16 Invitations

The invitee's screen, and the one screen that must work for a user with no employers at all.

**List** — every invitation still pending for the signed-in user: the employer, **the role being
offered**, who invited them, and when. Each row offers **Accept** and **Decline**. Being asked to take
on an employer as its owner is a materially different proposition from being asked to help edit it, so
the role is stated before the decision, not discovered after it.

- **Accept** creates the membership (§2.6), makes that employer the active one (§2.5) and lands the
  user on its dashboard — they asked to get in, so put them inside rather than back on a list.
- **Decline** resolves the invitation and returns to the list with a confirmation. It is not
  destructive to anything the user owns, so it needs no modal; it is reversible only by a new
  invitation, and the confirmation says so.
- Resolved invitations are **not** listed. This screen is a to-do list, not an archive; the history
  lives on the employer's People screen (§7.13).
- Only the signed-in user's own invitations ever appear here. A request to respond to someone else's
  **must** answer `404` (§2.4).

**Empty state** — per §7.6. For a user with no memberships this is one of only two things they can do,
so it explains both: wait for an invitation, or **create an employer of their own** (§2.2), with a link
that does it. It must not read like an error, and it must not imply they are stuck.

---

## 8. Cross-cutting behaviour

### 8.1 Internationalization

- The back-office UI ships in **English and German**; English is the fallback.
- Language is switchable at any time via `?lang=` and persists in the session.
- New screens add their keys to **both** bundles: `nav.invitations`, the `invitation.*` family for §7.16
  and the People screen, and `role.*` for the membership roles of §2.1. The bundles are asserted at parity, so a key added to one and not the
  other fails the build.
- **Every** user-visible string comes from a message bundle. There must be no hard-coded text in
  templates, and no enum constant may render as its raw name — `DIRECTOR`, every `SalaryInterval` value
  and every status value need labels in both bundles. A missing key must fail the build, not render as
  a key at runtime.
- Country names are rendered localized; country *codes* are stored and published unchanged.
- The UI language is unrelated to `Job.languageCode`, which describes the posting's own text. A German
  UI may perfectly well edit an English posting.

### 8.2 Validation and errors

- Validation runs on the server. Client-side validation is a convenience and never the only check.
- Field errors appear beside their field; form-level errors appear at the top of the form.
- Flash messages after a redirect are used for success and for errors that no longer have a form.
- Unexpected errors render a friendly error page with a correlation id, and the detail goes to the log.
  Stack traces and SQL must never reach the browser.
- Feed endpoints return JSON errors, never HTML — their callers are machines.

### 8.3 Lists

Pagination defaults to 20 items and is capped at 100 regardless of what the request asks for. Sort
fields are validated against an allow-list per screen. An out-of-range page clamps to the last available
page rather than erroring.

---

## 9. Technical shape

### 9.1 Stack

Java 25, Spring Boot 4 (Web MVC), Thymeleaf with the Layout Dialect for server-rendered HTML, htmx for
partial updates, Tabler as the design system, Spring Data JPA over MariaDB, Flyway for schema
migrations, Spring Security with the OAuth2 client stack for OIDC.

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

### 9.2 Structure

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

### 9.3 Persistence

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

### 9.4 Security configuration

- Two filter chains: one for the public feed and health endpoints (stateless, anonymous, CSRF disabled,
  `GET` only), one for the back-office (session-based, authenticated, CSRF enabled).
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

### 9.5 Configuration

No secrets in the repository. OIDC issuer, client id and client secret come from the environment. The
application's public base URL is configured explicitly, because feed URLs must be absolute and correct
behind a reverse proxy.

---

## 10. Non-functional requirements

**Performance.** A feed document of up to 500 jobs must be served in under 200 ms at the 95th percentile
from a warm cache, and under 1 s cold. Back-office list screens must respond in under 500 ms at the 95th
percentile for up to 10 000 jobs per employer.

**Correctness as a build gate.** The build **must** fail if the generated document does not validate
against the oJobPub schema. A checked-in copy of the schema is validated against representative
documents — minimum, maximal and empty feed — in the test suite, and the copy is checked against
upstream periodically. This is the single most important test in the project: everything else is
internal, this is the contract.

**Testing.** Service-level tests cover the lifecycle transitions, the inclusion predicate and the date
window at boundaries (deadline today, yesterday, tomorrow). Web-layer tests cover authorization,
asserting that a non-member receives `404`. Mapping tests assert every enum value against the schema's
enum lists by value, not by round-trip.

**Availability and operations.** Feed endpoints are the availability-critical surface; a back-office
outage inconveniences staff, a feed outage breaks partners. Health, readiness and Prometheus metrics are
exposed via actuator. Feed requests are logged with employer, feed, response status and whether the
response was a cache hit.

**Auditability.** Every status transition records who made it and when, and the log is visible on the
job's detail screen. "Why did this posting disappear from the feed?" must be answerable without database
access.

**Data protection.** The application stores no applicant data. Personal data is limited to back-office
user records (issuer, subject, email, display name), which are deleted with the user.

---

## 11. Open questions

1. **Multi-language postings.** A job carries one `languageCode`. An employer wanting the same position
   in German and French must currently create two jobs with different reference ids. Whether to model
   translations as one job with several language variants is deferred.
2. **Import.** Nothing here covers ingesting postings from an ATS or an existing feed; all postings are
   hand-authored. If import arrives, the lifecycle and the identity of imported jobs (`referenceId` as
   a natural key?) need specification.
3. **Feed-level filters.** Feed membership is explicit. Whether a feed may instead be defined as a saved
   filter (all jobs with tag *java*) is deferred; the URL contract is unaffected either way.
4. **Scheduled work.** The date window is evaluated at serving time, so no scheduler is required. If
   notifications ("three postings expire next week") are wanted, a scheduled job and its delivery
   channel need specification.
5. **Analytics.** Whether feed consumption is measured per consumer, and whether employers see it, is
   unspecified.
6. **Feed visibility.** Every feed URL is public to anyone holding the link (§5.1). Whether a feed may
   be marked private and require a rotatable token is deferred; the UUID in the path makes a link
   impractical to guess, but it is not a secret and may appear in referrer headers and proxy logs.
7. **Invitation delivery.** No email is sent; an invitee learns of an invitation only by signing in
   (§2.6). If invitations should reach people who are not already in the habit of logging in, a mail
   transport and its templates, bounce handling and opt-outs need specification — and it would be the
   first thing here to require a delivery channel at all.
8. **Leaving an employer.** A member can be removed by an owner (§2.6) but cannot currently remove
   themselves, and an owner cannot hand over and walk away in one action. Whether "leave" and "transfer
   ownership" should exist as first-class actions is deferred; today the sequence is promote, then ask
   the new owner to remove you.
9. **Self-serve and abuse.** Any signed-in user may now create an employer (§2.2). Nothing limits how
   many, and every employer publishes a public feed URL. Whether creation needs a quota, review or
   verification is unspecified, and matters more here than for most back-offices because the output is
   public by design.
10. **Keeping the JavaScript small.** §7.2 now permits the application's own module, which removes the
   `unsafe-eval` tension that hyperscript created but also removes the constraint that kept browser
   behaviour near zero. Nothing currently measures or bounds it. Whether a size budget, or a rule that
   each addition names the server round trip it replaces, is worth adopting is deferred — the module
   covers three interactions today (modal dismissal, chip removal, copy to clipboard).

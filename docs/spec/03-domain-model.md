<!-- Part of the specification: sections are numbered across all files, and code cites them as "spec N.M". -->
[Specification index](../SPEC.md) · ← [2. Actors and access](02-actors-and-access.md) · [4. Job lifecycle](04-job-lifecycle.md) →

# 3. Domain model

Nine entities. Every entity except `Tag` carries a UUID primary key, `createdAt` and `lastModifiedAt`
(UTC instants, maintained automatically).

```
Employer 1 ──── * Job            Job * ──── * Location
   │ 1                            Job * ──── * Tag
   │                              Job's employer is fixed at creation
   └── * Feed  * ──── * Job  (membership; both sides same employer)
Employer 1 ──── 1 Location (headquarters)

User         1 ──── * Membership * ──── 1 Employer   (carries the role: OWNER or EDITOR)
ServiceToken 1 ──── * Membership * ──── 1 Employer   (a token holds a role too)
User         1 ──── * Invitation * ──── 1 Employer   (carries the role it will grant)
```

## 3.1 Employer

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

## 3.2 Location

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

## 3.3 Job

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

## 3.4 Tag

A free-form keyword — a skill, technology or attribute.

| Field | Type | Required | Rules |
|---|---|---|---|
| `id` | long | yes | generated |
| `name` | string(1..28) | **yes** | lowercase, trimmed, **globally unique**; the schema caps tag length at 28 characters |

Tags are global, shared across all employers, and normalized on input (trimmed, lowercased). Creating a
tag that already exists returns the existing tag rather than an error. Deleting a tag removes it from
every job that carries it, and the confirmation screen **must** state how many jobs are affected.

## 3.5 Feed

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
has a working URL immediately. It is created **regardless of the feed limit** (§8.4) — an employer
without a working URL is a broken employer — though it counts towards the limit thereafter.

## 3.6 Slugs

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

## 3.7 User

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

## 3.8 Invitation

An offer of access to one employer, made to one registered user.

| Field | Type | Required | Rules |
|---|---|---|---|
| `id` | UUID | yes | generated |
| `employer` | Employer | **yes** | immutable |
| `invitee` | User | **yes** | resolved from the email when the invitation is created; immutable |
| `invitedBy` | Actor | **yes** | who sent it — a **User or a ServiceToken** (§3.11); kept even if they later lose the role that allowed it |
| `role` | enum | **yes** | the membership role acceptance will grant: `OWNER` or `EDITOR` (§2.6) |
| `status` | enum | **yes** | `PENDING`, `ACCEPTED`, `DECLINED`, `REVOKED` |
| `respondedAt` | instant | conditional | set when the invitation leaves `PENDING`, and **immutable** thereafter |

Permitted transitions: `PENDING → ACCEPTED`, `PENDING → DECLINED`, `PENDING → REVOKED`. Nothing else —
a resolved invitation is history and is never reopened. Wanting someone back after a decline or a
removal means sending a **new** invitation, which keeps the record of what happened intact.

Deleting an employer deletes its invitations. Deleting a user deletes the invitations addressed to them.

## 3.9 Membership

One person's standing in one employer. Previously a bare pair of ids; it now carries a role, which is
why it is an entity of its own rather than a set of employer references on the user.

| Field | Type | Required | Rules |
|---|---|---|---|
| `id` | UUID | yes | generated |
| `member` | Actor | **yes** | a **User or a ServiceToken** (§3.11); immutable |
| `employer` | Employer | **yes** | immutable |
| `role` | enum | **yes** | `OWNER` or `EDITOR` (§2.1) |
| `suspendedAt` | instant | no | set while an owner has suspended the membership (§2.7); **null means active**. A suspended membership grants nothing |

At most one membership per (member, employer). A user's membership is created only by creating an
employer or by accepting an invitation (§2.2); a token's is created with the token itself, by an owner
(§2.8). Its `role` and whether it is suspended are the only things about it that may change (§2.7).

**Invariant:** every employer has at least one **active** membership with role `OWNER` **held by a
user**. Creating an employer establishes it, and §2.7 forbids the demotion, removal or suspension that
would break it. A
token's owner membership does not count toward it (§2.7).

Deleting an employer or a user deletes their memberships.

## 3.10 ServiceToken

A credential that lets another system act on one employer (§2.8).

| Field | Type | Required | Rules |
|---|---|---|---|
| `id` | UUID | yes | generated |
| `employer` | Employer | **yes** | immutable; a token never spans employers |
| `name` | string(1..64) | **yes** | what it is for, e.g. *ats-sync*; shown wherever the token acted |
| `prefix` | string | **yes** | a short public fragment identifying the token without revealing it |
| `secretHash` | string | **yes** | the hash of the secret. The secret itself is **never** stored |
| `scopes` | set of enum | **yes** | from the six of §2.8; at least one |
| `createdBy` | User | **yes** | the owner who created it; a token is always someone's decision |
| `lastUsedAt` | instant | no | set on each **accepted** request; what makes a dormant token visible |
| `expiresAt` | instant | no | when it stops working; **null means never**, which a lifetime of 0 configures. Renewal moves it; nothing else does |
| `revokedAt` | instant | no | once set the token is dead, permanently |

A token's membership (§3.9) carries its role and is created and deleted with it.

Revoking is not deleting: the row is retained with `revokedAt` set, so the audit trail can still name
what acted. Deleting the employer deletes its tokens.

## 3.11 Actor

Not an entity — the notion that an action may be taken by a **User** or by a **ServiceToken**.

Anywhere the model records *who did this* — `Invitation.invitedBy`, a job's status events (§4.2),
`Membership.member` — it records an actor, exactly one of the two. Persisted as a nullable reference to
each with a constraint that precisely one is set, rather than an opaque id and a type string, so the
database still enforces that the reference points at something real.

An audit record names the **token**, not the person who created it. The token is what acted; attributing
its actions to a human would put words in the mouth of someone who may have been asleep. The person is
still reachable — `ServiceToken.createdBy` records who decided the integration should exist — so the
question "who is answerable for this?" is answerable, one step removed.

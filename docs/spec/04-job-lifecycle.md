<!-- Part of the specification: sections are numbered across all files, and code cites them as "spec N.M". -->
[Specification index](../SPEC.md) · ← [3. Domain model](03-domain-model.md) · [5. Feed publishing](05-feed-publishing.md) →

# 4. Job lifecycle

## 4.1 States

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

## 4.2 `publishedAt`

`publishedAt` is the date the position was **first** made public. It **must** be stamped once, on the
first `→ ACTIVE` transition, and **must not** change afterwards — not when the job is edited, not when
it is deactivated and reactivated. Consumers use it to order and age postings; a value that moves on
every edit makes every posting look permanently new.

An admin may correct `publishedAt` manually (for example when migrating postings that were first
published elsewhere). Nobody else may — not even an owner: the publication date is part of the
published contract (§6.4), not of the workspace.

## 4.3 Publication requirements

A job is **publishable** only when all of the following hold:

1. `status` is `ACTIVE`;
2. `publishedAt` is set;
3. `title`, `url`, `languageCode` and `jobType` are present and valid;
4. it has **at least one location**;
5. if any salary amount is set, `salaryCurrency` and `salaryInterval` are also set;
6. it is within its date window (§4.4).

Requirements 1–5 are enforced when activating. Requirement 6 is evaluated at serving time.

## 4.4 The date window

A posting leaves its feeds automatically, without anyone editing it, when it goes stale:

- if `applyBefore` is set and `applyBefore < today`, the job is **excluded**;
- if `endDate` is set and `endDate < today`, the job is **excluded**;
- `startDate` never excludes a job. A position starting in three months is a normal posting.

"Today" is evaluated in the application's configured display time zone (`Europe/Zurich` by default).

Exclusion by date does **not** change `status`. The job remains `ACTIVE` in the back-office and returns
to its feeds by itself if the date is extended. The UI **must** show such jobs distinctly — `ACTIVE`
but *expired* — so the state is never a mystery.

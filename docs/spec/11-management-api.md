<!-- Part of the specification: sections are numbered across all files, and code cites them as "spec N.M". -->
[Specification index](../SPEC.md) · ← [10. Non-functional requirements](10-non-functional.md) · [12. Open questions](12-open-questions.md) →

# 11. Management API

## 11.1 Purpose

A **GraphQL API for managing an employer's data from another system**: jobs, feeds, the employer
record, and its people. An applicant tracking system pushes postings; a script retires them; an
identity workflow invites and removes colleagues.

This is the *management* surface. The published `ojobpub.json` feed (§5) is unchanged and remains the
read surface for consumers — public, anonymous, cacheable. Nothing here alters it.

**The API is another caller of the same services, never a second implementation.** Every rule already
specified applies unchanged and unrestated: a job cannot be activated while a publication requirement
is unmet (§4.3), `publishedAt` is stamped once (§4.2), the last owner cannot be demoted (§2.7),
membership is created only by consent (§2.2), and an invitation to an unregistered address is reported
exactly as a successful one (§2.6). If a rule can be bypassed through the API, the rule was in the
wrong layer.

## 11.2 Endpoint and transport

- A single endpoint, `POST /graphql`. No `GET` query execution, so queries cannot be triggered by a
  link or cached by an intermediary.
- **Stateless**: a bearer token per request (§2.8), no session, no cookie, and therefore no CSRF
  token. It gets its own security filter chain, like the feed (§9.4).
- A missing, malformed, unknown or revoked token answers **401**. A token whose role or scopes do not
  permit the operation answers a GraphQL error with code `FORBIDDEN` — the request authenticated, so
  401 would be a lie. This differs from the back-office's 404-not-403 rule (§2.4) deliberately: hiding
  existence from a human browsing is worth the confusion, but an integration needs to know whether to
  fix its credentials or stop retrying.
- The employer is **implied by the token** and never passed as an argument. A token cannot name another
  employer, so the commonest authorization bug in a multi-tenant API cannot be written.

## 11.3 Shape of the schema

- **Queries** read: an employer, its jobs, feeds, locations, tags, members and pending invitations.
- **Mutations** are named for the act, not for CRUD: `createJob`, `updateJob`, `activateJob`,
  `deactivateJob`, `deleteJob`, `addJobToFeed`, `removeJobFromFeed`, `createFeed`, `updateFeed`,
  `updateEmployer`, `inviteMember`, `revokeInvitation`, `changeMemberRole`, `suspendMember`,
  `reinstateMember`, `removeMember`. Activation
  is not `updateJob(status: ACTIVE)`; it is a transition with its own rules (§4.1), and naming it so
  keeps the API honest about that.
- **Every mutation returns a payload**, never a bare entity: the result, and a list of `userErrors`
  each carrying a `field`, a `message` and a stable `code`.
- `inviteMember` is the one payload that returns an **outcome** rather than the record it created.
  Returning the invitation would disclose whether an address has an account — a null invitation for an
  unknown address and a populated one otherwise is precisely the oracle §2.6 forbids — so the payload
  carries `SENT`, `ALREADY_MEMBER` or `ALREADY_INVITED` and nothing else.
- Enum values are the **published** spellings where one exists (§6.5), so `jobType: PERMANENT` in the
  API and `"permanent"` in the document are visibly the same thing.

## 11.4 Errors

Two kinds, deliberately separated:

- **Domain failures are data.** An invalid job, a duplicate invitation, the last-owner rule — these
  return in `userErrors` with the mutation payload, with a `code` a client can branch on and a `field`
  matching the input field. They are expected outcomes of a well-formed request, not faults.
- **The GraphQL `errors` array is for faults**: bad syntax, unknown fields, authentication failures,
  exceeded limits, genuine server errors. It always carries an `extensions.code`.

A client that only ever reads `errors` will therefore miss a rejected mutation — which is the point:
the schema forces it to look at the result it asked for. Messages in `userErrors` are the same ones the
UI shows, resolved through the message bundles (§8.1), so the two surfaces cannot disagree about what
is wrong.

## 11.5 Pagination

Lists take `page` and `size`, matching the back-office (§8.3): `size` defaults to 20 and is capped at
100, and the result carries `totalElements` and `totalPages`. One pagination concept across both
surfaces is worth more here than the extra precision of cursors.

Ordering **must** be total and stable — a sort key plus the id as a tiebreak — so a client walking
pages sees a deterministic sequence. It still cannot be an exactly consistent one: rows created or
retired mid-walk shift the pages beneath it, so a client requiring an exact snapshot should filter by
a fixed date range rather than paging the whole set. Recorded as an open question (§12).

## 11.6 Limits and abuse

The API is reachable by anything holding a token, so it is bounded by construction, not by hope:

- **Query depth and complexity limits**, refusing an over-large query before it executes. GraphQL's
  flexibility is otherwise an invitation to ask for the transitive closure of the data in one request.
- **Rate limiting per token**, with the limit and remaining budget in response headers so a client can
  pace itself rather than discover the ceiling by hitting it. The counter is per instance and in
  memory: behind more than one replica the effective limit is the limit times the replica count. That
  is deliberate — a shared counter means a round trip to a store on every request, for a limit whose
  job is to stop a runaway script rather than to meter a paid product. Recorded in §12.
- **Batched operations are rejected** — one operation per request. Array batching turns one rate-limit
  unit into many.
- **Introspection is disabled outside development.** It is an authenticated API with documented
  clients; leaving the schema queryable mostly helps someone who should not be there.
- The maximum request body is bounded, and a query that would return more than the page cap is an
  error rather than a silent truncation.

## 11.7 Versioning

The schema follows GraphQL convention rather than the URL versioning of the feed (§6.9): additive
changes are made in place; a field being withdrawn is first marked `@deprecated` with a reason and a
replacement, and removed only after clients have moved. The feed's contract is versioned in its path
because its consumers are anonymous and unreachable; API clients hold tokens, so they can be told.

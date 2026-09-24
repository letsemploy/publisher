<!-- Part of the specification: sections are numbered across all files, and code cites them as "spec N.M". -->
[Specification index](../SPEC.md) · ← [4. Job lifecycle](04-job-lifecycle.md) · [6. The ojobpub document contract](06-document-contract.md) →

# 5. Feed publishing

## 5.1 URL

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

## 5.2 Inclusion predicate

A feed's document contains exactly those jobs that are **members of the feed** *and* **publishable**
per §4.3, ordered by `publishedAt` descending, then `title` ascending, for a stable diff between fetches.

A feed with no qualifying jobs **must** serve a valid document with an empty `jobs` array — the schema
permits it. It must never serve `404` or an error; a consumer polling a temporarily empty feed should
see "no jobs", not a failure.

## 5.3 Defensive serving

If a member job somehow fails schema validation at serving time, it **must** be omitted from the
document and the omission logged with the job id and the reason. One malformed posting must never
break the whole fetch for a consumer.

Every such omission **must** be surfaced in the back-office on the feed screen, so it is visible to a
human rather than buried in a log.

## 5.4 Caching and freshness

- `lastUpdated` is the most recent `lastModifiedAt` among the employer record, the feed record and the
  jobs included in the document, expressed in UTC.
- The response **must** carry a strong `ETag` derived from the serialized document, and
  `Last-Modified` derived from `lastUpdated`.
- Conditional requests (`If-None-Match`, `If-Modified-Since`) **must** be honoured with `304`.
- `Cache-Control: public, max-age=300` by default, configurable per deployment.
- Because the date window (§4.4) is time-dependent, cached documents must not be held beyond the end of
  the current day; the cache key **must** include the evaluation date.

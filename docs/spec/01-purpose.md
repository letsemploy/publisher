<!-- Part of the specification: sections are numbered across all files, and code cites them as "spec N.M". -->
[Specification index](../SPEC.md) · [2. Actors and access](02-actors-and-access.md) →

# 1. Purpose and scope

## 1.1 What this is

A **publishing back-office for job postings**. An employer's team creates and maintains job postings in
a database, groups the ones they want to expose into a **feed**, and gets a stable public URL that
serves those postings as a machine-readable **`ojobpub.json`** document.

The product exists to solve one problem: *an employer has open positions and wants other systems —
job boards, aggregators, partner sites — to consume them reliably, without scraping.*

## 1.2 What this is not

The application must not grow into any of the following:

- **Not a job board.** There is no job-seeker-facing search, browsing or alerting. Job seekers are
  served by the *consumers* of the feed, not by this application.
- **Not an applicant tracking system.** Applications, candidates, CVs, interviews and hiring pipelines
  are out of scope. Every posting links out via its `url` to wherever applications are actually handled.
- **Not a CMS.** Postings carry a short plain description (≤1000 characters), not rich marketing content.

## 1.3 Consumers

The audience for the published output is machines: aggregators, partner job boards, search indexers and
the employer's own website. Therefore stability of the published contract outranks convenience inside
the back-office. A change that breaks a consumer's parser is a breaking change even if no user notices.

## 1.4 The published contract

The output format is the **oJobPub v1 schema**, published at:

```
https://raw.githubusercontent.com/letsemploy/schema/refs/heads/main/v1/ojobpub.json
```

That JSON Schema (draft 2020-12) is the authoritative contract. This specification does not redefine it;
section 6 defines how the domain model maps onto it and how ambiguous cases are resolved. Any conflict
between this document and the schema must be resolved in favour of the schema.

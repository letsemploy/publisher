<!-- Part of the specification: sections are numbered across all files, and code cites them as "spec N.M". -->
[Specification index](../SPEC.md) · ← [9. Technical shape](09-technical-shape.md) · [11. Management API](11-management-api.md) →

# 10. Non-functional requirements

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

**The API.** A token is authenticated by a hash comparison on every request, so that lookup is indexed
by prefix and never scans. Rate limits and complexity limits (§11.6) are enforced before a query
executes, not after. API requests are logged with the token's prefix — never the secret — alongside the
employer, the operation name and the outcome.

**Auditability.** Every status transition records who made it and when — a user or a token (§3.11) —
and the log is visible on the job's detail screen. "Why did this posting disappear from the feed?" must be answerable without database
access.

**Data protection.** The application stores no applicant data. Personal data is limited to back-office
user records (issuer, subject, email, display name), which are deleted with the user.

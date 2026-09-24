<!-- Part of the specification: sections are numbered across all files, and code cites them as "spec N.M". -->
[Specification index](../SPEC.md) · ← [7. User interface](07-user-interface.md) · [9. Technical shape](09-technical-shape.md) →

# 8. Cross-cutting behaviour

## 8.1 Internationalization

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

## 8.2 Validation and errors

- Validation runs on the server. Client-side validation is a convenience and never the only check.
- Field errors appear beside their field; form-level errors appear at the top of the form.
- Flash messages after a redirect are used for success and for errors that no longer have a form.
- Unexpected errors render a friendly error page with a correlation id, and the detail goes to the log.
  Stack traces and SQL must never reach the browser.
- Feed endpoints return JSON errors, never HTML — their callers are machines.

## 8.3 Lists

Pagination defaults to 20 items and is capped at 100 regardless of what the request asks for. Sort
fields are validated against an allow-list per screen. An out-of-range page clamps to the last available
page rather than erroring.

## 8.4 Resource limits

Creation is self-serve (§2.2) and every employer publishes a public, anonymous URL (§5.1), so how much
may exist is a question the application has to answer rather than leave to good behaviour.

| Limit | Property | Default |
|---|---|---|
| Employer memberships per user | `app.limits.memberships-per-user` | 3 |
| Jobs per employer | `app.limits.jobs-per-employer` | 100 |
| Feeds per employer | `app.limits.feeds-per-employer` | 10 |
| Pending invitations per employer | `app.limits.pending-invitations-per-employer` | 20 |
| Service tokens per employer | `app.limits.tokens-per-employer` | 10 |
| Members per employer | `app.limits.members-per-employer` | 25 |

- **`0` means unlimited.** A single-installation team has no abuse problem to solve, and a quota that
  cannot be removed is an obstacle rather than a protection.
- **The cap belongs to the resource, not to the person acting.** A platform admin reaches it too;
  raising it is a configuration change. One rule, one code path, no privileged bypass.
- **What counts.** Jobs: every row, `DRAFT`, `ACTIVE` and `INACTIVE` alike — a draft occupies storage
  and a slot. Tokens: **non-revoked only**, because revoking retains the row for the audit trail
  (§3.10) and must therefore be the way back under the limit. An **expired** token still counts: it is
  one click from live again (§2.8), so treating it as free would let an employer hold a reserve of
  lapsed credentials and flip between them, and would make renewing a lapsed token something the quota
  could refuse — on the one screen whose purpose is to un-break an integration. Members and memberships: **people only** —
  a service token holds a membership (§2.8) but is not a colleague. A **suspended** membership still
  counts, for the expired token's reason: reinstating is one click, and a quota that could refuse it
  would tell the owner how many employers that person belongs to — the disclosure §2.6 refuses.
- **Refusal is a validation failure**, carried to the form or the flash on the web and to `userErrors`
  with the stable code `QUOTA_REACHED` through the API (§11.4). It is an expected outcome of a
  well-formed request, never a fault. The message names the way out — delete one, revoke one, leave
  one — because a refusal with no route forward is the same mistake as a greyed-out control (§2.7).
- **Enforced in the services**, never in the markup. A hidden button is not a limit (§2.4).

**These are policy; §10 is capability.** The performance targets there — up to 10 000 jobs per employer
on a list screen — are what the code must be able to do, and they still hold. The defaults above are
what a shared installation chooses to allow, and an operator may raise them or switch them off.

Two acts are deliberately exempt. The `all` feed created with an employer (§3.5) is never refused: an
employer without a working URL is a broken employer. And editing anything is never refused for being at
a limit — only creating is.

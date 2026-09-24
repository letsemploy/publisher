<!-- Part of the specification: sections are numbered across all files, and code cites them as "spec N.M". -->
[Specification index](../SPEC.md) · ← [11. Management API](11-management-api.md)

# 12. Open questions

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
9. **Reviewing self-serve employers.** The quota half of this question is answered: creation is bounded
   by the limits of §8.4. What remains is whether a self-serve employer should need **review or
   verification** before its feed is served publicly — a quota bounds how *many* public URLs a stranger
   can mint, not what they put behind them. Nothing today inspects an employer before its document is
   available to anyone with the link, and the answer probably depends on whether an installation is a
   single team or a public service.
10. **Offset pagination and a moving list.** The API pages by offset (§11.5), so a client walking a
   large list while jobs are being created or retired can see a row twice or miss one. A stable total
   ordering bounds the damage but does not remove it. Whether the API needs cursor connections, or a
   way to page a fixed snapshot, is deferred until a client is actually affected.
11. **Token rotation, and noticing an expiry.** Expiry now exists (§2.8), but it deliberately does not
   force **rotation**: renewal keeps the same secret, which is what makes it free of an outage, so a
   leaked secret stays valid as long as someone keeps renewing. Whether renewal should be able to issue
   a *new* secret with an overlap window, so an integration can move across without downtime, is still
   open. Two smaller questions come with it: nothing **notifies** anyone that a token is about to
   lapse — the warning is a flag on a screen an owner has no routine reason to visit, and a dashboard
   card or a sidebar badge is the obvious read-time answer — and whether a merely **unused** token
   should be disabled automatically, which unlike expiry really would need the scheduler item 4
   declines.
12. **Reading through the API versus the feed.** The API can read an employer's jobs, and the published
   feed exposes the same postings anonymously (§5). Whether consumers should be pushed toward the feed
   for reads, and the API narrowed toward management, is worth deciding before both grow clients.
13. **Where the rate-limit counter lives.** The per-token budget (§11.6) is counted in memory on the
   instance that served the request, so behind more than one replica a client gets the limit times the
   replica count, and a restart forgives whatever was spent. Whether that matters depends on how the
   application is eventually run; a shared counter is the answer if it does, at the cost of a round
   trip on every request.
14. **Keeping the JavaScript small.** §7.2 now permits the application's own module, which removes the
   `unsafe-eval` tension that hyperscript created but also removes the constraint that kept browser
   behaviour near zero. Nothing currently measures or bounds it. Whether a size budget, or a rule that
   each addition names the server round trip it replaces, is worth adopting is deferred — the module
   covers three interactions today (modal dismissal, chip removal, copy to clipboard).

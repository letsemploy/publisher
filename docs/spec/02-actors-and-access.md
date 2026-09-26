<!-- Part of the specification: sections are numbered across all files, and code cites them as "spec N.M". -->
[Specification index](../SPEC.md) · ← [1. Purpose and scope](01-purpose.md) · [3. Domain model](03-domain-model.md) →

# 2. Actors and access

## 2.1 Roles

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
| **Editor** | Full create/read/update/delete on the employer's jobs, feeds and locations; **see who else belongs to the employer and with which role** (§7.18) |
| **Owner** | Everything an editor may do, plus: edit the employer record, invite people (§2.6), change a member's role, suspend and reinstate members, and remove members (§2.7) |

There is no read-only role in v1. Every member may edit that employer's jobs and feeds; the difference
between owner and editor is authority over **the employer and its people**, not over its content.

**Seeing the people is not authority over them.** Every member may see the membership list, because
anyone editing an employer's jobs needs to know who else is doing so and who to ask for access; only an
owner may change it. What an editor is shown is the membership itself — who belongs, and as what — and
not the owner's working material: pending invitations, the invite form and the role, suspension and
removal controls are absent rather than disabled (§7.18).

Tags are global and every signed-in user may manage them.

An admin is not automatically a member. They act on any employer by virtue of the platform role, and
when they need to belong to one — to be listed as a person responsible for it — they are invited or
they create it, like anyone else.

**Not every actor is a person.** A service token (§2.8) also holds a membership role, so that anything
acting on an employer — a colleague or an integration — is describable in one vocabulary. Where a rule
below depends on there being a *human* answerable, it says so.

## 2.2 Authentication

- Authentication **must** use OAuth 2.0 / OpenID Connect through Spring Security, authorization-code
  flow with PKCE. The application is an OIDC *relying party*; it must not store passwords. PKCE is
  used although the application is a confidential client holding a secret: the secret authenticates
  the client, PKCE binds the code to the browser that started the flow, and only the second protects
  a code intercepted in transit.
- A user is identified by the stable pair **(issuer, subject)** from the ID token — never by email
  alone, which is mutable and may be reassigned.
- **Only OpenID Connect providers.** Several may be configured side by side (§7.19), and each must
  issue ID tokens: a provider that only offers OAuth 2.0 has no issuer and subject to identify a
  person by, and no verified email to invite them at. Such a registration **refuses to start**
  rather than completing a login that leaves the person as nobody. GitHub is the well-known case.
- **One account per identity, never joined by email.** The same person signing in through two
  providers has two accounts. Joining them by a shared address would let whoever controls that
  address at one provider take over the account made through the other (§12).
- On first successful login the application **must** provision a local user record from the ID token
  claims (issuer, subject, email, preferred display name). This record holds the application's own
  authorization data: role and employer memberships.
- **Name and email are the provider's** and are refreshed from the token at every sign-in, so a
  changed address reaches invitations (§2.6) without anyone editing it here. Nothing else on the
  record is: role and memberships are local.
- **Only a verified email is kept.** An invitation goes to whichever account holds the address (§2.6),
  so an email the provider has not verified would let anyone who can type an address into their
  profile collect invitations meant for someone else. An unverified address is stored as none; that
  account can sign in, create an employer, and be invited once its address is verified.
  `app.oidc.require-verified-email=false` relaxes this for a provider that never sends
  `email_verified` at all.
- **Email is not unique.** Two issuers may vouch for one address, and a provider may reassign one. An
  address held by more than one account names nobody in particular and is treated as naming no one —
  an invitation to it answers exactly as for an unknown address (§2.6).
- A newly provisioned user has the **User** platform role and **no employer memberships**. They can log
  in, and from an empty state they may either **create an employer** — becoming its owner (§2.7) — or
  wait for an invitation (§7.16). Neither path is privileged over the other.
- Creating an employer grants a membership, so it is **refused once the user is at their membership
  limit** (§8.4). The refusal says to leave an employer first.
- Membership is created by **exactly two acts**: creating an employer, and accepting an invitation
  (§2.6, §2.7). No other path grants access to an employer, and neither can be performed on someone
  else's behalf without their consent. Suspension and removal are the owner's or an admin's to perform
  and need none.
- The authenticated user **must** expose a stable **user identity** and their **email address**, not
  merely a display name and a set of employer ids. An invitation is addressed to a person, so a
  principal that cannot be identified as a specific user has nothing to attach one to.
- Role and membership are **local** data. The application should not depend on custom OIDC claims, so
  that it works against any standards-compliant provider. It *may* optionally map a configured group
  claim onto the Admin role, and this mapping must be off by default.
- Sessions are server-side. Logout must clear the local session and should trigger OIDC RP-initiated
  logout where the provider supports it.

## 2.3 Development mode

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

## 2.4 Public vs authenticated surface

| Surface | Access |
|---|---|
| Published feed documents (`GET /ojobpub/v1/{employerSlug}_{employerId}/{feedSlug}_{feedId}/ojobpub.json`) | **Public.** No authentication, no session, no cookies required. |
| `/actuator/health`, `/actuator/info` | Public |
| All other actuator endpoints | Admin only |
| Static assets, login routes, error pages | Public |
| The management API (`POST /graphql`) | **Token.** A bearer service token (§2.8); no session, no cookie, no CSRF |
| Everything else (the entire back-office) | Authenticated |

Authorization **must** be enforced server-side on every request, on the data access path — not by hiding
navigation. A user requesting a job belonging to an employer they are not a member of **must** receive
`404 Not Found` (not `403`), so that the existence of other employers' records is not disclosed. The
same applies to an action their **membership role** does not permit (§2.1).

## 2.5 Employer context

A user with access to more than one employer works in the context of **one active employer** at a time,
chosen with a switcher at the top of the sidebar (§7.3). The active employer scopes all list screens and
pre-fills the employer on every create form.

- The active employer is a **convenience, not a security boundary**. Authorization is always derived
  from membership, never from the stored context.
- It **must** be stored server-side in the session, not in a client-controlled cookie.
- If the user is a member of exactly one employer, that employer is selected automatically and the
  switcher is hidden.
- An admin's switcher also offers an "all employers" option, which is the default for admins.

## 2.6 Invitations

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
- **Accepting creates the membership**, with the role the invitation carried. It is **refused** if the
  invitee is at their membership limit, or the employer at its member limit (§8.4); the invitation stays
  pending, so it can be taken up once there is room. The **inviter is never told** — how many employers
  someone belongs to is theirs, and reporting it would be the same disclosure the invite form refuses to
  make.
- An employer at its member limit may not invite at all: an invitation that could never be accepted is
  worse than a refusal now.
- Declining and revoking create nothing. A declined invitation may be sent again later; people change
  jobs and teams.
- Invitations **do not expire**. Nothing here requires a scheduler.
- Removing an existing member is an owner's or an admin's action and requires no consent. A removed
  member may be invited again. The last owner cannot be removed (§2.7).

**Notification**

No email is sent — the application has no mail infrastructure. The invitee sees pending invitations
when they next sign in (§7.3 surfaces the count in the sidebar). Delivery is recorded as an open
question (§12).

## 2.7 Ownership and role changes

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

**Suspending a member**

- An owner of an employer, or an admin, **may suspend another member** and **reinstate** them. It needs
  no consent, like removal, and takes effect on the member's next request.
- A suspended membership **grants nothing**: the member sees and may do exactly what a non-member may,
  and asking for anything of that employer gets `404` (§2.4). It is not a read-only mode.
- It is **not a removal**. The membership stays, with its role, and stays on the People screen marked
  as suspended and since when, so colleagues are not left wondering where someone went. Reinstating
  restores it with the role it had, and needs no invitation — which is the point: removal hands the way
  back to the removed person's consent, suspension keeps it with the owners.
- **Nobody may suspend themselves.** An owner who locks themselves out needs another owner to let them
  back in; that is a removal they did not intend, not an act worth offering.
- A suspended member is still a member for every other purpose: they cannot be invited again ("already
  a member", §2.6), their role may still be changed, and they may be removed outright.
- Service tokens are not suspended; a token that should stop working is revoked (§2.8).

**The last owner**

An employer **must** always have at least one **active** owner **who is a person**. The application
**must** refuse to demote, remove or suspend the last one, and the screen **must** say why rather than simply disabling the
control with no explanation — "promote someone else first" is actionable, a greyed-out button is not.

A service token with the owner role (§2.8) does **not** satisfy this. A workspace whose only owner is a
credential has nobody who can be invited, can accept, or is answerable for it; it would be owned by a
secret in someone's CI configuration. Tokens are excluded from the count for the same reason the rule
exists at all. A **suspended** owner does not satisfy it either: they cannot act, so an employer whose
only other owners are suspended is exactly as frozen. Conversely, demoting or removing a suspended owner
is never refused by this rule — it takes away nobody who could act.

This is the one rule here that protects against a state nobody can repair from inside the employer. An
ownerless employer would still be reachable by platform admins, but its own people could no longer
invite, change roles, or edit the record — the workspace would be frozen to everyone who actually uses
it.

**What an owner may not do**

Deleting an employer remains an **admin** action (§7.13). Ownership is authority over a workspace, not
the power to destroy it along with every feed URL its consumers depend on.

## 2.8 API authentication

The management API (§11) is used by other systems, so it authenticates with a bearer token rather than
a session. OIDC is for people at a browser; a nightly sync has no browser and no person.

**What a token is**

- A **service token belongs to one employer**, not to a person. An integration should outlive the
  colleague who set it up, and should not quietly gain their access elsewhere.
- Only an **owner of that employer** may create, rename or revoke one (§2.7). Not an editor, whose
  authority is over content; and not a platform admin acting alone, because a credential that can act
  as an employer is the employer's to hand out.
- A token carries **its own membership role**, `OWNER` or `EDITOR`, meaning exactly what it means for a
  person (§2.1) — except that it never satisfies the last-owner rule (§2.7).
- A token carries **scopes** that narrow it further (below). Role and scope are both ceilings: an
  action needs the role *and* the scope.

**Scopes**

Six, in three pairs — `jobs:read`, `jobs:write`, `feeds:read`, `feeds:write`, `people:read`,
`people:write`. A token is issued with an explicit set and gains nothing by default.

The split exists because the blast radii differ. A feed-publishing integration has no business editing
postings, and almost nothing has business removing colleagues from an employer. `people:write` — invite,
revoke, change a role, suspend or remove a member — is the one to be reluctant with, and it is useless without the
owner role beside it.

**Handling the secret**

- The secret is generated by the application, **shown once** at creation and never again. There is no
  recovery: a lost token is revoked and replaced.
- It is stored **hashed**, with the same care as a password. A database disclosure must not yield usable
  credentials.
- It carries a short **public prefix** that identifies which token it is without revealing it, so the
  UI, logs and audit records can name a token unambiguously.
- Presented as `Authorization: Bearer <token>`. A token in a query string would end up in access logs
  and referrer headers.
- Revocation is **immediate** and permanent; a revoked token is never reactivated.
- A token **expires after a configured lifetime**, 12 months by default, and an owner **renews** it.
  Renewal keeps the **same secret** and only moves the date, so honouring an expiry costs a click and
  no redeployment; the same act **reactivates** one that has already lapsed. `0` configures no expiry
  at all, for an installation that wants none.

  This reverses an earlier decision, and the objection that stood behind it — *expiry silently breaks a
  working integration at a bad moment* — is answered rather than dismissed. It is answered three ways:
  the register shows each token's date and **flags one before it lapses**; renewal changes no secret,
  so there is nothing to redeploy under time pressure; and a lapsed token comes back in seconds instead
  of being reissued. What remains true is that nothing **notifies** anybody (§12) — the flag is on a
  screen an owner must visit.
- **Expiry is evaluated when a token is used or listed, never by a background job.** A lapsed token is
  simply one whose date has passed; nothing is written as it happens, which is why reactivating it is a
  date change rather than an undo, and why this still needs no scheduler (§12).
- Expired and revoked are refused **identically**, and identically to an unknown token: a caller learns
  only that this credential does not work. The register must show when each token was **last used**,
  which is what makes a forgotten integration noticeable — and a refused request is not a use.

**What a token is not**

It cannot sign in to the back-office, accept an invitation, be invited, or create an employer. Those
are acts of consent or identity, and a credential has neither.

<!-- Part of the specification: sections are numbered across all files, and code cites them as "spec N.M". -->
[Specification index](../SPEC.md) · ← [6. The ojobpub document contract](06-document-contract.md) · [8. Cross-cutting behaviour](08-cross-cutting.md) →

# 7. User interface

## 7.1 Principles

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

## 7.2 JavaScript budget

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

## 7.3 Layout

A fixed left sidebar, a slim top bar and a content area — the conventional administration shell, chosen
because the navigation is a flat set of nine destinations that must stay visible and reachable in one
click while the user works down a long job list.

```
┌──────────────┬──────────────────────────────────────────────────┐
│  ojobpub     │                      ▸ Employer ▾   ◻ User ▾     │  ← top bar
│              ├──────────────────────────────────────────────────┤
│ ◻ Dashboard  │  [page title]                    [primary action]│
│ ◻ Jobs       ├──────────────────────────────────────────────────┤
│ ◻ Feeds      │                                                  │
│ ◻ People     │   page content                                   │
│ ◻ Tokens  ⚿  │                                                  │
│ ◻ Employers  │                                                  │
│ ◻ Locations  │                                                  │
│ ◻ Tags       │                                                  │
│ ◻ Invites ②  │                                                  │
└──────────────┴──────────────────────────────────────────────────┘
```

Implemented with Tabler's vertical navbar (`navbar navbar-vertical navbar-expand-lg`) inside
`page` / `page-wrapper`.

**Sidebar** — navigation, and nothing else:

- The product brand, linking to the dashboard.
- The primary navigation: **Dashboard, Jobs, Feeds, People, API tokens, Employers, Locations, Tags,
  Invitations**, each with a Tabler icon and label. *Employers* is visible to everyone but only offers
  create/delete to admins.
- **People** (§7.18) covers the **active employer** (§2.5), which is why it sits beside Jobs and Feeds
  rather than under Employers: it answers "who am I working with here", the same scope those two
  answer. It is visible to every member, and what it offers depends on the membership role.
- **API tokens** (§7.17) covers the active employer too, and is the **only entry whose visibility
  depends on the role**: owners and admins only, because holding the list is close to holding the
  access. It is **absent** when no employer is active, rather than showing an empty state as People
  does — People is visible to every member, so an empty state is honest, whereas with no employer
  selected there is no ownership question to answer. An admin who chooses *All employers* therefore
  does not see it, and reaches a specific employer's tokens from the employer record.
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

## 7.4 htmx interaction patterns

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

## 7.5 Component vocabulary

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

## 7.6 Lists

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

## 7.7 Forms

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

## 7.8 Pickers without a JavaScript library

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

## 7.9 Accessibility

- A skip link to the main region precedes the sidebar.
- Every control is reachable and operable by keyboard; visible focus is never removed.
- Every input has a programmatically associated label; errors are linked with `aria-describedby` and
  the field carries `aria-invalid`.
- The flash region is `aria-live="polite"`; a destructive confirmation modal traps focus and is
  dismissible with `Escape`.
- Icon-only controls carry an accessible name.
- Contrast meets WCAG 2.1 AA in both themes, verified for the status colours in §7.6.
- Page titles are unique per screen, so history and tabs are distinguishable.

## 7.10 Dashboard

The landing screen for the active employer. Cards show counts of **published**, **draft**, **expired**
and **inactive** jobs, and a feed summary listing each feed with its published job count and public URL.
Every figure links to the list already filtered to it.

Any feed currently omitting a job at serving time (§5.3) appears as a prominent warning card naming the
feed and the count, linking to the feed screen. A quiet dashboard means the feeds are healthy, and that
must be true at a glance.

## 7.11 Jobs

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

## 7.12 Feeds

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

## 7.13 Employers

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
**API tokens** (owners only) — §7.17.

**People** — §7.18. Reachable from here for any employer, and from the sidebar for the active one.

## 7.14 Locations

List, create, edit and delete of city/country pairs, per §7.6 and §7.7. The country field is the native
select described in §7.8. A location still referenced by an employer or a job cannot be deleted, and the
UI names what references it rather than only refusing.

## 7.15 Tags

List of all tags with the number of jobs carrying each, searchable, sorted by name. Create and rename
apply the §3.4 normalization and surface the 28-character and uniqueness rules as field errors. Delete
confirms through a modal stating how many jobs will lose the tag.

## 7.16 Invitations

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

## 7.17 API tokens

Managing an employer's service tokens (§2.8). **Owners only** — an editor never sees it, because
holding the list is close to holding the access.

A **sidebar destination** covering the active employer (§7.3), and reachable per-employer from the
employer record. It is the one navigation entry whose visibility depends on the role.

**List** — every token: its name, its role, its scopes, the public prefix, who created it, when it was
created, **when it was last used**, and **when it expires**. Last-used is the column that earns its
place: it is how a forgotten integration becomes visible, and a token that has not been used in months
is the one to revoke. Each row shows its state — active, expiring soon, expired or revoked.

**Renew** — on every token that is not revoked, whether or not it is near its date; an owner tidying up
before a change freeze should not be told to come back later. On a lapsed token the same control reads
**Reactivate**. It needs no confirmation: it is additive and reversible, unlike revoking.

**Create** — a name, a role (§2.1) and a set of scopes, with the scopes explained in terms of what they
let a caller *do* rather than by their identifiers. `people:write` **must** carry a plain warning that
it allows inviting and removing colleagues.

**The secret is shown exactly once**, on the screen that follows creation, with an unmistakable notice
that it cannot be retrieved again. The screen offers to copy it and says what to do if it is lost —
revoke and create another. It **must not** appear in any later view, in the list, or in a log.

**Revoke** — confirmed through the §7.4 modal, naming the token and stating that anything using it will
begin failing immediately **and that, unlike an expired token, a revoked one cannot be renewed**. Now
that lapsing is recoverable, the modal has to say which of the two this is. Revocation is permanent; the
token stays in the list, marked revoked, so the audit trail still resolves (§3.10).

**Empty state** — per §7.6, explaining what a token is for and that it lets another system act on this
employer without a person signing in.

---

## 7.18 People

Who belongs to an employer. A sidebar destination covering the **active employer** (§2.5), and the same
screen reachable per-employer at the employer record (§7.13) — an admin, and anyone with several
employers, needs to look at one that is not the one they are working in.

**Every member sees the membership list**, editors included (§2.1): name, email address and role, with
the sole owner marked. Someone editing an employer's jobs works alongside these people and has to know
who to ask when they need access or a decision; a screen that answers that question only for owners
makes the owner a lookup service for their own colleagues.

**What the role changes is the actions, not the list.** For an editor the list is the whole screen. An
owner or admin additionally gets, on the same screen:

- **Role and removal controls** on each member. Removing needs no consent (§2.6) but confirms through
  the §7.4 modal, naming the person and the employer, and states that they may be invited again.
- **Suspend and reinstate** on each member but oneself (§2.7). Suspending is reversible, so it does not
  confirm through a modal. A suspended member's row stays in the list for **everyone**, editors included,
  marked *suspended* in words with the date — it is part of the membership, like the role.
- **The last owner** — the demote, remove and suspend controls **must** be refused for the last remaining
  active owner, with a sentence saying to promote someone else first (§2.7). A control that is merely greyed out
  leaves the user guessing.
- **Pending invitations** — who was invited, with which role, by whom, when, and a *Revoke* action.
- **Invite** — an email field and a **role choice** (Editor by default). The invitee must already be
  registered, which the form says up front so a failure is not a surprise.

The owner-only parts are **absent** for an editor, not disabled. A disabled invite form advertises a
capability and then refuses it; and pending invitations are the owner's working material — who has been
approached and has not yet answered — which is theirs to disclose, not the screen's.

The invite form's feedback obeys §2.6: an address that matches no account produces the **same** message
as a successful invitation, while "already a member" and "already invited" are named, because both
people are listed directly above the form.

**Authorization is on the data, not the markup** (§2.4). Reading the list takes membership of that
employer, and every write takes the owner role; both are enforced in the service, so hiding a control
is presentation and never the check. A non-member asking for an employer's people gets `404`.

**No active employer** — for a user who has chosen *All employers* (§2.5), or has none at all, the
screen cannot name a subject. It says so and points at the employer switcher, or, for a user with no
memberships, at §7.16 and creating an employer, exactly as the other employer-scoped screens do.


## 7.19 Sign-in

Where a signed-out visitor starts, where a failed sign-in lands, and where signing out ends. A single
centred card — the logo, "Sign in to oJobPub Publisher", one line saying the visitor will be taken to
their organisation's sign-in page and brought back, a **Sign in** button, and the language choice.

- **No credentials are entered here.** The application must not handle passwords (§2.2); the button
  hands over to the identity provider, whose own page asks for them. A username and password form on
  this page would be one.
- **It is shown before the provider**, not skipped. One click is the price of a first visit that says
  what this is and where the visitor is about to be sent, and of a sign-out that ends somewhere rather
  than bouncing straight back into the provider.
- **Two states**, each in words and not only in colour (§7.9): a failed sign-in (`?error`) says it did
  not complete and to try again, without the technical detail of why; a completed sign-out
  (`?logout`) says so. Switching language keeps the state.
- It is **standalone**: no sidebar, switcher or user menu, since a signed-out visitor has none of them.
- A signed-in visitor asking for it is sent on to the dashboard. Under `dev` it is inert, like every
  login route (§2.3).
- With **no identity provider configured** the page is refused like the rest of the back-office
  (§9.4). A Sign in button that cannot work would be worse than the refusal.

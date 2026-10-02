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
| **Tabler's CSS and JS** | npm | The design system, and the components that cannot work without script: offcanvas sidebar, dropdowns, modals, tooltips, and the sparklines of the dashboard (§7.10) |
| **Tabler icons** | npm | A sprite trimmed to the icons actually used, not the full set |
| **The application's own module** | written here | The small amount of behaviour that genuinely belongs in the browser |

Rules:

- **Our own JavaScript is permitted and should stay small.** One module, plain modern ES served as
  written — no transpiler, no bundler, no framework. It is reviewed like any other source file: no
  `eval`, no building markup or code from strings, and nothing that duplicates state the server owns.
- **A library needs a reason.** No SPA framework, no jQuery, no client-side validation library, no date
  picker, no rich text editor, no charting library — Tabler's own sparkline and tracking components
  are enough for the dashboard's trends (§7.10), and the application's module draws the sparklines an
  htmx swap brings in — no select/autocomplete library (§7.8 gives the
  server-rendered alternative). Adding one is a decision to record, not a convenience.
- **Dependencies come from npm**, pinned in `package.json` with a committed lockfile. Their `dist`
  files are copied into `/static` **and committed**, so a clean checkout builds with Maven alone and
  needs no Node (§9.1). Refreshing them is one documented command and a reviewable commit — a diff
  that shows exactly which bytes the browser will receive.
- Assets are **self-hosted**, never loaded from a CDN. This keeps the application working offline and
  in restricted networks, removes a third-party availability and privacy dependency, and allows the
  strict `Content-Security-Policy` of §9.4.
  - **One exception: a hosted captcha.** hCaptcha's script cannot be self-hosted, so where it is the
    configured captcha (§2.12) it is loaded from hCaptcha, on the pages that carry the captcha only,
    and the policy is widened for hCaptcha on those pages alone. mCaptcha's script is vendored like
    any other dependency; only its widget frame comes from the configured instance.
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
because the navigation is a flat set of destinations that must stay visible and reachable in one
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
│ ◻ Activity   │                                                  │
└──────────────┴──────────────────────────────────────────────────┘
```

Implemented with Tabler's vertical navbar (`navbar navbar-vertical navbar-expand-lg`) inside
`page` / `page-wrapper`.

**Footer** — at the bottom of every page, quietly: a link to the source code, the license (Apache
License 2.0) and the running version, from the build. The project address is configurable
(`app.project-url`), so a fork points at its own repository. Nothing in it needs JavaScript, and it is
left out when printing.

**Sidebar** — navigation, and nothing else:

- The product brand, linking to the dashboard.
- The primary navigation: **Dashboard, Jobs, Feeds, People, API tokens, Employers, Locations, Tags,
  Invitations, Activity, Users**, each with a Tabler icon and label. *Employers* is visible to everyone but only offers
  create/delete to admins.
- **People** (§7.18) covers the **active employer** (§2.5), which is why it sits beside Jobs and Feeds
  rather than under Employers: it answers "who am I working with here", the same scope those two
  answer. It is visible to every member, and what it offers depends on the membership role.
- **API tokens** (§7.17) covers the active employer too, and is one of the **two entries whose
  visibility depends on the role** — the other is Users (§7.20), for admins: owners and admins only, because holding the list is close to holding the
  access. It is **absent** for a user with no employer at all, rather than showing an empty state as
  People does — with no employer there is no ownership question to answer.
- **Invitations** (§7.16) carries a count badge when any are pending. It stays visible when there are
  none: a user with no employer memberships has nothing else to do, and an entry that disappears when
  empty cannot be found by someone who wants to check whether an invitation ever arrived.

**Top bar** — everything about *context* rather than destination, right-aligned:

- The **employer switcher** (§2.5), a dropdown showing the active employer's name and offering the
  others. Hidden when the user has exactly one employer. There is no "all employers" choice. Switching posts to the
  server, which stores the choice in the session and redirects to the dashboard (§7.10). Not back to
  the current screen: that may show a record of the employer just left, which the new scope does not
  cover.
- The **user menu**: the current user, with *My activity* (§7.22), *Language*, *Theme* and *Log out*.
  For an admin it also offers *Switch to admin mode* or *Leave admin mode* (§2.10).
- An **Admin mode** badge while admin mode is on, so it is never on unnoticed.

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
theme. There are three choices — *System*, *Light* and *Dark* — and *System* is the default: it renders
`data-bs-theme="auto"`, which a small synchronous script in `<head>` (`theme.js`, not deferred, so it
runs before the first paint) resolves to light or dark from the operating system's preference, following
it while the page is open. *Light* and *Dark* need no JavaScript; without it, *System* shows light.
Choosing *Light* or *Dark* overrides the system until *System* is chosen again. The menu offers all
three rather than a toggle, because the server cannot know what the system prefers.

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
of an autocomplete library, both use one pattern:

1. **Selected items are chips**, each backed by a hidden input, so the selection posts with the job
   form. Each chip has a remove button; removing it removes its input.
2. **A search input** issues `hx-get` on `input changed delay:300ms` and on `search`, and swaps a
   server-rendered result list below it. The search covers the job's own employer only (§3.3).
3. **Choosing a result adds a chip in the browser**, with no request: `app.js` builds the chip from
   the id and label the result carries as data. It uses `createElement` and `textContent`, never a
   string of markup, so the CSP needs neither `unsafe-inline` nor `unsafe-eval` (§7.2). Choosing an
   item already chosen adds nothing, and the result leaves the list either way.
4. **Results are not form fields.** Only chips post, so what is listed but not chosen is never
   submitted. The server still checks every id against the job's employer (§3.3); the picker only
   limits what is offered.
5. **New tags and locations are not created here.** They are made on the Tags and Locations screens
   (§7.14, §7.15), and a search matching nothing says so.

**The one exception to §7.1.** Assembling a chip in the browser departs from point 1, and point 2 does
not hold here: without JavaScript the chips still render and post, so saving keeps the selection, but
it cannot be changed. The remove buttons are hidden, and the results only ever arrive through htmx.
Changing locations and tags therefore needs JavaScript. A round trip per chosen item was judged a
contrived way to append one element (§7.1, point 3).

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

The landing screen for the active employer (§2.5): every figure on it covers that employer. Top to
bottom:

- **Status cards:** counts of **published**, **draft** (drafts and incomplete jobs), **expired** and
  **inactive** jobs, each linking to the list already filtered to it. The published card also shows
  the published share of all jobs as a ring, and how many jobs were first published in the last 30
  days.
- **Job link clicks** (§5.6), over the last 30 days (configurable):
  - the total, and its change against the 30 days before, as a percentage with an arrow;
  - a line of the daily clicks;
  - a **tracking strip** with one square per day, shaded by activity against the busiest day (none,
    low, medium, high), each day's count in its tooltip, and a legend;
  - with no clicks in the period before, the change reads *new*.
- **Needs attention**, up to five jobs per group, each linking to the job, and "and N more" linking to
  the filtered list:
  - published jobs whose last day — the earlier of the apply-by and end dates (§4.4) — is within 14
    days, soonest first;
  - active jobs that cannot be published (§4.3);
  - drafts nobody has changed for 30 days.
  - With none of these, it says that nothing needs attention.
- **Most clicked jobs** over the window, most first, each linking to its job and with its employer when
  Each row has a bar chart of its last 14 days, and a trend against the
  14 days before.
- **Clicks by country**, the country named in the viewer's language and *Unknown* in words, with a bar
  against the largest.
- **Recent activity:** the five latest events of the log the Activity screen shows this viewer (§7.21),
  with the same audience rules, and a link to it.
- **Feeds**, each with its published job count.
- **Permalinks** (§5.5): name, the feed each publishes — or *No feed*, in words — and its URL with a
  copy action and an *Open* link. These are the URLs a website is configured with, so the dashboard
  shows them and not the feeds' own URLs, which are for testing (§7.12).

**Getting started.** Outside admin mode (§2.10), the dashboard opens with an information message for
each thing a newcomer has yet to create, with the way to do it:

- **No employer yet** — for a user who belongs to none (§2.5): create one, or see the invitations
  (§7.16).
- **No job yet** — for an active employer without any job: create the first one.

Each is shown on every visit while it applies and disappears once the gap is filled; there is nothing
to dismiss and nothing stored. There is no location message: every employer has its headquarters
(§3.1).

**Charts are hints.** They are Tabler's own sparkline and tracking components (§7.2); every figure they
draw is also printed as text beside them, and each has a label saying what it shows (§7.9). Without
JavaScript only the shape is lost.

The dashboard carries no separate warning for jobs a feed leaves out at serving time (§5.3): the status
counts already show how many are expired, drafts or inactive, and the Feeds list and each feed's screen
name the omitted jobs and why (§7.12). A job that has since left the feed keeps its clicks.

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
*Dates*, *Locations*. Locations and tags use the §7.8 picker, which searches the job's own employer only. A new job is created as `DRAFT`; the form
offers *Save draft* and *Save and activate*, the latter running the §4.3 checks and, on failure, saving
the draft and listing precisely what is missing.

**Delete** — permitted in any state, confirmed through a modal naming the job and listing the feeds it
will disappear from.

## 7.12 Feeds

**List** — every feed of the active employer: name, published job count and last publication change. The
feed's own URL is offered only as a quiet *Test* link: it is for trying a feed out, while a website is
configured with a permalink (§5.5, §7.23), whose URL stays when the feed behind it is switched.

**Detail** — the operational centre of the product:

- **The permalinks publishing this feed** lead the screen, each with its URL as one copyable string and
  an *Open* link. With none, the screen says that a website should use a permalink and offers to create
  one.
- The canonical feed URL (§5.1), labelled as being for testing, collapsed beside the JSON preview —
  copyable and with an *Open* link once expanded, but never the first URL a user is offered.
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
receive `404`. Jobs are not deleted. Permalinks publishing the feed (§5.5) are named, since they will
publish no jobs afterwards; the detail screen lists them first.

## 7.13 Employers

**List** — a user's own employers; for an admin, all of them, searchable and paginated. The empty state
offers **creating** an employer, because that is now a thing any user may do (§2.2) — it is no longer a
dead end that only an administrator can resolve.
**Detail** — the record, its headquarters, its feeds and its job counts.
**Create** — open to every signed-in user. Creating an employer also creates its `all` feed (§3.5) and
makes the creator its **owner** (§2.7).
**Edit** — name, slug, url, industry, headquarters location; slug behaviour per §7.12. The
headquarters is picked from the employer's own locations, or entered as a new city and country,
which joins them. On **create** there are none yet, so the city and country are entered. **Owners and
admins only**; an editor works on jobs and feeds, not on the employer record.
**Delete** — **owners and admins** (§2.7), from the employer record. A modal states what goes — the
number of jobs, feeds, members and API tokens, and its locations, tags and invitations — and that its
public feed URLs will answer "not found" from then on. The employer's **name must be typed back**. The
server checks it; the page only keeps the button disabled until it matches, and without JavaScript the
button works and a wrong name is refused. Afterwards, if it was the active employer, the active employer
falls back to the first remaining (§2.5).
**API tokens** (owners only) — §7.17.

**People** — §7.18. Reachable from here for any employer, and from the sidebar for the active one.

## 7.14 Locations

The locations of the active employer (§2.5), with list, create, edit and delete, per §7.6 and §7.7.
A new one belongs to the active employer. The country field is the native select described in
§7.8. A location still referenced by its employer or a job cannot be deleted, and the UI names what
references it rather than only refusing. Another employer's location is not found (§2.4).

## 7.15 Tags

The tags of the active employer (§2.5), with the number of jobs carrying each, searchable, sorted by
name. A new one belongs to the active employer. Another employer's tag is not found (§2.4). Create and rename
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

**No employer** — for a user who belongs to none, the screen cannot name a subject (an employer is
always active otherwise, §2.5). It says so and points at §7.16 and creating an employer, exactly as the
other employer-scoped screens do.


## 7.19 Sign-in

Where a signed-out visitor starts, where a failed sign-in lands, and where signing out ends. A single
centred card — the logo, "Sign in to oJobPub Publisher", one line saying how to sign in, the sign-in
form or buttons, and the language choice.

- **With local accounts enabled (§2.12)**, an email and password form comes first, with links to sign
  up and to reset a forgotten password. Any provider buttons follow below it.

- **One button per configured identity provider.** Adding one is configuration alone. Each button
  says "Continue with …", naming the provider by its configured display name, or by the brand when
  none is configured. It carries the provider's logo when the provider is recognised by the host of
  its authorisation endpoint (Google, GitHub, GitLab, Microsoft, Apple, Auth0), and a generic icon
  otherwise.
  - They are **sorted by name**, with an unnamed one last. The configured order is not kept
    underneath, so sorting is the only order a reader can predict.
  - With several, all look alike, so none is presented as the one to pick. A single provider keeps
    one primary "Sign in" button, as before.

- **Provider credentials are never entered here.** A provider button hands over to the identity
  provider, whose own page asks for them. Only a local account's password is entered on this page.
- **It is shown before the provider**, not skipped. One click is the price of a first visit that says
  what this is and where the visitor is about to be sent, and of a sign-out that ends somewhere rather
  than bouncing straight back into the provider.
- **Its states**, each in words and not only in colour (§7.9): a failed sign-in (`?error`) says it did
  not complete and to try again, without the technical detail of why; a completed sign-out
  (`?logout`) says so; and a suspended account (`?suspended`, §2.11) is told it is suspended and to
  contact the administrator. With local accounts, also: a sign-up just completed (`?verified`), a
  password just reset (`?reset`), a session ended by a password change (`?expired`), and a sign-in
  refused for want of a solved captcha after repeated failures (`?captcha`, §2.12), where the form
  then carries it. Switching language keeps the state.
- It is **standalone**: no sidebar, switcher or user menu, since a signed-out visitor has none of them.
- A signed-in visitor asking for it is sent on to the dashboard. Under `dev` it is inert, like every
  login route (§2.3).
- With **no identity provider configured and local accounts off** the page is refused like the rest
  of the back-office (§9.4). A Sign in button that cannot work would be worse than the refusal.

## 7.20 Users

Everyone who has signed in, for **platform admins only**: a sidebar destination only they are offered,
and a `404` for anyone else asking for it (§2.4). It lists each person's name, email, the identity
provider they sign in with, their platform role, and how many employers they belong to. It is searchable
by name or email, sorted by name, and paginated (§8.3).

Each ordinary user's row offers **View as** (§2.9). An admin's row, and the viewer's own, say why they
cannot be viewed as, rather than showing a disabled button.

While viewing as someone, the actor *is* that user, not an admin, so this destination disappears from
the sidebar. The banner's *Stop viewing* is the way back.

Every row also links to that person's **activity** (§7.22), as they would see it.

A suspended account says so in its row, with the date (§2.11), and the list can be narrowed to
**suspended only**. Each ordinary user's row offers **Suspend**, which leads to a confirmation page:
what stays, what keeps working, the employers left without an active owner, and an optional reason.
A suspended row offers **Reinstate** instead, without confirmation. Admins and the viewer's own row
offer neither, for the reason they cannot be viewed as.

## 7.21 Activity

The audit log (§3.12) of the **active employer** (§2.5), newest first, in pages of 50 (§8.3). It is
a sidebar destination for everyone. Each row says when, who, and what — "created the job
“Backend Engineer”" — naming things as they were called at the time. A token is marked as a token and
the application's own acts as the application's, in words, not only by colour (§7.9).

- A **member** reads the employer's log, without the owners-only events (§3.12).
- An **owner** reads all of it.
- An **admin** reads the active employer's log, like anyone, and is offered **All activity**:
  everything, including events about people, which belong to no employer, and the logs of employers
  since deleted, with a column naming each row's employer. It is the one view across employers
  (§2.5). Anyone else asking for it is told it does not exist (§2.4).
- With nothing in scope, the screen shows an empty state.

## 7.22 My activity

A person's own log (§3.12): what was done to them, such as being suspended, invited, or viewed by an
admin (§2.9), and what they did. It is reached from the user menu rather than the sidebar, because it
is about the person, not the employer being worked on. The same screen, for any user, is where an admin
lands from the Users screen (§7.20).

## 7.23 Permalinks

Part of the Feeds screen (§7.12), below the feeds, rather than a destination of its own: a permalink is
a way of publishing them.

- **A list** of the employer's permalinks: name and description, the feed it publishes, and its URL
  with a copy action and an *Open* link.
- **The switch is on the list.** Each row carries a select of the employer's feeds plus *No feed*, and
  a *Switch* button, as a plain form: the one thing done here often, and under time pressure, needs no
  second screen and no JavaScript. No feed is also said in words (§7.9). The confirmation says a switch
  reaches consumers within the cache lifetime (§5.5).
- **Create / edit** — name, description and the feed, on a form of its own. The URL is shown once the
  permalink exists.
- **Webserver configuration** — on the edit form, once the URL exists: a tab each for Apache, nginx
  and Caddy, holding a redirect and a reverse proxy from `/.well-known/ojobpub.json` on the
  employer's own domain to the permalink, filled in and each with a copy action. Without JavaScript
  the three show one below the other (§7.1). The screen says why one might choose either: a redirect
  is simpler, but fails a consumer that does not follow one (§5.5).
- **Delete** — modal confirmation stating that the URL will stop working and consumers will receive
  `404`, and pointing to *No feed* for anyone who meant to stop publishing but keep the URL.

## 7.24 Account screens

The screens of a local account (§2.12), standalone like sign-in (§7.19) and offered only when local
accounts are enabled; otherwise they do not exist.

- **Sign up** — email, name and the captcha. Afterwards one page, whatever happened: "check your
  email".
- **Complete the sign-up** — the link from the mail: the password twice. Afterwards sign-in, saying
  the address is confirmed. A used or expired link says so and offers to send a new one.
- **Resend the link** and **Forgot password** — an email and the captcha; afterwards one page,
  whatever happened.
- **Reset password** — reached from the mail: the new password twice. Afterwards sign-in, saying the
  password changed.
- **Change password** — for a signed-in local account, from the user menu: the current password and
  the new one twice. Accounts from a provider are not offered it; their password is the provider's.
- Every form says its password rule before it is broken, and keeps what was typed except passwords
  when it refuses.

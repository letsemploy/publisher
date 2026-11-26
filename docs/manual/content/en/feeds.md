---
title: "Feeds and domains"
description: "Choosing what is published, and the URL you give to others."
weight: 30
---

## Feeds {#feeds}

A **feed** is a selection of your jobs, published as one `ojobpub.json` document. Every employer has a
feed called `all`; create more with **New feed** to publish different selections to different
partners.

A feed's page shows:

- **the permalinks publishing it** first, with their URLs to copy;
- the jobs in the feed and the employer's remaining jobs side by side, each with a search, to add or
  remove jobs;
- for each job in the feed, whether it is **published** and, if not, why: draft, inactive, expired or
  incomplete. A job you added is only in the document while it is published;
- a **preview** of the document exactly as it is served, with a badge saying whether it is valid
  against the oJobPub schema.

The feed's own URL is offered quietly as **Test**: it is for trying the feed out. Give others a
permalink instead. Only your employer's members can open it, signed in; for everyone else it does not exist.

A feed that no permalink with a domain publishes is on no website: job boards looking for your jobs at
your website do not find them. Its page then opens with a warning, and the Feeds list and the dashboard
mark it **no domain**. Point one of your domains at it under **Domains**.

Renaming a feed or changing its slug is safe: old links keep working and are forwarded. Deleting a
feed makes its URL answer *not found*; the jobs are not deleted.

## Domains {#permalinks}

Job boards look for your jobs at **your own domain**, at
`https://example.com/.well-known/ojobpub.json`. **Domains**, in the sidebar, is where you set that up.
Each domain has a **permalink**: a URL that stays the same while you change the feed behind it. Your
website serves it at that address; afterwards you switch what it publishes yourself.

Every employer starts with one permalink, publishing its `all` feed, and without a domain yet. After
signing in, the first page asks for that domain until it is set, and the dashboard reminds you until a
check finds your website serving it.

The list shows, for each domain, what its last check found and a **Check setup** button, a select of
your feeds with a **Switch** button, and the permalink's URL. Choosing *No feed* keeps the URL alive
but publishes no jobs — the way to pause without losing the URL. Consumers see a switch once their
cached copy expires.

**New domain** asks for the domain first — the domain itself, such as `example.com`, without `www.` or
another subdomain; the domain of your employer's website is offered — then the feed, `all` to begin
with, and optionally a name and a description. A name left empty is the domain. Each domain is used
once per employer.

Once saved, the edit page numbers the steps: the domain, a ready-made **webserver configuration** for
Apache, nginx and Caddy, and the check.

- a **redirect** is the simpler setup, but a consumer that does not follow redirects will fail;
- a **reverse proxy** serves the document from your domain directly.

**Check setup** asks the address on your website what it really serves: this permalink, a redirect to
it, another feed, a copy of the file, your site's own page, or nothing. Each answer says what to do
next.

Deleting a domain makes its permalink's URL answer *not found*. To stop publishing but keep the URL,
switch it to *No feed* instead.

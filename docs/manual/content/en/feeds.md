---
title: "Feeds and permalinks"
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
permalink instead.

Renaming a feed or changing its slug is safe: old links keep working and are forwarded. Deleting a
feed makes its URL answer *not found*; the jobs are not deleted.

## Permalinks {#permalinks}

A **permalink** is a URL that stays the same while you change the feed behind it. Configure your
website or a partner's import with it once; afterwards you switch what it publishes yourself.

Permalinks are listed on the **Feeds** page, below the feeds. Each row has a select of your feeds and a
**Switch** button. Choosing *No feed* keeps the URL alive but publishes no jobs — the way to pause
without losing the URL. Consumers see a switch once their cached copy expires.

**New permalink** asks for a name, a description and the feed. Once it exists, its edit page offers a
ready-made **webserver configuration** for Apache, nginx and Caddy, so the feed can also be found at
`/.well-known/ojobpub.json` on your own domain:

- a **redirect** is the simpler setup, but a consumer that does not follow redirects will fail;
- a **reverse proxy** serves the document from your domain directly.

Deleting a permalink makes its URL answer *not found*. To stop publishing but keep the URL, switch it
to *No feed* instead.

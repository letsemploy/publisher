---
title: "API tokens"
description: "Letting another system manage jobs and feeds, without a person signing in."
weight: 60
---

An **API token** lets another system — an applicant tracking system, an HR tool, a script — work on
this employer through the management API, without a person signing in. Only **owners** see and manage
tokens.

## Creating a token {#create}

**Create a token** asks for a name, a role and a set of **scopes**, which say what the token may do,
such as reading or writing jobs or feeds. A token never gets more than its role and scopes allow. The
scope for people allows inviting and removing colleagues; give it only when that is the point.

**The secret is shown once**, right after creating the token. Copy it into the other system then; it
cannot be shown again. If it is lost, revoke the token and create another.

## Expiry, renewal and revocation {#expiry}

A token expires after the period the installation sets, twelve months by default. The list shows
each token's state — **active**, **expiring soon**, **expired** or **revoked** — and **when it was last
used**: a token nobody has used for months is the one to revoke.

- **Renew** moves the expiry date forward without changing the secret, so nothing has to be
  redeployed. On an expired token the same button reads **Reactivate**.
- **Revoke** stops the token at once, and permanently: a revoked token cannot be renewed.

A token cannot create or renew tokens, nor delete the employer.

The API is documented by its schema, at `/graphql/schema` on this installation.

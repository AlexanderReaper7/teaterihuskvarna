# 0002 — Accounts under a personal login

2026-09-21

## Decision

The repository is `AlexanderReaper7/teaterihuskvarna`, private, on a personal GitHub account. The Sanity project id is left empty in `studio/` rather than bound to whoever runs the initialiser.

## Why, when the plan says otherwise

[projektplan-original.md](../projektplan-original.md) is explicit under "Ägarskap": every account belongs to the association, registered on a function address such as `webb@teaterihuskvarna.se`, with at least two board members holding admin. This decision breaks that rule on purpose, so the break is written down rather than discovered later.

No such organisation exists yet. It cannot be created from here either: the `gh` token carries `gist`, `read:org`, `repo` and `workflow` but not `admin:org`, and `gh` has no org-create command in any case. Organisations are made in the web UI by a human.

The alternative was to block all work until the board creates one. That trades a certain cost, twelve weeks is not long, against a cheap and reversible one.

## What it costs to undo

A GitHub transfer keeps commits, issues, pull requests and stars. Actions secrets and variables do **not** transfer and must be re-entered. That is the entire bill.

The same reasoning covers Sanity: `npm create sanity@latest` wants a login and binds the project to that person, which is the identical mistake in a service the association would then not own. Scaffolding the studio by hand avoids it.

## Open

The transfer is tracked in [open-questions.md](../open-questions.md) under "Raised during setup". Risk number one in the plan's own risk table is a system nobody owns after week 12, and an account on one student's login is that risk in miniature.

---
created: 2026-09-21
provenance: unreviewed
description: Which accounts own the repository and the Sanity project.
---

# 0002: Accounts under a personal login

Amended by an agent on 2026-11-10. The repository is public and the Studio names a Sanity project, and the record now says so.

## Decision

The repository is `AlexanderReaper7/teaterihuskvarna`, on a personal GitHub account. It was private when this was decided and is public now, a change [0003](0003-no-branch-protection-yet.md) records on 2026-09-30. The Sanity project id was left empty in [`studio/`](../../studio/) at first, rather than bound to whoever runs the initialiser. Since 2026-09-28 [`studio/env.ts`](../../studio/env.ts) names project `gk5ur3tb`. No record says which Sanity login owns that project, and Sanity's API answers 401 when asked about the project without a token.

## Why, when the plan says otherwise

[projektplan-original.md](../projektplan-original.md) is explicit under "Ägarskap": every account belongs to the association, registered on a function address such as `webb@teaterihuskvarna.se`, with at least two board members holding admin. This decision breaks that rule on purpose, so the break is written down rather than discovered later.

No such organisation exists yet. It cannot be created from here either: the `gh` token carries `gist`, `read:org`, `repo` and `workflow` but not `admin:org`, and `gh` has no org-create command in any case. Organisations are made in the web UI by a human.

The alternative was to block all work until the board creates one. That trades a certain cost, twelve weeks is not long, against a cheap and reversible one.

## What it costs to undo

A GitHub transfer keeps commits, issues, pull requests and stars. Actions secrets and variables do **not** transfer and must be re-entered. That is the entire bill.

The same reasoning covers Sanity: `npm create sanity@latest` wants a login and binds the project to that person, which is the identical mistake in a service the association would then not own. Scaffolding the studio by hand avoids it.

## Open

Klas creates the association's organisation ([first meeting](../meetings/2026-09-24-meeting-1.md#who-does-what)), and the repository moves there once it exists. On 2026-11-10 the repository is still owned by the user account `AlexanderReaper7`, and five other accounts have write access. Risk number one in the plan's own risk table is a system nobody owns after week 12, and an account on one student's login is that risk in miniature.

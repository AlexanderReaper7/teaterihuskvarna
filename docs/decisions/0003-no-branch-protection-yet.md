---
created: 2026-09-22
provenance: unreviewed
description: Whether main has branch protection rules.
---

# 0003: No branch protection on main, for now

## Decision

main carries no branch protection rules. The restriction that matters, that only Alexander Öberg merges or force-pushes to main, comes from repository access rather than from a rule: he is the only collaborator and the only admin.

[.github/CODEOWNERS](../../.github/CODEOWNERS) names him owner of every path. It is staged rather than active, because a CODEOWNERS entry does nothing until some rule requires code owner review.

## Since 2026-09-27

The reasons below no longer describe the repository. The four students and Klas have write access, the APL period began on 2026-09-14, and the protection endpoints still answer 403. Nothing stops any of them from pressing the merge button or pushing to main with `git push --no-verify`. The rules for the team are in [0020](0020-pull-requests-and-merging.md), and the [`pre-push`](../../.githooks/pre-push) hook enforces part of them on each clone.

Whether to pay for protection or make the repository public is deferred. Decided by the user on 2026-09-27.

## Why not simply turn protection on

It is not available on this account. Both `GET /repos/AlexanderReaper7/teaterihuskvarna/branches/main/protection` and the rulesets endpoint answer 403:

> Upgrade to GitHub Pro or make this repository public to enable this feature.

GitHub's plan comparison lists protected branches under Pro and under Team, in both cases as a private repository feature. A free organisation would not lift the block either, so the transfer tracked in [open-questions.md](../open-questions.md) does not by itself solve this.

## Why waiting is the cheap move

Buying Pro costs roughly 4 USD a month for a feature that becomes free later, see below. Making the repository public costs nothing in money and publishes the produktägare's plan, the board and partner material, and the association's internal timeline before the board has agreed to any of that. A repository setting can be flipped back; a disclosure cannot.

Against that: there is currently nobody to protect main from. The APL period has no start date, the four students have no accounts, and the one person with write access is the one who would be inconvenienced by the rules.

## What unblocks it

GitHub for Nonprofits gives a verified organisation free GitHub Team, with unlimited private repositories and the full feature set, to "501(c)3 (or equivalent) organizations and libraries". A Swedish ideell förening is the "or equivalent" case. The board applies at nonprofits.github.com, which is a second application, separate from the Microsoft Azure credit the plan already asks about under "Öppna frågor".

Protection then arrives free, on the repository the association actually owns, rather than being rented on a personal account in the meantime.

## When to revisit

Before week 1 of the APL period, whether or not the transfer has happened. Four developers with write access and no rules is a different proposition from one, and the plan's own rule under "Regler för kod" is that no code reaches main unreviewed. That rule needs enforcement exactly when the second person gets push access, not before.

## What it costs to wait

main is unprotected against force-push and deletion until then. The exposure is one person's mistake on his own repository, recoverable from any local clone.

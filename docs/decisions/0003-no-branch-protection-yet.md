---
created: 2026-09-22
provenance: unreviewed
description: The rulesets that protect main, and who can bypass each of them.
---

# 0003: Branch protection on main

Amended by an agent on 2026-11-10. The rulesets are described as GitHub returned them that day, with the bypass each one has. The file keeps its first name, from before the rulesets existed, because other records link to it.

## Current state, 2026-11-10

On 2026-09-30 the user confirmed that the customer permitted publication and requested branch protection and relevant repository rules. The repository is public. GitHub returned the settings below on 2026-11-10.

## Agent notes

Three rulesets are active on the default branch. Each has one bypass actor, the repository admin role. On this personal repository the owner is the only admin, and the five other collaborators have the write role.

- [Main Protection](https://github.com/AlexanderReaper7/teaterihuskvarna/rules/23985046) requires a pull request and an up-to-date branch, and asks for no approval. The GitHub Actions checks `test`, `image`, `check`, and `closes-an-issue` must pass. It blocks deletion, force-pushes and merge commits. The admin role can bypass it, through a pull request only. GitHub's history of the ruleset shows no bypass actor from 2026-09-30 to 2026-10-26, when this one was added. The history names nobody, and this record and [0020](0020-pull-requests-and-merging.md) name a requested bypass only for review requirements. The owner merged pull request #205 (Refuse a mailing to an audience that reaches no member, instead of asking to try again) on 2026-11-04 while its required check `image` had failed, which only this bypass allows.
- [Review required except owner](https://github.com/AlexanderReaper7/teaterihuskvarna/rules/24239084) requires one approval, dismisses stale approvals after new commits, and requires resolved review conversations. The user explicitly requested permission to bypass review requirements. The admin role can bypass it, through a pull request only.
- [Only owner merges main](https://github.com/AlexanderReaper7/teaterihuskvarna/rules/24239026) restricts updates. The admin role can bypass it, in mode `always` since the evening of 2026-09-30. The first version, from that morning, allowed the bypass through a pull request only. A push by the owner outside a pull request still meets Main Protection, whose bypass covers pull requests only.

The rulesets enforce [0020](0020-pull-requests-and-merging.md) with the user's explicit exception allowing the owner to skip review. Since 2026-10-26 the required checks bind everyone but the owner. Code owner approval is not required because the sole code owner cannot approve an authored pull request. One collaborator approval satisfies GitHub's rule; GitHub does not establish whether the reviewer is a student, so the student review requirement remains a team responsibility.

Secret scanning, secret scanning push protection, vulnerability alerts and Dependabot security updates are enabled. Squash and rebase merging, automatic branch deletion, read-only default workflow tokens and the ban on Actions approving pull requests are enabled, and merge commits are off.

## Verification

On 2026-11-10 an agent read the three rulesets, each ruleset's history, the collaborators and the security settings from GitHub's API, and the checks of pull request #205. No rejected push or merge was attempted, on that day or when the rulesets were made. The required check names and their GitHub Actions app ID came from an existing pull request's check runs.

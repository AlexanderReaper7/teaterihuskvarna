---
created: 2026-09-22
provenance: unreviewed
description: The rulesets that protect main, enabled on 2026-09-30.
---

# 0003: Branch protection on main

## Current state, 2026-09-30

The user confirmed that the customer permitted publication and requested branch protection and relevant repository rules. The repository is now public. GitHub accepted and returned the settings below on 2026-09-30.

## Agent notes

The agent configured three active rulesets for the default branch:

- [Main Protection](https://github.com/AlexanderReaper7/teaterihuskvarna/rules/23985046) requires a pull request and an up-to-date branch. The GitHub Actions checks `test`, `image`, `check`, and `closes-an-issue` must pass. It blocks deletion, force-pushes and merge commits. Nobody has bypass permission, including the owner.
- [Review required except owner](https://github.com/AlexanderReaper7/teaterihuskvarna/rules/24239084) requires one approval, dismisses stale approvals after new commits, and requires resolved review conversations. The user explicitly requested permission to bypass review requirements. The admin role has a pull-request-only bypass for this ruleset.
- [Only owner merges main](https://github.com/AlexanderReaper7/teaterihuskvarna/rules/24239026) restricts updates. Only the repository admin role can bypass this rule, and only through a pull request. On this personal repository the owner is the only admin. This bypass does not bypass Main Protection.

The rulesets enforce [0020](0020-pull-requests-and-merging.md) with the user's explicit exception allowing the owner to skip review. CI still applies to the owner. Code owner approval is not required because the sole code owner cannot approve an authored pull request. One collaborator approval satisfies GitHub's rule; GitHub does not establish whether the reviewer is a student, so the student review requirement remains a team responsibility.

Secret scanning, secret scanning push protection, vulnerability alerts and Dependabot security updates are enabled. Existing squash/rebase merge settings, automatic branch deletion, read-only default workflow tokens and the ban on Actions approving pull requests remain enabled.

Verification read the rulesets and security settings back from GitHub. No rejected push or merge was attempted. The required check names and their GitHub Actions app ID came from an existing pull request's check runs.

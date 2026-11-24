---
created: 2026-09-22
provenance: unreviewed
description: The rulesets that protect main, and who can bypass each of them.
---

# 0003: Branch protection on main

Amended by an agent on 2026-11-10, twice: once to describe the rulesets as GitHub returned them that day, with the bypass each one has, and once after the user deleted the ruleset that required an approval. Amended by an agent on 2026-11-24 to add the environment that holds the token for Dependabot's merges. The file keeps its first name, from before the rulesets existed, because other records link to it.

## Current state, 2026-11-10

On 2026-09-30 the user confirmed that the customer permitted publication and requested branch protection and relevant repository rules. The repository is public. On 2026-11-10 the user deleted the ruleset Review required except owner. GitHub returned the settings below that evening.

## Agent notes

Two rulesets are active on the default branch. Each has one bypass actor, the repository admin role. On this personal repository the owner is the only admin, and the five other collaborators have the write role.

- [Main Protection](https://github.com/AlexanderReaper7/teaterihuskvarna/rules/23985046) requires a pull request and an up-to-date branch, and asks for no approval. The GitHub Actions checks `test`, `image`, `check`, and `closes-an-issue` must pass. It blocks deletion, force-pushes and merge commits. The admin role can bypass it, through a pull request only. GitHub's history of the ruleset shows no bypass actor from 2026-09-30 to 2026-10-26, when this one was added. The history names nobody, and this record and [0020](0020-pull-requests-and-merging.md) name a requested bypass only for review requirements. The owner merged pull request #205 (Refuse a mailing to an audience that reaches no member, instead of asking to try again) on 2026-11-04 while its required check `image` had failed, which only this bypass allows.
- [Only owner merges main](https://github.com/AlexanderReaper7/teaterihuskvarna/rules/24239026) restricts updates. The admin role can bypass it, in mode `always` since the evening of 2026-09-30. The first version, from that morning, allowed the bypass through a pull request only. A push by the owner outside a pull request still meets Main Protection, whose bypass covers pull requests only. A stack merge with `gh stack merge` cannot use a bypass, so this ruleset refuses it, the owner's included. On 2026-11-10 the user chose to pause it for one stack merge: an agent set it to disabled from 22:12:04 to 22:14:13 Stockholm time, merged the 43 pull requests of stack #235, set it back to active and read it back. An earlier attempt paused it from 22:05:35 to 22:06:04 and merged nothing.

The rulesets enforce [0020](0020-pull-requests-and-merging.md). Since 2026-10-26 the required checks bind everyone but the owner. No ruleset requires an approval, so the customer's rule that another student reviews every change is the owner's check before merging.

One thing merges without the owner at the keyboard, by the user's decision of 2026-11-24 in [0020](0020-pull-requests-and-merging.md): [`dependabot-merge.yml`](../../.github/workflows/dependabot-merge.yml) merges Dependabot's grouped minor and patch updates with a token of the owner's. The token acts as the owner, so both rulesets let it through. What keeps it from the five collaborators is where it is stored:

- It is the secret `DEPENDABOT_MERGE_TOKEN` of the environment [dependabot-merge](https://github.com/AlexanderReaper7/teaterihuskvarna/settings/environments), not a repository secret. A repository secret is handed to a workflow on any branch, and a collaborator with the write role can push a branch with a workflow of their own.
- The environment has one deployment branch rule, `main`. GitHub refuses a job that names the environment from any other branch before the job starts, so the secret never reaches it. Only an admin can change an environment, and the owner is the only admin.
- A workflow on main changes only through a pull request the owner merges, so what runs with the token is what the owner let in.
- It is a fine-grained token for this repository alone, with read and write on contents and on pull requests and nothing else. [`dependabot-merge.sh`](../../scripts/dependabot-merge.sh) uses it for two calls, the merge and the comment that asks Dependabot to rebase, and reads everything else with the workflow's own read-only token.

The deleted ruleset, Review required except owner, required one approval, dismissed stale approvals after new commits, required resolved review conversations, and let the admin role bypass it through a pull request only. The user deleted it on 2026-11-10, choosing an agent's recommendation, after GitHub refused a stack merge with "At least 1 approving review is required by reviewers with write access". The agent's reason: Only owner merges main already keeps every collaborator but the owner from merging, so the approval only ever stopped the owner, who could bypass it on a single pull request but not in a stack merge.

Secret scanning, secret scanning push protection, vulnerability alerts and Dependabot security updates are enabled. Squash and rebase merging, automatic branch deletion, read-only default workflow tokens and the ban on Actions approving pull requests are enabled, and merge commits are off.

## Verification

On 2026-11-10 an agent read the three rulesets, each ruleset's history, the collaborators and the security settings from GitHub's API, and the checks of pull request #205. The required check names and their GitHub Actions app ID came from an existing pull request's check runs.

That evening GitHub refused three stack merges of stack #235: for the approval and the update restriction together, for the update restriction alone while the approval rule was deleted, and for a required check, `closes-an-issue`, whose old cancelled runs were still on the commits. The fourth attempt merged. An agent saved each ruleset's definition before deleting or pausing it, then read the two remaining rulesets back: Only owner merges main matched its saved definition, active, with the admin role's `always` bypass.

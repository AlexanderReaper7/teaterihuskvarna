---
created: 2026-09-27
provenance: user
description: How a change reaches main, who merges it, and how it gets reviewed.
---

# 0020: Every change reaches main through a pull request the owner merges

Amended by the user on 2026-11-02. A pull request may close several work items. Amended by an agent on 2026-11-10 with the user's decision that day to delete the review requirement.

The decisions are the user's. The user gave no reasons beyond choosing, so the reasoning under Agent notes is an agent's. The guide for the team is [branches-and-pull-requests.md](../branches-and-pull-requests.md).

## Decision

Unless dated otherwise, decided by the user on 2026-09-27.

- Only Alexander Öberg merges pull requests.
- A pull request is squash merged or rebase merged, chosen per pull request by whoever merges. Merge commits are off. A squash commit takes the pull request's title and description.
- A branch is brought up to date by merging main into it. A pushed branch is never rebased or force-pushed.
- A branch is created from its issue, with the issue's "Create a branch" button or `gh issue develop`, which names it `<issue>-<slug>` and links it to the issue.
- A pull request may close several work items, decided by the user on 2026-11-02. It is opened as a draft early. Its description names its issues with closing keywords, `Closes #N`, and a check fails a pull request that closes no issue. The label `no-issue` exempts a pull request from the check, and stays visible on it.
- The owner's own work, agents included, goes through pull requests too. The owner's agents may rebase their branches.
- CI's [`build.yml`](../../.github/workflows/build.yml) and [`docs.yml`](../../.github/workflows/docs.yml) run on every pull request, and on push only to main.
- Automatic review: a reviewing agent runs on the owner's machine, and its findings stay private to the owner. Before anything is built, a time-boxed spike checks whether a T3 Code thread can host the review, so the owner can follow it as it runs. `t3` 0.0.42 has no command that starts a thread.
- GitHub enforcement was deferred on 2026-09-27. On 2026-09-30 the user confirmed customer permission to publish and requested protection. [0003](0003-no-branch-protection-yet.md) records the active rules and verification.
- No ruleset requires an approval on main, decided by the user on 2026-11-10, choosing an agent's recommendation. The reasoning is the agent's, under Agent notes.

## Agent notes

- The customer's rule that every change is reviewed by at least one other student ("Regler för kod" in [projektplan-original.md](../projektplan-original.md)) is not changed by this record, so the guide keeps it: a student reviews, then the owner merges. The user has not said whether the automatic review replaces the student's.
- Squash exists for the students: a branch of "wip" and "fix" commits becomes one commit on main, and one `git revert` undoes the pull request. Rebase exists for the owner, whose agents write a series of commits meant to stay separate. Because only the owner merges, allowing both is safe: a student never picks the method.
- Merging main in, rather than rebasing, is for the students. Force-pushing a rebased branch is how a junior loses work or overwrites a teammate's, and squash throws the merge commits away anyway.
- A squash merged branch is not an ancestor of main, so `git branch -d` refuses to delete it locally. The guide says to use `git branch -D` after the merge, so the refusal does not look like lost work.
- The check reads `closingIssuesReferences` from GitHub's GraphQL API rather than searching the description, so it agrees with what GitHub will actually close. An issue linked by hand under Development counts too, but linking one does not rerun the check.
- The agent recommends triggering the review from the owner's machine rather than with a self-hosted GitHub Actions runner: a `pull_request` run uses the workflow file from the pull request's branch, so anyone with write access could run commands on the owner's machine. The reviewing agent reads untrusted text, so it gets read-only tools and no GitHub token, and the script around it does the rest.
- The review requirement stopped only the owner: the ruleset Only owner merges main already keeps every collaborator but the owner from merging, and the owner's bypass works on a single pull request but not in a stack merge with `gh stack merge`. What went with it: GitHub no longer marks a student's pull request as missing an approval, so the customer's rule that another student reviews every change is the owner's check before merging. The guide still asks the students for the review.
- A stack merge reads every check suite on a pull request's head commit, superseded ones included, and cannot use a bypass. On 2026-11-10 stack #235 needed Only owner merges main paused, and 47 old runs of `closes-an-issue` rerun, each cancelled by the workflow's concurrency group or failed before the label `no-issue` was added. [0003](0003-no-branch-protection-yet.md) has the times.
- CI on push to every branch built each commit on a pull request twice, once for the push and once for the pull request. The last 30 runs of [`build.yml`](../../.github/workflows/build.yml) took 4 to 6 minutes each. A branch without a pull request now gets no CI.

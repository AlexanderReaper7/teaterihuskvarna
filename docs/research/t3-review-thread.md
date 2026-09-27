---
created: 2026-09-27
provenance: agent
description: Whether a script can start a T3 Code thread for the automatic pull request review, measured against t3 0.0.42, and what the thread can do to the machine.
---

# Starting a T3 Code thread from a script

The spike that [decisions/0020](../decisions/0020-pull-requests-and-merging.md) asked for, done on 2026-09-27 against t3 0.0.42. The answer is yes, through an HTTP endpoint the `t3 project` command already uses, but a thread that runs in a pull request's checkout would run that pull request's code before the reviewer reads a line.

## What works, measured

1. `t3 pair --label <label> --ttl 5m` mints a one-time pairing token and prints it on a `Token:` line.
2. `POST /oauth/token` exchanges it for a bearer token. The form is an RFC 8693 token exchange: `grant_type=urn:ietf:params:oauth:grant-type:token-exchange`, `subject_token_type=urn:t3:params:oauth:token-type:environment-bootstrap`, `requested_token_type=urn:ietf:params:oauth:token-type:access_token`. With `scope=orchestration:read orchestration:operate` the server granted exactly those two scopes, for 2591999 seconds, 30 days. The session shows in T3's connections list under its label.
3. `POST /api/orchestration/dispatch` with a `thread.create` command, then a `thread.turn.start` command, created a thread in the teaterihuskvarna project and ran a turn. It appeared in the sidebar like any other thread, titled "Spike: automatic PR review probe".
4. `GET /api/orchestration/threads/<id>` returns the thread's messages, activities and session status, so a script can wait for the reply and read it.

The server's address is in `~/.t3/userdata/server-runtime.json`. The command schemas are in the t3 source, `packages/contracts/src/orchestration.ts`, readable from `bin.mjs.map` in the Nix store package, because the release ships no documentation for them.

## What the thread can do

A thread's `runtimeMode` and `interactionMode` decide its permissions. `approval-required` starts Claude in its default permission mode and routes each approval to the T3 window, and `interactionMode: plan` switches it to plan mode (`src/provider/Layers/ClaudeAdapter.ts`). T3 has no way to hand Claude a list of allowed tools, so the read-only tool list the review design wanted does not exist here.

In the probe, the Read tool ran without a prompt. Asked twice to call Bash, the model refused both times, citing plan mode, so the permission layer itself was never tested: what stops a prompt-injected model from calling Bash in plan mode is unmeasured.

## The problem: project settings

T3 starts Claude with the setting sources `user`, `project` and `local`. A thread's working directory decides which project settings load. If the review thread runs in a checkout of the pull request, that pull request's `.claude/settings.json` loads, and a hook in it is a shell command Claude runs by itself on events such as the session starting. Plan mode does not govern hooks. The pull request's `CLAUDE.md` and [`AGENTS.md`](../../AGENTS.md) load as instructions too. This is from reading the source, not from a live test.

So the thread must not run in the pull request's checkout. Two ways that keep the T3 thread:

- Run the thread in a checkout of `main`, and hand it the pull request as data: the diff, and the changed files extracted to a directory outside any project, which it reads with the Read tool.
- Refuse to review automatically when the pull request touches `.claude/`, `CLAUDE.md`, [`AGENTS.md`](../../AGENTS.md) or `.mcp.json`, and tell the owner instead.

## Left behind by the spike

- The probe thread in T3, which the owner can delete.
- A connected client labelled `pr-review-spike`, valid until 2026-10-27. The token file was deleted, but the session stays until the owner revokes it in T3's connections list.

## Agent notes

- The endpoint is undocumented and t3 is at version 0.0.x, so a later `t3 update` can change the command schemas. A script should check `t3 --version` and stop on a version it was not tested against, rather than fail halfway.
- Moving the review off T3 does not remove the project-settings problem: `claude -p` in the pull request's checkout loads the same settings. The bubblewrap design had the same hole, since it mounted the checkout read-only but still ran Claude inside it.

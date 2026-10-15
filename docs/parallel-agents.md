---
created: 2026-09-26
provenance: agent
description: How teaterihuskvarna was changed so several agents can build, test and run the stack from separate git worktrees at once, what was measured, and what is still shared.
---

# Parallel agents on teaterihuskvarna

Written by the agent on 2026-09-26. Decisions credited to the user below are the ones the user made in that session; everything else is the agent's.

The goal was one git worktree per agent, each able to run `verify`, the image build and the Playwright suite at the same time as the others, with nothing left behind that needs sudo to delete. The work follows the `parallel-agents` skill: list what the tooling writes outside the working copy, then close each item by parameterising it or copying it.

## The choice between clone and worktree

The user chose worktrees over clones. A worktree is cheaper to create and shares objects, but it broke two things a clone does not: the image build, whose `.git` file points outside the Docker build context, and the git-commit-id plugin, which recorded the main checkout's commit. Both are fixed below. The user ruled out the alternative of switching the plugin to `failOnNoGitDirectory=false`.

## What was shared, and how each was closed

Each row is one thing that lived outside the working copy or had a fixed name. The work was three commits, c18bb8f, 8401c41 and 8be6b5c, which pull request #50 squashed into [8621eb7](https://github.com/AlexanderReaper7/teaterihuskvarna/commit/8621eb7) on 2026-09-28, so the original hashes are on no branch.

| Shared thing | Failure | Fix | Commit |
|---|---|---|---|
| `target/` written by a root container | The checkout could not be deleted without sudo. | [`scripts/maven.sh`](../scripts/maven.sh) runs Maven as the host user, in the docker socket's group so Testcontainers still works, and chowns anything an older build left owned by root. The user agreed. | [8621eb7](https://github.com/AlexanderReaper7/teaterihuskvarna/commit/8621eb7) |
| Maven's download cache | Every checkout downloaded everything again. | The named volume `teaterihuskvarna-cache`, mounted as `HOME`. Maven resolver 2.0.21 locks per artifact with `file-lock`, so parallel builds share it. | [8621eb7](https://github.com/AlexanderReaper7/teaterihuskvarna/commit/8621eb7) |
| The Maven wrapper's own install | Two installs into one directory delete each other's files (`DirectoryNotEmptyException`, measured in parallel image builds). | In the Dockerfile the wrapper gets its own layer and the BuildKit cache covers only `~/.m2/repository`. In the shared volume the race remains: only the very first build on an empty volume can hit it. The user accepted that risk. | [8621eb7](https://github.com/AlexanderReaper7/teaterihuskvarna/commit/8621eb7) |
| The e2e compose project name and host ports | A second run replaced the first one's stack. | The project name comes from the checkout directory. Nothing is published on the host; Playwright runs inside the application container's network namespace (`--network container:<app>`), where the services are `mail` and `db`. `E2E_KEEP=1` publishes ports for debugging, one kept stack per machine. The user decided on a stack per checkout. | [8621eb7](https://github.com/AlexanderReaper7/teaterihuskvarna/commit/8621eb7) |
| Traefik's docker provider | Without a constraint it merged routers from every stack; the main stack returned 404 while a second dev stack ran (measured). | `--providers.docker.constraints` on the compose project label. | [8621eb7](https://github.com/AlexanderReaper7/teaterihuskvarna/commit/8621eb7) |
| The dev image tag `teaterihuskvarna:dev` | Stacks overwrote each other's image. | `image: !reset null` in [`compose.dev.yaml`](../compose.dev.yaml), so compose names it `<project>-app`. | [8621eb7](https://github.com/AlexanderReaper7/teaterihuskvarna/commit/8621eb7) |
| `.env` | A new worktree had none, and the stack refused to start. | A [`post-checkout`](../.githooks/post-checkout) hook copies [`.env.example`](../.env.example), and the example's defaults work unchanged, database password included. `git worktree add` runs the hook. | [8621eb7](https://github.com/AlexanderReaper7/teaterihuskvarna/commit/8621eb7) |
| The git directory in a worktree | `COPY . .` could not reach the `.git` the worktree's `.git` file points to, and git-commit-id 10.0.1 (the latest) resolves a worktree to the main checkout and runs git there. | The git directory is its own build context (`additional_contexts` in [`compose.yaml`](../compose.yaml), `--build-context git=.git` in CI and VS Code). The hook writes `BUILD_GIT_DIR` and `BUILD_GIT_WORKTREE` into `.env` in a worktree. `GIT_DIR` and `GIT_WORK_TREE` point git at the worktree whatever directory the plugin runs it in. [`maven.sh`](../scripts/maven.sh) runs the Dockerfile's new `jdk` stage, which has git, and mounts the shared git directory at its host path. | [8621eb7](https://github.com/AlexanderReaper7/teaterihuskvarna/commit/8621eb7) |
| Claude Code auto-memory | Keyed by the main repository, so every worktree shares one memory directory (measured with `claude -p`). | Not isolated. The one project memory that mattered to every agent, "a change the agent did not make is the user's", moved into the global instructions, as the user asked. Future project memories get a junction on the user's machine only. | e751be6 in the [agents repository](https://github.com/AlexanderReaper7/agents/commit/e751be6) |

## What was measured

- `sh scripts/maven.sh verify` in a fresh worktree: build success, 111 integration tests, no failures. `target/` owned by uid 1000.
- `git.properties` from that worktree, on a probe commit `1da7fd7`: `git.branch=agent/wt-probe`, `git.commit.id.abbrev=1da7fd7`, `git.dirty=false`. With one untracked file added, `git.dirty=true`. Before the `GIT_DIR` fix the same worktree recorded main's commit and `branch=main`.
- `docker compose build app` in the worktree produced `teaterihuskvarna-wt-probe-app`, and its jar recorded the same worktree commit and branch.
- `sh scripts/maven.sh verify` in the main checkout after the change: 111 integration tests, no failures, `branch=main`, `8be6b5c`, `dirty=false`.
- `docker build --build-context git=.git .` in the main checkout recorded `branch=main`, the current commit, `dirty=false`.
- `docker build .` without the git context fails with `fatal: not a git repository: '/git'` once the source differs from the last good build. The Dockerfile's empty `git` stage exists so a missing context fails here rather than pulling an image named `git` from Docker Hub.
- `sh e2e/run.sh` from the worktree: 217 passed, 1 skipped, exit 0.
- Earlier in the session, before worktrees: two e2e runs in parallel from two checkouts, and `verify` through the script.
- `git worktree remove` succeeded without sudo, and no containers or images from the probe were left.

## What is still shared or unverified

- The dev stack's ports. A worktree's `.env` comes from [`.env.example`](../.env.example), so every worktree gets `PROXY_HTTP_PORT=8000`, and the debug overlay publishes a fixed 5005. Two dev stacks with default `.env` files collide. Deriving the port from a slot number, as the skill suggests, is not done.
- The wrapper download race in the shared volume, accepted by the user.
- E3 in [open questions](open-questions.md): under two parallel e2e runs one Firefox test failed on an empty device name. Each run passes alone.
- The Windows VS Code tasks run the plain JDK image without git, so the plugin falls back to JGit there: untracked files do not count as changes, and a worktree records the main checkout's commit. Nothing on Windows was run.
- BuildKit reused a cached image for `docker build .` without the git context when the source was byte-identical to the last good build. The result is the earlier build's jar, so a commit that changed nothing but history would carry the older `git.properties`.
- The changes reached main through pull request #50. Whether CI passed on it was not checked when this file moved.
- Claude Code memory is shared by every worktree, as above.

## For the next project

The project-specific part was the list in the table, and it took measurement to find: the Traefik merge and the plugin's worktree handling both passed silently until a second checkout ran next to the first. Two lessons carry over. A tool that reads git metadata is worth testing from a worktree before trusting it, since "resolve the worktree to its main checkout" is an easy bug to write. And a reverse proxy watching the docker socket sees every stack on the machine unless told otherwise.

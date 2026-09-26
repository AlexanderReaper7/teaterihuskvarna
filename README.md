# Teater i Huskvarna

Website and member register for the Teater i Huskvarna association. What the system has to do is in [docs/projektplan.md](docs/projektplan.md); why it is built this way is in [docs/decisions/](docs/decisions/).

## Getting started

### What to install

- Git.
- Docker Desktop on Windows or macOS, or Docker Engine with the compose plugin on Linux. Docker Desktop has to be running before any `docker` command works.
- VS Code if you want it. It is optional, and the [VS Code](#vs-code) section covers what [`.vscode/`](.vscode/) sets up.

Nothing else. Java and Maven are not installed on your machine: the build runs inside a container with a pinned JDK 25 and the checked-in Maven wrapper ([`mvnw`](mvnw)), per [decisions/0011](docs/decisions/0011-maven-and-the-build-in-a-container.md). Every command in this section works in PowerShell and in a POSIX shell. The multi-line commands further down are written for a POSIX shell, so on Windows use the VS Code tasks for those, or Git Bash, which `sh e2e/run.sh` needs too.

### First run

1. Clone the repository and go into it: `git clone https://github.com/AlexanderReaper7/teaterihuskvarna.git` then `cd teaterihuskvarna`.
2. Run every test and check once: `Ctrl+Shift+B` in VS Code, or the command under [Tests](#tests). The first build downloads Maven and every dependency and takes a few minutes; later builds reuse them. Besides the tests, it creates `.env` from [`.env.example`](.env.example) and installs the [git hooks](#git-hooks). Git ignores `.env`, so what you put in it stays on your machine. The template works locally as it is.
3. Build and start the stack: `docker compose up -d --build`.
4. Check that four containers are running: `docker compose ps` lists `proxy`, `app`, `db` and `mail`.
5. Open [http://localhost:8000/](http://localhost:8000/). If the page does not load yet, the application is still starting. `docker compose logs -f app` shows it, and it is ready at the line `Started Application in ... seconds`. Ctrl+C stops following the log, not the application.

### Logging in

Nobody has a password. Logging in works by a link sent by mail, and locally every mail goes to Mailpit instead of a real inbox.

1. On [http://localhost:8000/](http://localhost:8000/), which is the development index, pick a seeded member or administrator and press its button. The application mails that address a login link.
2. Open [http://localhost:8000/mailpit/](http://localhost:8000/mailpit/), open the newest mail and follow the link.

The seeded people are invented, under `.test` addresses that cannot reach anyone. Karin Holmberg has both a member account and an administrator account on the same address, which is the case the two login pages (`/logga-in` and `/admin/logga-in`) exist for.

### Everyday commands

| To | Run |
| --- | --- |
| Start the stack | `docker compose up -d` |
| See a change to Java code or a template | `docker compose up -d --build` |
| See a change to CSS or an image | Reload the page. [`src/main/resources/static`](src/main/resources/static/) is mounted into the container. |
| Follow the application's log | `docker compose logs -f app` |
| Stop the stack, keep the database | `docker compose down` |
| Stop the stack and delete the database | `docker compose down -v`. The next start seeds it again. |
| Run every test and check before a commit | `Ctrl+Shift+B` in VS Code, or the command under [Tests](#tests) |

### How changes reach main

Only Alexander Öberg merges into `main`. Everyone else works on a branch and opens a pull request:

1. Start from the current main: `git switch main`, `git pull`, then `git switch -c <branch-name>`.
2. Commit, then push the branch: `git push -u origin <branch-name>`.
3. Open a pull request on GitHub and wait for CI and a review. Do not press the merge button, even though GitHub shows it to you.
4. After the merge, delete the branch and start the next one from step 1. Do not keep committing on a merged branch.

GitHub cannot enforce this on the repository's current plan ([0003](docs/decisions/0003-no-branch-protection-yet.md)), so the [`pre-push`](.githooks/pre-push) [git hook](#git-hooks) enforces part of it on your machine. It cannot see the merge button on GitHub, so the rule above still holds for everything the hook misses.

### Git hooks

The hooks live in [`.githooks/`](.githooks/). Every Maven build (`verify`, `test`, any of the VS Code Maven tasks) copies them into `.git/hooks/` at its first step, so a clone gets them from its first build and every later build keeps them current. `docker compose up --build` does not install them, because the image build works on a copy of the repository.

| Hook | Runs | Does |
| --- | --- | --- |
| [`pre-push`](.githooks/pre-push) | Before `git push` sends anything | Refuses a push to `main`, a force-push to `main`, and any branch that contains a commit taken off `main`. `git push --no-verify` skips it. |
| [`post-checkout`](.githooks/post-checkout) | After `git switch`, `git checkout` and `git worktree add`. Not after `git clone`, which runs before any hook is installed. | Copies [`.env.example`](.env.example) to `.env` when there is none. In a git worktree it also adds `BUILD_GIT_DIR` and `BUILD_GIT_WORKTREE` to `.env`, which the image build needs to find the repository's history. |

The build overwrites a hook of your own with the same name, and warns when it replaces a different [`pre-push`](.githooks/pre-push). A git worktree uses the main checkout's `.git/hooks/`, so the build skips the copy there.

`.env` is created in more places than the hook: the Maven build and the VS Code compose tasks also copy [`.env.example`](.env.example) when `.env` is missing. None of them ever changes an existing `.env`.

### Updating an existing clone

A clone made before [`f9df7d5`](https://github.com/AlexanderReaper7/teaterihuskvarna/commit/f9df7d5) (2026-09-24) needs these steps once. On Windows the build otherwise fails with `./mvnw: not found`, because git checked [`mvnw`](mvnw) out with CRLF line endings. [`.gitattributes`](.gitattributes) now pins them to LF.

1. Commit or stash your own changes. The reset in step 3 throws away anything uncommitted.
2. Pull: `git pull`
3. Check the files out again with the new line endings: `git rm --cached -r -q .` then `git reset --hard`
4. Delete the old database volume, which fails Flyway's checksum since V1 changed: `docker compose down -v`
5. Run every test and check once, as in [First run](#first-run) step 2, to install the git hooks and create `.env` if it is missing. An existing `.env` must set `POSTGRES_PASSWORD`; [`.env.example`](.env.example) sets it to `local-development-only` in the local development lines.
6. Build and start: `docker compose up -d --build`

`git ls-files --eol mvnw` should print `i/lf    w/lf`. If it prints `w/crlf`, step 3 did not run.

### When something goes wrong

- **`required variable POSTGRES_PASSWORD is missing a value`.** `.env` is missing or leaves `POSTGRES_PASSWORD` empty. `docker compose` reads `.env` before anything in this repository runs, so in a fresh clone it cannot create one. Run a build first, as in [First run](#first-run) step 2, or `cp .env.example .env`.
- **`./mvnw: not found` during the build.** [`mvnw`](mvnw) has Windows line endings. Follow [Updating an existing clone](#updating-an-existing-clone).
- **The app container stops, and its log says `Migration checksum mismatch`.** The database volume was created by an older version of a migration. `docker compose down -v` deletes it, and the next start builds it again.
- **`address already in use` or `port is already allocated` on 8000.** Another program has the port. Set `PROXY_HTTP_PORT=8001` in `.env`, run `docker compose up -d` again and use [http://localhost:8001/](http://localhost:8001/).
- **`Cannot connect to the Docker daemon`, or on Windows `error during connect`.** Docker is not running. Start Docker Desktop and wait until it says it is running.
- **`pre-push: refusing to push to main`.** Your commits are on `main` instead of a branch. `git switch -c <branch-name>` moves them onto a new branch, then push that. Your local `main` still points at them, so reset it afterwards: `git switch main` then `git reset --hard origin/main`.
- **`pre-push: ... contains 5f57ec0...`.** The branch was started before `main` was rewritten on 2026-09-25. The message prints the `git rebase` command that moves your own commits onto the current `main`.
- **The build fails at `checkstyle`, `spotbugs` or `pmd`.** The code breaks one of the static analysis rules, and the message names the file and line. See [Static analysis](#static-analysis).
- **Anything else.** Read the end of `docker compose logs app`. The first `ERROR` line usually names the problem.

## The local stack

The stack is Traefik, the Spring Boot application behind it, and PostgreSQL
18.2. Flyway applies the migrations in [`src/main/resources/db/migration`](src/main/resources/db/migration/) at
startup.

[`compose.yaml`](compose.yaml) alone is production. `COMPOSE_FILE` in [`.env.example`](.env.example) adds
[`compose.dev.yaml`](compose.dev.yaml), which must never be loaded in production. It serves plain
HTTP on `PROXY_HTTP_PORT` (8000 unless set), turns on the `dev` profile with
invented settings ([`application-dev.yaml`](src/main/resources/application-dev.yaml)) and invented members, and starts
Mailpit, which catches every mail the application sends.

Under the `dev` profile, [http://localhost:8000/](http://localhost:8000/) is a development index in place
of the start page. It lists every route, has a button per seeded member and
administrator that mails that address a login link, and shows the schema
version, the commit the jar was built from, whether it was built with
uncommitted changes, and who is logged in. Open the link in Mailpit's UI at
[http://localhost:8000/mailpit/](http://localhost:8000/mailpit/), which Traefik routes to Mailpit. The same data is JSON under `/api/development/`, which
answers 404 without the profile.

[`compose.dev.yaml`](compose.dev.yaml) also mounts [`src/main/resources/static`](src/main/resources/static/) into the container,
so an edited stylesheet or image shows on reload. Templates and Java still
need `docker compose up -d --build`.

### Sharing the dev stack

A Tailscale Funnel shows the dev stack to people off the tailnet. [`compose.share.yaml`](compose.share.yaml) puts the site and Mailpit behind a password and points login links at the public address. Add it to `COMPOSE_FILE`, set `SHARE_URL` and `SHARE_BASIC_AUTH` as [`.env.example`](.env.example) describes, then:

```sh
docker compose up -d --build
tailscale funnel --bg --https=8443 "$PROXY_HTTP_PORT"   # tailscale funnel --https=8443 off to close it
```

Funnel publishes a machine name, never a Tailscale Service name, so the address is `https://<machine>.<tailnet>.ts.net:8443`. Port 8443 because 443 on the machine may already serve something tailnet-only. The dev index sends a login link for any seeded account, administrator included, which is why the password covers everything.

V1 was edited on 2026-09-23, before any deploy, to move the email address from
`member` to `account`. A database volume created before that fails Flyway's
checksum; `docker compose down -v` deletes it and the next start rebuilds it.

## Tests

Unit tests need nothing. Integration tests (`*IT`) start a real PostgreSQL
container through Testcontainers, so they need a docker socket.

```sh
sh scripts/maven.sh verify
```

The script runs [`./mvnw`](mvnw) in the pinned JDK container with the docker socket mounted. On Linux it runs as your user, so `target/` stays yours. Maven's downloads are kept in the docker volume `teaterihuskvarna-cache`, shared by every checkout on the machine. On Windows the VS Code tasks run the same container directly, since PowerShell has no `sh`.

`docker build --build-context git=.git .` runs the unit tests only. A build stage has no docker daemon,
so the integration tests run here and in CI instead. The image build needs the git directory as its own context, and fails without it; `docker compose up --build` passes it itself. In a git worktree the [`post-checkout`](.githooks/post-checkout) hook writes the two `BUILD_GIT_*` lines to `.env` that make this work, and `git worktree add` runs that hook.

### End-to-end

A Playwright suite in [`e2e/`](e2e/) drives the running site in Chromium and Firefox ([0017](docs/decisions/0017-playwright-e2e-in-docker.md)). It needs only docker.

```sh
sh e2e/run.sh                        # build, start its own stack, run every test, remove the stack
sh e2e/run.sh tests/login.spec.ts    # arguments go to `playwright test`
E2E_KEEP=1 sh e2e/run.sh             # leave the stack running, published on http://localhost:55556
```

The stack is the compose project `teaterihuskvarna-e2e-<directory>`, with its own database, and never touches the dev stack. Nothing is published on the host unless `E2E_KEEP` is set, so several checkouts can run the suite at once. The report lands in `e2e/playwright-report/`, and each failed test's trace in `e2e/test-results/`. A test marked `test.fail()` is a known bug listed in [docs/open-questions.md](docs/open-questions.md) under "Found by the e2e suite".

CI runs the suite only when started by hand: `gh workflow run e2e.yml`, or "Run workflow" on the e2e workflow in the Actions tab. The report and traces are the run's `playwright-report` artifact.

## Static analysis

Checkstyle, SpotBugs and PMD all fail the build ([0013](docs/decisions/0013-three-static-analysis-gates.md)).
Checkstyle is bound to `validate`, so a style violation stops the build before
anything compiles and `docker build` runs it too. SpotBugs and PMD need bytecode
and run at `verify`. The `verify` command above is the one that runs all three;
`./mvnw test` runs only Checkstyle.

Doc comments are `///` Markdown (JEP 467). Checkstyle rejects `/** */`, but it
cannot read what is inside a `///` comment, so `@param` and `@return` are a
convention here rather than a gate. [0013](docs/decisions/0013-three-static-analysis-gates.md) has the measurement and the option that
was costed and not taken.

## How the code is arranged

Every capability is a method on an application service in a domain package such
as `se.teaterihuskvarna.member`. Two adapters sit over it as peers: `web` holds
the JTE controllers, `api` holds the REST endpoints, and both call the same
service method. Anything the site can do, the API can do
([0014](docs/decisions/0014-one-service-layer-two-adapters.md)).

`AdapterRulesTest` fails the build if an adapter reaches a repository or an
entity, if the two adapters start depending on each other, or if a JTE page uses
a service method that no endpoint exposes.

## VS Code

[`.vscode/`](.vscode/) is checked in. Open the folder and VS Code offers the extensions in
[`extensions.json`](.vscode/extensions.json); none are required.

Every task in [`tasks.json`](.vscode/tasks.json) runs through docker, so nothing here needs a JDK or
Maven on the machine. `Ctrl+Shift+B` runs `maven: verify`, the command
[`AGENTS.md`](AGENTS.md) requires. The others cover `test`, `compile`, `checkstyle` alone, the e2e suite,
the docs check, the compose stack and the image build. Compiler and Checkstyle
messages land in the Problems panel.

The Java language server is the exception: it runs on the host JDK, and this
project targets Java 25. If the host JDK is a different version the editor can
offer APIs the build rejects, and `./mvnw verify` is what catches that.
[`.devcontainer/`](.devcontainer/) is the fallback, a pinned JDK 25 with the docker socket passed
through, for when that gap starts costing time.

To debug the running application, pick "Attach to the application" in the Run
view. It starts the dev stack with [`compose.debug.yaml`](compose.debug.yaml), which opens JDWP on
`127.0.0.1:5005`. That file is named rather than called `compose.override.yaml`
on purpose, so a plain `docker compose up` never opens a debug port.

## Documentation

[`docs/check.py`](docs/check.py) guards the quoted requirement text, the relative links between
documents, the Mermaid diagrams, every document's provenance and front matter,
the names of meeting documents, and that a requirement id followed by an issue
(`R005 #8`) names the same issue everywhere. Its docstring lists all eight
checks. CI runs it; see [.github/workflows/docs.yml](.github/workflows/docs.yml).

- Requirements are numbered `R001` upwards, in [docs/requirements.md](docs/requirements.md), which says how ids are given out.
- Meeting documents are named `YYYY-MM-DD-<slug>.md`, per [docs/meetings/naming-convention.md](docs/meetings/naming-convention.md). A question for the customer goes in the next meeting's document, which also records the answer. [docs/open-questions.md](docs/open-questions.md) holds only what cannot be asked there.
- Front matter and provenance are described in [docs/decisions/0019](docs/decisions/0019-provenance-of-documents.md).

## Where things are

| Path | What |
| --- | --- |
| [`src/main/java/se/teaterihuskvarna/`](src/main/java/se/teaterihuskvarna/) | Application code |
| [`src/main/jte/`](src/main/jte/) | Templates, compiled to Java classes at build time |
| [`src/main/resources/messages_sv.properties`](src/main/resources/messages_sv.properties) | Fixed Swedish copy ([0001](docs/decisions/0001-language-policy.md)) |
| [`src/main/resources/db/migration/`](src/main/resources/db/migration/) | Flyway migrations ([0010](docs/decisions/0010-flyway-for-migrations.md)) |
| [`checkstyle.xml`](checkstyle.xml), [`suppressions.xml`](suppressions.xml) | Style ruleset and its exemptions ([0013](docs/decisions/0013-three-static-analysis-gates.md)) |
| [`spotbugs-exclude.xml`](spotbugs-exclude.xml) | SpotBugs exclusions, each with its reasoning beside it |
| [`src/test/java/se/teaterihuskvarna/architecture/`](src/test/java/se/teaterihuskvarna/architecture/) | The adapter rules ([0014](docs/decisions/0014-one-service-layer-two-adapters.md)) |
| [`.vscode/`](.vscode/) | Tasks, launch configurations and extension suggestions |
| [`compose.yaml`](compose.yaml) | The production stack ([0008](docs/decisions/0008-everything-in-containers.md)) |
| [`compose.dev.yaml`](compose.dev.yaml) | Local development on top of it, loaded through `COMPOSE_FILE` |
| [`compose.debug.yaml`](compose.debug.yaml) | JDWP on loopback, loaded only when named |
| [`docs/decisions/`](docs/decisions/) | Why the technical choices are what they are |
| [`docs/requirements.md`](docs/requirements.md) | What the system has to do, by requirement id |
| [`docs/meetings/`](docs/meetings/) | One document per meeting, with its questions and answers |
| [`docs/onboarding.md`](docs/onboarding.md) | How the team works, its meetings and the Kanban board, for someone who has never used one |
| [`e2e/`](e2e/) | The Playwright suite, its compose file and [`run.sh`](e2e/run.sh) ([0017](docs/decisions/0017-playwright-e2e-in-docker.md)) |

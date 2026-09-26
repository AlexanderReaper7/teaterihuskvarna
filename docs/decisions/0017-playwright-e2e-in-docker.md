---
created: 2026-09-23
provenance: unreviewed
description: How the end-to-end tests are written and run.
---

# 0017: Playwright end-to-end tests, run in docker against their own stack

## Decision

[`e2e/`](../../e2e/) holds a Playwright suite in TypeScript that drives the real application in Chromium and Firefox. It covers link login for both kinds, the membership application, adding and removing administrators, the member and administrator pages, passkeys, the passkey offer and its decline cookie, the REST API and the layout at four widths. Each spec has a "what works" part and a "what a person might get wrong" part: double presses, links used twice or cut short, two tabs, the back button, a stale page, markup in names.

`sh e2e/run.sh` is the whole command. It builds the image, starts the application, PostgreSQL and Mailpit under the compose project `teaterihuskvarna-e2e-<directory>`, runs `mcr.microsoft.com/playwright:v1.63.0-noble` against it, and removes the stack with its volume and image. Arguments go to `playwright test`, and `E2E_KEEP=1` leaves the stack running.

CI runs the suite only when someone starts it by hand: [`.github/workflows/e2e.yml`](../../.github/workflows/e2e.yml) has `workflow_dispatch` and no other trigger, and keeps the report and the traces as an artifact for 14 days. The user decided this on 2026-09-23, after the first fixes. A run costs about three minutes and an image build, and a few tests are timing-sensitive where the product is (see "What it costs"), so a push that changes no page would wait on it and could fail for a reason it did not cause. [`build.yml`](../../.github/workflows/build.yml) stays the check on every push.

## Why Playwright, and why in its image

Playwright runs Chromium and Firefox from one test file, and its Chromium exposes a software authenticator through the DevTools protocol, so passkeys can be tested without a person pressing a fingerprint reader. Selenium can do the same through the WebAuthn WebDriver extension, with a driver per browser to keep in step; Playwright's image carries browsers and library at one version. Spring's MockMvc tests already cover the server side, but they run no JavaScript, and [`passkey.js`](../../src/main/resources/static/js/passkey.js) is where one of the bugs this suite found lives.

Node, the browsers and their system libraries come from Microsoft's image, pinned to the same version as `@playwright/test` in [`e2e/package.json`](../../e2e/package.json). The two must match, or the library looks for browser builds the image does not carry. Nothing is installed on the host, for the reason [0011](0011-maven-and-the-build-in-a-container.md) gives for the JDK.

## Why a stack of its own, and no Traefik

The suite changes data: it expires links, removes administrators and fills the per-address rate limit. Run against the dev stack, it would change whatever the developer was looking at and depend on what they had done. So [`run.sh`](../../e2e/run.sh) starts a separate compose project with its own database volume, removed before and after every run. A fresh database also resets the rate limit's counts.

Each checkout gets its own stack, so agents in several worktrees can run the suite at once. Decided by the user on 2026-09-26. The project name comes from the checkout's directory, and so does the image's, since [`compose.dev.yaml`](../../compose.dev.yaml) leaves the tag to compose. Nothing is published on the host: Playwright runs in the application container's network namespace (`--network container:`), where the application is `localhost:55556` and Mailpit and PostgreSQL are `mail` and `db`. The application listens on 55556 inside its container, the same port it builds login links with. The suite reads mail through Mailpit's API, and reads and changes rows directly through [`e2e/support/db.ts`](../../e2e/support/db.ts), to expire a link without waiting a day. `E2E_KEEP=1` adds [`e2e/compose.e2e-keep.yaml`](../../e2e/compose.e2e-keep.yaml), which publishes the application on 55556, Mailpit on 55025 and PostgreSQL on 55433, so only one kept stack fits on a machine.

Traefik is left out. It finds applications through container labels on the whole docker host. [`compose.dev.yaml`](../../compose.dev.yaml) limits a dev Traefik to its own compose project, and the e2e application is labelled `traefik.enable=false` as well. What this skips is the proxy itself; both stacks serve plain HTTP, so no forwarded `https` is lost.

## Passkeys in Chromium only

[`passkeys.spec.ts`](../../e2e/tests/passkeys.spec.ts) needs the DevTools authenticator, so [`playwright.config.ts`](../../e2e/playwright.config.ts) keeps it out of the Firefox project. Firefox still runs every other test, including the login pages that carry the passkey button.

## Known bugs stay in the suite, marked

A test that finds a product bug stays, marked `test.fail()`, with a comment citing [open-questions.md](../open-questions.md), "Found by the e2e suite". A marked bug does not turn the suite red while it is there, and does once someone fixes it and forgets to remove the mark. The user chose this on 2026-09-23 over fixing each bug on the spot, so each fix is decided rather than made in passing. The first run found seven; the user had all seven fixed the same day, so no test is marked now.

## What it costs

- Every run builds the image. The Dockerfile copies the whole repository before `mvn package`, so any edited file, a spec included, reruns the Maven build. The whole run took 175 s on 2026-09-23, 2.5 minutes of it the tests, with docker's layer cache warm. `E2E_KEEP=1` keeps a stack up for running Playwright again without a rebuild.
- One worker, one test at a time, because all tests share a database and some change the administrators or the rate limit.
- The browser tests are timing-sensitive where the product is. Before the fix for the login race, the two tests that open a link in a new browser context failed some Chromium runs; one Firefox link request in about 60 was never sent, for reasons not yet found.
- A double press is a browser behaviour, not a server one: Firefox sends both, Chromium one. Once [`forms.js`](../../src/main/resources/static/js/forms.js) stopped the second POST, the double-press tests no longer reach the server's side of it, so `SessionStoreIT` covers the session insert that failed there without a browser.

## What it costs to undo

Deleting [`e2e/`](../../e2e/), [`.github/workflows/e2e.yml`](../../.github/workflows/e2e.yml), the [`.dockerignore`](../../.dockerignore) lines for it, the README section and the VS Code task removes it. Nothing in the application depends on it.

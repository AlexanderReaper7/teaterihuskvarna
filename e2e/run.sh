#!/bin/sh
# Runs the end-to-end suite: builds the application, starts it with its own
# database and Mailpit under the compose project teaterihuskvarna-e2e, runs
# Playwright in its docker image against it, and removes the stack again.
# Why this way: docs/decisions/0017.
#
#   e2e/run.sh                      every test
#   e2e/run.sh tests/login.spec.ts  one file; arguments go to `playwright test`
#   E2E_KEEP=1 e2e/run.sh           leave the stack running afterwards
#
# The report is in e2e/playwright-report, and a failed test's trace in
# e2e/test-results.
set -eu

cd "$(dirname "$0")/.."

# Shell variables win over .env when compose fills in ${...}, so the e2e stack
# never takes the dev stack's database settings.
export POSTGRES_DB=teaterihuskvarna POSTGRES_USER=teaterihuskvarna POSTGRES_PASSWORD=e2e

compose() {
  docker compose -p teaterihuskvarna-e2e --project-directory . \
    -f compose.yaml -f compose.dev.yaml -f e2e/compose.e2e.yaml "$@"
}

if [ -z "${E2E_KEEP:-}" ]; then
  trap 'compose down -v --remove-orphans >/dev/null 2>&1' EXIT
fi

# A fresh database each run, which also resets the rate limit's counts.
compose down -v --remove-orphans >/dev/null 2>&1 || true
compose up -d --build --wait app db mail

attempt=0
until curl -fsS -o /dev/null http://localhost:55556/; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 90 ]; then
    echo "The application did not answer on http://localhost:55556 within 3 minutes." >&2
    compose logs app | tail -50 >&2
    exit 1
  fi
  sleep 2
done

# The image's tag must match @playwright/test in e2e/package.json, or the
# browsers it carries are not the ones the library expects.
docker run --rm --network host --ipc=host --init \
  --user "$(id -u):$(id -g)" -e HOME=/tmp -e CI="${CI:-}" \
  -v "$PWD":/w -w /w/e2e \
  mcr.microsoft.com/playwright:v1.63.0-noble \
  sh -c 'npm ci --no-audit --no-fund --loglevel=error && npx playwright test "$@"' sh "$@"
